package com.troks.tomatorouters.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.troks.tomatorouters.MainActivity
import com.troks.tomatorouters.R
import com.troks.tomatorouters.ui.AppLanguage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Notifies about new router log lines of the chosen levels. Two separate modes:
 * while the app is on screen (every 1 / 3 / 5 min), and while it's closed, through
 * WorkManager (15 min at the shortest, as Android allows) with the saved login.
 */
object LogAlerts {
    private const val PREFS = "log_alerts"
    private const val CHANNEL = "log_alerts"
    private const val NOTIFICATION_ID = 4101
    private const val WORK = "log_alerts"
    /** Set on the notification's tap: open the system log. */
    const val EXTRA_OPEN_LOGS = "open_logs"
    private val mutex = Mutex()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Checking while the app is on screen (older versions had one switch: "enabled"). */
    fun openOn(ctx: Context) = prefs(ctx).let { it.getBoolean("open_on", it.getBoolean("enabled", false)) }

    /** Checking in the background, while the app is closed. */
    fun closedOn(ctx: Context) = prefs(ctx).let { it.getBoolean("closed_on", it.getBoolean("enabled", false)) }

    /** Alerts are on in at least one of the two modes. */
    fun enabled(ctx: Context) = openOn(ctx) || closedOn(ctx)

    /** Check period choices (minutes) while the app is open. */
    val OPEN_MINUTES = listOf(1, 3, 5)

    fun openMinutes(ctx: Context): Int =
        prefs(ctx).getInt("open_minutes", OPEN_MINUTES.first()).takeIf { it in OPEN_MINUTES } ?: OPEN_MINUTES.first()

    fun setOpenMinutes(ctx: Context, minutes: Int) {
        if (minutes in OPEN_MINUTES) prefs(ctx).edit().putInt("open_minutes", minutes).apply()
    }

    /** At most this many levels can be chosen. */
    const val MAX_LEVELS = 3
    val DEFAULT_LEVELS = setOf(LogLevel.EMERG, LogLevel.CRIT, LogLevel.ERR)

    /** Background check period choices, in minutes; anything else is a custom value. */
    val PRESET_MINUTES = listOf(15, 30, 60)
    const val MIN_CUSTOM_MINUTES = 60
    const val MAX_CUSTOM_MINUTES = 24 * 60
    private const val DEFAULT_MINUTES = 60

    /** The log levels the user wants to be told about (1 to [MAX_LEVELS]). */
    fun levels(ctx: Context): Set<LogLevel> =
        prefs(ctx).getStringSet("levels", null)
            ?.mapNotNull { n -> LogLevel.entries.firstOrNull { it.name == n } }?.toSet()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_LEVELS

    fun setLevels(ctx: Context, levels: Set<LogLevel>) {
        if (levels.isEmpty() || levels.size > MAX_LEVELS) return
        prefs(ctx).edit().putStringSet("levels", levels.map { it.name }.toSet()).apply()
    }

    /** How often (minutes) to check in the background, when the app is closed. */
    fun intervalMinutes(ctx: Context): Int {
        val m = prefs(ctx).getInt("interval", DEFAULT_MINUTES)
        // a value saved under older limits falls back to the default
        val ok = if (intervalIsCustom(ctx)) m in MIN_CUSTOM_MINUTES..MAX_CUSTOM_MINUTES else m in PRESET_MINUTES
        return if (ok) m else DEFAULT_MINUTES
    }

    /** True when the user typed their own interval (kept so the screen shows "Custom" again). */
    fun intervalIsCustom(ctx: Context): Boolean = prefs(ctx).getBoolean("interval_custom", false)

    /** Saves the interval; returns false if the value isn't allowed. */
    fun setIntervalMinutes(ctx: Context, minutes: Int, custom: Boolean): Boolean {
        val ok = if (custom) minutes in MIN_CUSTOM_MINUTES..MAX_CUSTOM_MINUTES else minutes in PRESET_MINUTES
        if (!ok) return false
        val changed = minutes != intervalMinutes(ctx)
        prefs(ctx).edit().putInt("interval", minutes).putBoolean("interval_custom", custom).apply()
        if (changed) schedule(ctx, replace = true)
        return true
    }

    fun setOpenOn(ctx: Context, on: Boolean) = setModes(ctx, open = on, closed = closedOn(ctx))

    fun setClosedOn(ctx: Context, on: Boolean) = setModes(ctx, open = openOn(ctx), closed = on)

