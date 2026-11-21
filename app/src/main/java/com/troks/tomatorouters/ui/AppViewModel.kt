package com.troks.tomatorouters.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.troks.tomatorouters.data.CertChangedException
import com.troks.tomatorouters.data.DemoShell
import com.troks.tomatorouters.data.Endpoint
import com.troks.tomatorouters.data.cleanHost
import com.troks.tomatorouters.data.connectTimeoutFor
import com.troks.tomatorouters.data.endpoints
import com.troks.tomatorouters.data.isNetworkError
import kotlinx.coroutines.CancellationException
import com.troks.tomatorouters.data.Err
import com.troks.tomatorouters.data.LogAlerts
import com.troks.tomatorouters.data.LogLevel
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.RouterClient
import com.troks.tomatorouters.data.RouterConfig
import com.troks.tomatorouters.data.RouterException
import com.troks.tomatorouters.data.SavedLogin
import com.troks.tomatorouters.data.Profile
import com.troks.tomatorouters.data.MAX_PROFILES
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

    /** Saved login profiles (up to [MAX_PROFILES]) and the one shown on the login screen. */
    var profiles by mutableStateOf(store.profiles())
        private set
    var activeProfileId by mutableStateOf(store.activeProfile()?.id)
        private set

    /** Shows [l] on the login screen, holding a remembered password back for fingerprint / face. */
    private fun show(l: SavedLogin) {
        lockedPassword = null
        biometricLocked = false
        login = l
        if (biometricAvailable && l.remember && l.password.isNotEmpty()) {
            lockedPassword = l.password
            login = l.copy(password = "")
            biometricLocked = true
        }
        loginError = null
        certChanged = null
    }

    /** Picking a name fills in everything saved for it (addresses, ports, user, password). */
    fun selectProfile(id: String) {
        if (connecting) return
        val p = profiles.firstOrNull { it.id == id } ?: return
        store.activeProfileId = id
        activeProfileId = id
        show(p.login)
    }

    /**
     * Adds a named profile and switches to it. The very first one takes what's already typed in;
     * later ones start from the defaults. False when [MAX_PROFILES] already exist.
     */
    fun addProfile(name: String): Boolean {
        if (profiles.size >= MAX_PROFILES || name.isBlank()) return false
        val base = if (profiles.isEmpty()) login.copy(password = lockedPassword ?: login.password) else SavedLogin()
        val p = Profile(store.newProfileId(), name.trim(), base)
        val list = profiles + p
        store.saveProfiles(list)
        profiles = list
        selectProfile(p.id)
        return true
    }

    fun renameProfile(id: String, name: String) {
        if (name.isBlank()) return
        val list = profiles.map { if (it.id == id) it.copy(name = name.trim()) else it }
        store.saveProfiles(list)
        profiles = list
    }

    /** Removes a profile (and its saved password); the next one, if any, is shown instead. */
    fun deleteProfile(id: String) {
        if (api != null && id == connectedProfileId) logout()
        val list = profiles.filterNot { it.id == id }
        store.saveProfiles(list)
        profiles = list
        if (activeProfileId == id) {
            val next = list.firstOrNull()
            if (next != null) selectProfile(next.id)
            else {
                store.activeProfileId = null
                activeProfileId = null
                show(SavedLogin())
            }
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
        val host = cleanHost(l.host)
        if (host.isEmpty()) { loginError = { it.enterHost }; return }
        if (l.port !in 1..65535) { loginError = { it.badPort }; return }
        if (cleanHost(l.remoteHost).isNotEmpty() && l.remotePort !in 1..65535) { loginError = { it.badPort }; return }
        if (l.username.isBlank()) { loginError = { it.enterUsername }; return }
        connecting = true
        loginError = null
        certChanged = null
        certEndpoint = null
        viewModelScope.launch {
            // local address first (at home), then the external one (away from home)
            val eps = l.endpoints(getApplication())
            val failures = mutableListOf<Pair<Endpoint, Exception>>()
            try {
                for ((i, ep) in eps.withIndex()) {
                    val pin = store.certPin(ep.host, ep.port)
                    val cfg = RouterConfig(ep.host, ep.port, ep.https, l.username.trim(), l.password, pin)
                    val client = RouterClient(cfg, connectTimeoutSeconds = connectTimeoutFor(hasFallback = i < eps.lastIndex))
                    try {
                        client.connect()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // any failure on the local address (not home, or another network's router
                        // answering at the same address) moves on to the external one
                        failures += ep to e
                        continue
                    }
                    if (ep.https && pin == null) store.setCertPin(ep.host, ep.port, client.seenCertFingerprint)
                    store.save(l.copy(host = host, remoteHost = cleanHost(l.remoteHost)))
                    profiles = store.profiles()
                    activeProfileId = store.activeProfile()?.id
                    isDemo = false
                    connectedVia = ep
                    connectedProfileId = activeProfileId
                    api = RouterApi(client)
                    return@launch
                }
                // nothing worked: show the most telling reason. An address that answered and said
                // no (wrong password, changed certificate) beats one that simply couldn't be reached.
                val (ep, e) = failures.firstOrNull { !isNetworkError(it.second) } ?: failures.last()
                if (e is CertChangedException) { certChanged = e.seen; certEndpoint = ep }
                loginError = { s -> friendlyError(e, s) }
            } finally {
                connecting = false
            }
        }
    }


    /** The profile the current session belongs to (the login screen may show another one). */
    var connectedProfileId by mutableStateOf<String?>(null)
        private set

    /**
     * Quick switch from the main screen: signs out and connects to [id] in one step. Without a
     * saved password (or while it waits for fingerprint / face) it stops at the login screen.
     */
    fun switchProfile(id: String) {
        if (connecting) return
        if (api != null && !isDemo && id == connectedProfileId) return
        logout()
        selectProfile(id)
        if (!biometricLocked && login.username.isNotBlank() && login.password.isNotEmpty()) connect()
    }
    /** The address the current session uses (local or external); null when not connected. */
    var connectedVia by mutableStateOf<Endpoint?>(null)
        private set

    /** The address whose certificate changed (see [certChanged]). */
    private var certEndpoint: Endpoint? = null

    /** User confirmed the new certificate is legitimate (e.g. it was regenerated). */
    fun trustNewCertificate() {
        val ep = certEndpoint
        if (ep != null) store.setCertPin(ep.host, ep.port, null)
        else store.setCertPin(cleanHost(login.host), login.port, null)
        certChanged = null
        certEndpoint = null
        connect()
    }

    /** Google Play donations; connects only when the donate sheet is first opened. */
    val donations by lazy { com.troks.tomatorouters.data.Donations(app, viewModelScope) }
    private var donationsUsed = false
    fun openDonations() { donationsUsed = true; donations.load() }

    override fun onCleared() { if (donationsUsed) donations.close() }

    fun logout() {
        api = null
        connectedVia = null
        connectedProfileId = null
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
