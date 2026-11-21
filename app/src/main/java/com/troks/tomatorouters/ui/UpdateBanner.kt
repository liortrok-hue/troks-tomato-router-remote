package com.troks.tomatorouters.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private enum class UpdateStage { NONE, AVAILABLE, DOWNLOADING, DOWNLOADED }

/**
 * In-app "new version on Google Play" bar (not a system notification).
 *
 * Every time the app comes to the screen it asks Google Play (through the Play In-App Updates
 * API, on the device, no network call of our own) whether a newer version is published.
 * If so, a bar slides in at the top: "Update" downloads it in the background while the app
 * keeps working (a flexible update), then the bar offers "Restart" to install it.
 * Closing the bar hides it until the app is opened again.
 *
 * Works only for the copy installed from Google Play; debug / side-loaded builds simply never
 * see an update, and any Play error just means no bar.
 */
@Composable
fun UpdateBanner(lang: AppLanguage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { AppUpdateManagerFactory.create(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val t = updateStrings(lang)

    var stage by remember { mutableStateOf(UpdateStage.NONE) }
    var info by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    // closed by the user: stays hidden until the app is opened again (survives rotation)
    var dismissed by rememberSaveable { mutableStateOf(false) }

    // download progress / finished, reported by Play while a flexible update runs
    DisposableEffect(manager) {
        val listener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.PENDING, InstallStatus.DOWNLOADING -> {
                    stage = UpdateStage.DOWNLOADING
                    val total = state.totalBytesToDownload()
                    progress = if (total > 0) state.bytesDownloaded().toFloat() / total else 0f
                }
                InstallStatus.DOWNLOADED -> { stage = UpdateStage.DOWNLOADED; dismissed = false }
                InstallStatus.CANCELED, InstallStatus.FAILED -> stage = UpdateStage.AVAILABLE
                else -> Unit
            }
        }
        manager.registerListener(listener)
        onDispose { manager.unregisterListener(listener) }
    }

    // check each time the app comes to the screen (a cheap, local call to the Play Store app)
    LaunchedEffect(manager, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val i = suspendCoroutine<AppUpdateInfo?> { cont ->
                manager.appUpdateInfo
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) } // not from Play, no Play Store, offline...
            } ?: return@repeatOnLifecycle
            info = i
            stage = when {
                // downloaded earlier but never installed: offer the restart right away
                i.installStatus() == InstallStatus.DOWNLOADED -> UpdateStage.DOWNLOADED
                i.installStatus() == InstallStatus.DOWNLOADING || i.installStatus() == InstallStatus.PENDING -> UpdateStage.DOWNLOADING
                i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> UpdateStage.AVAILABLE
                else -> UpdateStage.NONE
            }
        }
    }

    // Play's own confirmation sheet for the flexible update; declining just keeps the bar
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    fun openPlayStore() {
        val pkg = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(market) } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun startUpdate() {
        val i = info
        val flexible = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        val started = i != null && i.isUpdateTypeAllowed(flexible) &&
            runCatching { manager.startUpdateFlowForResult(i, launcher, flexible) }.getOrDefault(false)
        // Play didn't offer an in-app download for this update: go to the store page instead
        if (!started) openPlayStore()
    }

    AnimatedVisibility(
        visible = stage != UpdateStage.NONE && !dismissed,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val cs = MaterialTheme.colorScheme
        Surface(
            color = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (stage == UpdateStage.DOWNLOADED) Icons.Outlined.RestartAlt else Icons.Outlined.SystemUpdate,
                        null,
                        Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (stage) {
                                UpdateStage.DOWNLOADING -> t.downloading
                                UpdateStage.DOWNLOADED -> t.ready
                                else -> t.available
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when (stage) {
                                UpdateStage.DOWNLOADING -> t.downloadingDesc
                                UpdateStage.DOWNLOADED -> t.readyDesc
                                else -> t.availableDesc
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    when (stage) {
                        UpdateStage.AVAILABLE -> FilledTonalButton(onClick = ::startUpdate) { Text(t.update, maxLines = 1) }
                        UpdateStage.DOWNLOADED -> FilledTonalButton(onClick = { manager.completeUpdate() }) { Text(t.restart, maxLines = 1) }
                        else -> Unit
                    }
                    IconButton(onClick = { dismissed = true }) { Icon(Icons.Outlined.Close, t.close) }
                }
                if (stage == UpdateStage.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

private class UpdateStrings(
    val available: String,
    val availableDesc: String,
    val update: String,
    val downloading: String,
    val downloadingDesc: String,
    val ready: String,
    val readyDesc: String,
    val restart: String,
    val close: String,
)

private fun updateStrings(lang: AppLanguage) = when (lang) {
    AppLanguage.HEBREW -> UpdateStrings(
        available = "גרסה חדשה זמינה",
        availableDesc = "עדכון חדש מחכה ב-Google Play",
        update = "עדכן",
        downloading = "מוריד את העדכון…",
        downloadingDesc = "אפשר להמשיך לעבוד בינתיים",
        ready = "העדכון מוכן",
        readyDesc = "הפעלה מחדש תתקין אותו",
        restart = "הפעל מחדש",
        close = "סגור",
    )
    AppLanguage.RUSSIAN -> UpdateStrings(
        available = "Доступна новая версия",
        availableDesc = "Обновление ждёт в Google Play",
        update = "Обновить",
        downloading = "Загрузка обновления…",
        downloadingDesc = "Можно продолжать работу",
        ready = "Обновление готово",
        readyDesc = "Перезапуск установит его",
        restart = "Перезапустить",
        close = "Закрыть",
    )
    AppLanguage.ARABIC -> UpdateStrings(
        available = "يتوفر إصدار جديد",
        availableDesc = "تحديث جديد بانتظارك في Google Play",
        update = "تحديث",
        downloading = "جارٍ تنزيل التحديث…",
        downloadingDesc = "يمكنك متابعة العمل في الأثناء",
        ready = "التحديث جاهز",
        readyDesc = "إعادة التشغيل ستثبّته",
        restart = "إعادة التشغيل",
        close = "إغلاق",
    )
    AppLanguage.ENGLISH -> UpdateStrings(
        available = "New version available",
        availableDesc = "An update is waiting on Google Play",
        update = "Update",
        downloading = "Downloading update…",
        downloadingDesc = "You can keep working meanwhile",
        ready = "Update ready",
        readyDesc = "Restart the app to install it",
        restart = "Restart",
        close = "Close",
    )
}