    private fun setModes(ctx: Context, open: Boolean, closed: Boolean) {
        val wasOn = enabled(ctx)
        val e = prefs(ctx).edit().putBoolean("open_on", open).putBoolean("closed_on", closed).remove("enabled")
        // starting fresh: lines already in the log are not "new"
        if (!wasOn) e.remove("last").remove("lastKey")
        e.apply()
        schedule(ctx)
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Keeps the background check scheduled while alerts are on (safe to call on every start). */
    fun schedule(ctx: Context, replace: Boolean = false) {
        val wm = WorkManager.getInstance(ctx)
        if (!closedOn(ctx)) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<LogAlertWorker>(intervalMinutes(ctx).toLong(), TimeUnit.MINUTES)
            .setConstraints(NETWORK)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    /** The background check's router session cookies, kept between runs. */
    internal fun sessionCookies(ctx: Context, key: String): List<String> =
        prefs(ctx).getStringSet("cookies_$key", null)?.toList().orEmpty()

    internal fun saveSessionCookies(ctx: Context, key: String, cookies: List<String>) {
        prefs(ctx).edit().putStringSet("cookies_$key", cookies.toSet()).apply()
    }

    private val NETWORK = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Battery state for background checks, as Android sees this app. */
    enum class Battery { UNRESTRICTED, OPTIMIZED, RESTRICTED }

    fun battery(ctx: Context): Battery {
        val am = ctx.getSystemService(android.app.ActivityManager::class.java)
        if (Build.VERSION.SDK_INT >= 28 && am.isBackgroundRestricted) return Battery.RESTRICTED
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        return if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) Battery.UNRESTRICTED else Battery.OPTIMIZED
    }

    /**
     * Opens this app's settings page, where "App battery usage" > "Unrestricted" is.
     * (Asking directly needs a permission Google Play allows only for a few kinds of apps.)
     */
    fun openBatterySettings(ctx: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", ctx.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    /**
     * Reads the end of the log, notifies about matching lines added since the last check,
     * and returns how many there were. The first check after turning alerts on only sets
     * the starting point.
     */
    suspend fun check(ctx: Context, api: RouterApi): Int = mutex.withLock {
        if (!enabled(ctx)) return 0
        val lines = api.syslog(400) ?: return 0
        if (lines.isEmpty()) return 0
        val p = prefs(ctx)
        val sig = lines.map { signature(it) }
        val last = p.getString("last", null)
        val fresh: List<LogLine> = when {
            last == null -> emptyList()
            else -> {
                val i = sig.lastIndexOf(last)
                if (i >= 0) lines.drop(i + 1)
                else {
                    // the last line seen is gone (log rotated, router restarted): go by time
                    val lastKey = p.getLong("lastKey", 0L)
                    lines.filter { timeKey(it.date) > lastKey }
                }
            }
        }
        val newest = lines.last()
        p.edit().putString("last", sig.last())
            .apply { timeKey(newest.date).takeIf { it > 0 }?.let { putLong("lastKey", it) } }
            .apply()

        val wanted = levels(ctx)
        val hits = fresh.filter { it.level in wanted }
        if (hits.isNotEmpty()) notify(ctx, hits)
        hits.size
    }

    private fun signature(l: LogLine) = "${l.date}|${l.process}|${l.message}"

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** "Nov 17 21:18:26" -> 1117211826 (no year in syslog; good enough to order recent lines). */
    private fun timeKey(date: String): Long {
        val parts = date.trim().split(Regex("\\s+"))
        if (parts.size < 3) return 0
        val month = MONTHS.indexOf(parts[0]) + 1
        val day = parts[1].toIntOrNull() ?: return 0
        val t = parts[2].split(':').mapNotNull { it.toIntOrNull() }
        if (month == 0 || t.size != 3) return 0
        return ((((month * 100L + day) * 100 + t[0]) * 100 + t[1]) * 100) + t[2]
    }


    private fun notify(ctx: Context, hits: List<LogLine>) {
        if (!canNotify(ctx)) return
        val lang = AppLanguage.fromCode(SecureStore(ctx).languageCode)
        val s = alertStrings(lang)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, s.channel, NotificationManager.IMPORTANCE_HIGH).apply { description = s.channelDesc }
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_LOGS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun text(l: LogLine) = "[${l.level.name.lowercase()}] " + (if (l.process.isNotBlank()) "${l.process}: " else "") + l.message
        val newestFirst = hits.asReversed()
        val inbox = NotificationCompat.InboxStyle()
        newestFirst.take(6).forEach { inbox.addLine(text(it)) }
        if (hits.size > 6) inbox.setSummaryText("+${hits.size - 6}")
        // e.g. "Error, Warning · Router log (3)"
        val names = hits.map { it.level }.distinct().sorted().joinToString(", ") { lang.strings.mon.levelNames[it.ordinal] }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_log_alert)
            .setContentTitle("$names · ${s.routerLog} (${hits.size})")
            .setContentText(text(newestFirst.first()))
            .setStyle(inbox)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked in canNotify
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
    }
}

