package com.troks.tomatorouters.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.troks.tomatorouters.data.CertChangedException
import com.troks.tomatorouters.data.DemoShell
import com.troks.tomatorouters.data.Err
import com.troks.tomatorouters.data.LogAlerts
import com.troks.tomatorouters.data.LogLevel
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.RouterClient
import com.troks.tomatorouters.data.RouterConfig
import com.troks.tomatorouters.data.RouterException
import com.troks.tomatorouters.data.SavedLogin
import com.troks.tomatorouters.data.SecureStore
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Text that is resolved in whatever language is selected when it's shown. */
typealias Message = (Strings) -> String

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)

    var language by mutableStateOf(AppLanguage.fromCode(store.languageCode))
        private set
    var login by mutableStateOf(store.load())
        private set

    /** Fingerprint / face unlock is available on this phone (hardware present and enrolled). */
    val biometricAvailable = biometricAvailable(app)

    /**
     * Saved password held back until fingerprint / face unlock succeeds. Set on app start
     * when a password was remembered and the phone supports biometrics.
     */
    private var lockedPassword: String? = null
    var biometricLocked by mutableStateOf(false)
        private set

    init {
        com.troks.tomatorouters.data.DeviceMemory.init(app)
        LogAlerts.schedule(app)
        if (biometricAvailable && login.remember && login.password.isNotEmpty()) {
            lockedPassword = login.password
            login = login.copy(password = "")
            biometricLocked = true
        }
    }

    /** Fingerprint / face confirmed: put the saved password back and sign in. */
    fun biometricUnlocked() {
        val pass = lockedPassword ?: return
        lockedPassword = null
        biometricLocked = false
        login = login.copy(password = pass)
        connect()
    }
    var connecting by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<Message?>(null)
        private set
    /** Set when the router's HTTPS certificate differs from the pinned one. */
    var certChanged by mutableStateOf<String?>(null)
        private set
    var api by mutableStateOf<RouterApi?>(null)
        private set
    /** True while connected to the simulated demo router. */
    var isDemo by mutableStateOf(false)
        private set

    /** Opens the app against a simulated router (fake addresses, nothing is sent anywhere). */
    fun startDemo(tomato64: Boolean = false) {
        isDemo = true
        loginError = null
        api = RouterApi(DemoShell(tomato64))
    }

    var themeMode by mutableStateOf(ThemeMode.fromName(store.themeMode))
        private set
    var dynamicColor by mutableStateOf(store.dynamicColor)
        private set

    fun changeLanguage(lang: AppLanguage) {
        language = lang
        store.languageCode = lang.code
    }

    fun changeTheme(mode: ThemeMode) {
        themeMode = mode
        store.themeMode = mode.name
    }

    fun changeDynamicColor(on: Boolean) {
        dynamicColor = on
        store.dynamicColor = on
    }

    var palette by mutableStateOf(ColorPalette.fromName(store.colorPalette))
        private set

    /** Picking a palette turns dynamic color off, since that would hide it. */
    fun changePalette(p: ColorPalette) {
        palette = p
        store.colorPalette = p.name
        if (dynamicColor) changeDynamicColor(false)
    }

    /** Set when a log alert notification was tapped: the system log opens (once logged in). */
    var openLogs by mutableStateOf(false)

    /** Log alerts while the app is on screen, and how often to check then (1 / 3 / 5 min). */
    var logAlertsOpen by mutableStateOf(LogAlerts.openOn(app))
        private set
    var logAlertOpenMinutes by mutableStateOf(LogAlerts.openMinutes(app))
        private set
    /** Log alerts in the background, while the app is closed. */
    var logAlertsClosed by mutableStateOf(LogAlerts.closedOn(app))
        private set
    var logAlertLevels by mutableStateOf(LogAlerts.levels(app))
        private set
    /** Background check period in minutes (15 / 30 / 60, or a custom value of 60+). */
    var logAlertMinutes by mutableStateOf(LogAlerts.intervalMinutes(app))
        private set

    fun changeLogAlertsOpen(on: Boolean) {
        logAlertsOpen = on
        LogAlerts.setOpenOn(getApplication(), on)
    }

    fun changeLogAlertsClosed(on: Boolean) {
        logAlertsClosed = on
        LogAlerts.setClosedOn(getApplication(), on)
    }

    fun changeLogAlertOpenMinutes(minutes: Int) {
        LogAlerts.setOpenMinutes(getApplication(), minutes)
        logAlertOpenMinutes = LogAlerts.openMinutes(getApplication())
    }

    /** Adds or removes a level; at least one and at most [LogAlerts.MAX_LEVELS] stay chosen. */
    fun toggleLogAlertLevel(level: LogLevel) {
        val next = if (level in logAlertLevels) logAlertLevels - level else logAlertLevels + level
        if (next.isEmpty() || next.size > LogAlerts.MAX_LEVELS) return
        logAlertLevels = next
        LogAlerts.setLevels(getApplication(), next)
    }

    var logAlertCustom by mutableStateOf(LogAlerts.intervalIsCustom(app))
        private set

    /** Saves the background check interval; false when [minutes] isn't allowed. */
    fun changeLogAlertMinutes(minutes: Int, custom: Boolean): Boolean {
        if (!LogAlerts.setIntervalMinutes(getApplication(), minutes, custom)) return false
        logAlertMinutes = minutes
        logAlertCustom = custom
        return true
    }

    /** Text size for low-vision users: one of [TEXT_SCALES], applied on top of the system font size. */
    var textScale by mutableStateOf(store.textScale.let { saved -> TEXT_SCALES.minBy { kotlin.math.abs(it - saved) } })
        private set

    fun changeTextScale(scale: Float) {
        textScale = scale
        store.textScale = scale
    }

    fun update(l: SavedLogin) {
        // typing a password by hand replaces the saved one; turning "remember" off drops biometric unlock
        if (biometricLocked && (l.password.isNotEmpty() || !l.remember)) {
            lockedPassword = null
            biometricLocked = false
        }
        login = l
        loginError = null
    }

    fun connect() {
        val l = login
        // Fixed review login for Google Play: "demo" / "demo" opens the simulated router (no network, any address).
        if (l.username.trim().equals(DEMO_LOGIN, ignoreCase = true) && l.password == DEMO_LOGIN) {
            startDemo()
            return
        }
        // same, but acting like Tomato64 ("demo64" / "demo") to try its WiFi handling
        if (l.username.trim().equals(DEMO_LOGIN + "64", ignoreCase = true) && l.password == DEMO_LOGIN) {
            startDemo(tomato64 = true)
            return
        }
        val host = l.host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (host.isEmpty()) { loginError = { it.enterHost }; return }
        if (l.port !in 1..65535) { loginError = { it.badPort }; return }
        if (l.username.isBlank()) { loginError = { it.enterUsername }; return }
        connecting = true
        loginError = null
        certChanged = null
        viewModelScope.launch {
            val cfg = RouterConfig(host, l.port, l.https, l.username.trim(), l.password, store.certPin(host, l.port))
            val client = RouterClient(cfg)
            try {
                client.connect()
                if (l.https && cfg.pinnedCert == null) store.setCertPin(host, l.port, client.seenCertFingerprint)
                store.save(l.copy(host = host))
                isDemo = false
                api = RouterApi(client)
            } catch (e: Exception) {
                if (e is CertChangedException) certChanged = e.seen
                loginError = { s -> friendlyError(e, s) }
            } finally {
                connecting = false
            }
        }
    }

    /** User confirmed the new certificate is legitimate (e.g. it was regenerated). */
    fun trustNewCertificate() {
        store.setCertPin(login.host.trim(), login.port, null)
        certChanged = null
        connect()
    }

    /** Google Play donations; connects only when the donate sheet is first opened. */
    val donations by lazy { com.troks.tomatorouters.data.Donations(app, viewModelScope) }
    private var donationsUsed = false
    fun openDonations() { donationsUsed = true; donations.load() }

    override fun onCleared() { if (donationsUsed) donations.close() }

    fun logout() {
        api = null
        isDemo = false
        if (!login.remember) login = login.copy(password = "")
    }
}

private const val DEMO_LOGIN = "demo"

/** Text size steps offered in Settings. */
val TEXT_SCALES = listOf(1f, 1.15f, 1.3f, 1.5f)

fun friendlyError(e: Throwable, s: Strings): String = when (e) {
    is RouterException -> when (e.err) {
        Err.AUTH -> s.errAuth
        Err.NOT_TOMATO -> s.errNotTomato
        Err.HTTP -> s.errHttp(e.detail?.toIntOrNull() ?: 0)
        Err.HTTPS -> s.errHttps(e.detail.orEmpty())
        Err.BAD_RESPONSE -> s.errBadResponse
        Err.BAD_MAC -> s.errBadMac
        Err.BAD_SSID -> s.errBadSsid
        Err.BAD_WIFI_PASS -> s.errBadWifiPass
        Err.BAD_HOST -> s.errBadHost
        Err.BAD_VALUE -> s.m.invalidValue
        Err.IP_IN_USE -> s.m.ipInUse
    }
    is CertChangedException -> s.errCertChanged
    is UnknownHostException -> s.errUnknownHost
    is ConnectException -> s.errConnect
    is SocketTimeoutException -> s.errTimeout
    else -> e.message ?: e.javaClass.simpleName
}