/** Background check: signs in with the saved login (only if the password is remembered). */
class LogAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        checkOnce(applicationContext)
        return Result.success()
    }

    private suspend fun checkOnce(ctx: Context) {
        if (!LogAlerts.closedOn(ctx)) return
        val store = SecureStore(ctx)
        val l = store.load()
        if (!l.remember || l.password.isEmpty() || l.username.isBlank()) return
        val pin = store.certPin(l.host, l.port)
        // never trust an HTTPS certificate that wasn't pinned in the app first
        if (l.https && pin == null) return
        val cfg = RouterConfig(l.host, l.port, l.https, l.username.trim(), l.password, pin)
        // not at home / router off: just try again next time
        // reuse the last router session, so each check doesn't add a "login successful" line to the log
        val key = "${l.host}:${l.port}"
        // router unreachable or not answering: each request gives up after 30 s, the error is
        // swallowed, and the next check simply happens at the next scheduled time (no retries)
        val client = RouterClient(cfg, LogAlerts.sessionCookies(ctx, key), { LogAlerts.saveSessionCookies(ctx, key, it) }, callTimeoutSeconds = 30)
        runCatching { LogAlerts.check(ctx, RouterApi(client)) }
    }
}

class LogAlertStrings(
    val channel: String,
    val channelDesc: String,
    val routerLog: String,
    val setting: String,
    val settingDesc: String,
    val beta: String,
    val levelsLabel: String,
    /** "While the app is open" switch */
    val openTitle: String,
    val openDesc: String,
    /** labels for [LogAlerts.OPEN_MINUTES] */
    val openIntervals: List<String>,
    /** "While the app is closed" switch */
    val closedTitle: String,
    val closedDesc: String,
    val intervalLabel: String,
    /** labels for [LogAlerts.PRESET_MINUTES] */
    val intervals: List<String>,
    val custom: String,
    val minutes: (min: Int) -> String,
    val minutesError: (min: Int) -> String,
    val save: String,
    val saved: (Int) -> String,
    val batteryOk: String,
    val batteryOptimized: String,
    val batteryRestricted: String,
    val batteryHow: String,
    val batteryButton: String,
    val needRemember: String,
    val blocked: String,
)

fun alertStrings(lang: AppLanguage): LogAlertStrings = when (lang) {
    AppLanguage.HEBREW -> LogAlertStrings(
        channel = "התראות לוג",
        channelDesc = "אירועים חדשים בלוג המערכת של הראוטר",
        routerLog = "לוג הראוטר",
        setting = "התראות מהלוג",
        settingDesc = "התראה על אירועים חדשים בלוג הראוטר",
        beta = "Beta",
        levelsLabel = "התראה על (עד 3)",
        openTitle = "כשהאפליקציה פתוחה",
        openDesc = "בדיקה בזמן שהאפליקציה על המסך",
        openIntervals = listOf("כל דקה", "כל 3 דקות", "כל 5 דקות"),
        closedTitle = "כשהאפליקציה סגורה",
        closedDesc = "בדיקה ברקע עם פרטי ההתחברות השמורים",
        intervalLabel = "תדירות הבדיקה",
        intervals = listOf("כל 15 דקות (פחות מומלץ)", "כל חצי שעה (מומלץ)", "כל שעה (מומלץ מאוד)"),
        custom = "מותאם אישית",
        minutes = { "דקות ($it ומעלה)" },
        minutesError = { "אפשר $it עד 1440 דקות" },
        save = "שמור",
        saved = { "נשמר: בדיקה כל $it דקות" },
        batteryOk = "שימוש ברקע: ללא הגבלה ✓",
        batteryOptimized = "חיסכון בסוללה פעיל: Android עלול לדחות את הבדיקות ברקע",
        batteryRestricted = "השימוש ברקע מוגבל: לא יגיעו התראות כשהאפליקציה סגורה",
        batteryHow = "בהגדרות: שימוש בסוללה של האפליקציה ← ללא הגבלה",
        batteryButton = "הגדרות סוללה",
        needRemember = "בדיקה ברקע צריכה סיסמה שמורה (\"זכור סיסמה\")",
        blocked = "ההתראות חסומות בהגדרות הטלפון",
    )
    AppLanguage.RUSSIAN -> LogAlertStrings(
        channel = "Оповещения журнала",
        channelDesc = "Новые события в системном журнале роутера",
        routerLog = "Журнал роутера",
        setting = "Оповещения из журнала",
        settingDesc = "Уведомления о новых событиях в журнале роутера",
        beta = "Beta",
        levelsLabel = "Оповещать о (до 3)",
        openTitle = "Когда приложение открыто",
        openDesc = "Проверка, пока приложение на экране",
        openIntervals = listOf("Каждую минуту", "Каждые 3 мин", "Каждые 5 мин"),
        closedTitle = "Когда приложение закрыто",
        closedDesc = "Фоновая проверка с сохранённым входом",
        intervalLabel = "Как часто проверять",
        intervals = listOf("Каждые 15 мин (менее рекомендуется)", "Каждые 30 мин (рекомендуется)", "Каждый час (очень рекомендуется)"),
        custom = "Свой интервал",
        minutes = { "Минуты (от $it)" },
        minutesError = { "От $it до 1440 минут" },
        save = "Сохранить",
        saved = { "Сохранено: каждые $it мин" },
        batteryOk = "Работа в фоне: без ограничений ✓",
        batteryOptimized = "Включена экономия батареи: Android может откладывать фоновые проверки",
        batteryRestricted = "Работа в фоне ограничена: оповещений при закрытом приложении не будет",
        batteryHow = "В настройках: расход батареи приложением → Без ограничений",
        batteryButton = "Настройки батареи",
        needRemember = "Для фоновой проверки нужен сохранённый пароль",
        blocked = "Уведомления запрещены в настройках телефона",
    )
    AppLanguage.ARABIC -> LogAlertStrings(
        channel = "تنبيهات السجل",
        channelDesc = "أحداث جديدة في سجل نظام الراوتر",
        routerLog = "سجل الراوتر",
        setting = "تنبيهات من السجل",
        settingDesc = "إشعار بالأحداث الجديدة في سجل الراوتر",
        beta = "Beta",
        levelsLabel = "التنبيه عند (حتى 3)",
        openTitle = "عندما يكون التطبيق مفتوحًا",
        openDesc = "فحص أثناء ظهور التطبيق على الشاشة",
        openIntervals = listOf("كل دقيقة", "كل 3 دقائق", "كل 5 دقائق"),
        closedTitle = "عندما يكون التطبيق مغلقًا",
        closedDesc = "فحص في الخلفية بتسجيل الدخول المحفوظ",
        intervalLabel = "عدد مرات الفحص",
        intervals = listOf("كل 15 دقيقة (أقل توصية)", "كل نصف ساعة (موصى به)", "كل ساعة (موصى به جدًا)"),
        custom = "مخصص",
        minutes = { "دقائق ($it أو أكثر)" },
        minutesError = { "من $it إلى 1440 دقيقة" },
        save = "حفظ",
        saved = { "تم الحفظ: كل $it دقيقة" },
        batteryOk = "الاستخدام في الخلفية: غير مقيد ✓",
        batteryOptimized = "توفير البطارية مفعّل: قد يؤخر Android الفحص في الخلفية",
        batteryRestricted = "الاستخدام في الخلفية مقيد: لن تصل تنبيهات والتطبيق مغلق",
        batteryHow = "في الإعدادات: استخدام البطارية للتطبيق ← غير مقيد",
        batteryButton = "إعدادات البطارية",
        needRemember = "الفحص في الخلفية يحتاج إلى كلمة مرور محفوظة",
        blocked = "الإشعارات محظورة في إعدادات الهاتف",
    )
    AppLanguage.ENGLISH -> LogAlertStrings(
        channel = "Log alerts",
        channelDesc = "New events in the router's system log",
        routerLog = "Router log",
        setting = "Log alerts",
        settingDesc = "Get notified about new events in the router's log",
        beta = "Beta",
        levelsLabel = "Alert me about (up to 3)",
        openTitle = "While the app is open",
        openDesc = "Checks while the app is on screen",
        openIntervals = listOf("Every minute", "Every 3 min", "Every 5 min"),
        closedTitle = "While the app is closed",
        closedDesc = "Checks in the background with the saved login",
        intervalLabel = "How often to check",
        intervals = listOf("Every 15 min (less recommended)", "Every 30 min (recommended)", "Every hour (highly recommended)"),
        custom = "Custom",
        minutes = { "Minutes ($it or more)" },
        minutesError = { "$it to 1440 minutes" },
        save = "Save",
        saved = { "Saved: every $it min" },
        batteryOk = "Background usage: unrestricted ✓",
        batteryOptimized = "Battery optimization is on: Android may delay background checks",
        batteryRestricted = "Background usage is restricted: no alerts while the app is closed",
        batteryHow = "In settings: App battery usage → Unrestricted",
        batteryButton = "Battery settings",
        needRemember = "Background checks need a saved password (\"Remember password\")",
        blocked = "Notifications are blocked in the phone's settings",
    )
}
