package com.troks.tomatorouters.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.X509TrustManager

data class RouterConfig(
    val host: String,
    val port: Int,
    val https: Boolean,
    val username: String,
    val password: String,
    /** SHA-256 of the router's HTTPS certificate, pinned on first connection (trust on first use). */
    val pinnedCert: String? = null,
) {
    val baseUrl: String
        get() {
            val h = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            return "${if (https) "https" else "http"}://$h:$port"
        }
}

/** Error kinds; the UI turns them into text in the selected language. */
enum class Err { AUTH, NOT_TOMATO, HTTP, HTTPS, BAD_RESPONSE, BAD_MAC, BAD_SSID, BAD_WIFI_PASS, BAD_HOST, BAD_VALUE, IP_IN_USE }

class RouterException(val err: Err, val detail: String? = null) :
    IOException(err.name + (detail?.let { ": $it" } ?: ""))

class CertChangedException(val seen: String) : IOException("certificate changed")

/**
 * Talks to the FreshTomato web UI (httpd):
 *  - HTTP Basic auth on every request
 *  - an anti-CSRF token ("http_id", looks like TIDxxxxxxxx) scraped from any .asp page,
 *    sent as `_http_id` on every POST
 *  - `shell.cgi` (Tools > System Commands) runs a shell command as root and returns
 *    `cmdresult = '<js-escaped output>';`
 */
/** Anything that can run a shell command on a router: the real one, or the demo. */
interface RouterShell {
    suspend fun shell(command: String): String
}

class RouterClient(val config: RouterConfig) : RouterShell {

    /** Fingerprint of the certificate the server presented (for HTTPS). */
    @Volatile
    var seenCertFingerprint: String? = null
        private set

    @Volatile
    private var httpId: String? = null

    private val http: OkHttpClient = buildClient()

    private fun buildClient(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // older routers only speak older TLS suites
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
                        .header("Referer", config.baseUrl + "/")
                        .build()
                )
            }
        if (config.https) {
            // Routers use self-signed certificates, so instead of a CA check we pin the
            // certificate the first time and refuse any different one later.
            val tm = PinningTrustManager(config.pinnedCert) { seenCertFingerprint = it }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(tm), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, tm)
            b.hostnameVerifier { _, _ -> true } // the pin replaces hostname verification
        }
        return b.build()
    }

    /** Checks credentials and fetches the http_id token. */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val page = get("/status-overview.asp")
        httpId = TID_REGEX.find(page)?.value
            ?: throw RouterException(Err.NOT_TOMATO)
    }

    /** Runs a shell command on the router and returns its output. */
    override suspend fun shell(command: String): String = withContext(Dispatchers.IO) {
        if (httpId == null) connect()
        try {
            runShell(command)
        } catch (e: RouterException) {
            if (e.err != Err.HTTP && e.err != Err.BAD_RESPONSE) throw e
            // token may have expired (router rebooted) - refresh once
            connect()
            runShell(command)
        }
    }

    private fun runShell(command: String): String {
        val body = FormBody.Builder()
            .add("action", "execute")
            .add("command", command)
            .add("_http_id", httpId ?: "")
            .build()
        val req = Request.Builder().url(config.baseUrl + "/shell.cgi").post(body).build()
        val text = execute(req)
        return parseCmdResult(text)
    }

    private fun get(path: String): String =
        execute(Request.Builder().url(config.baseUrl + path).get().build())

    private fun execute(req: Request): String {
        try {
            http.newCall(req).execute().use { r ->
                if (r.code == 401) throw RouterException(Err.AUTH)
                if (!r.isSuccessful) throw RouterException(Err.HTTP, r.code.toString())
                return r.body.string()
            }
        } catch (e: SSLException) {
            val cause = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is CertChangedException }
            if (cause != null) throw cause as CertChangedException
            throw RouterException(Err.HTTPS, e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        private val TID_REGEX = Regex("TID[0-9a-fA-F]{4,}")

        fun parseCmdResult(text: String): String {
            val start = text.indexOf("cmdresult")
            if (start < 0) {
                if (text.contains("<html", ignoreCase = true)) throw RouterException(Err.BAD_RESPONSE)
                return text
            }
            val q = text.indexOf('\'', start)
            val end = text.lastIndexOf('\'')
            if (q < 0 || end <= q) return ""
            return unescapeJs(text.substring(q + 1, end))
        }

        fun unescapeJs(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c != '\\' || i + 1 >= s.length) { sb.append(c); i++; continue }
                when (val n = s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'x' -> {
                        val hex = s.substring(i + 2, minOf(i + 4, s.length))
                        val v = hex.toIntOrNull(16)
                        if (v != null && hex.length == 2) { if (v != 0x0d) sb.append(v.toChar()); i += 4 } else { sb.append(n); i += 2 }
                    }
                    'u' -> {
                        val hex = s.substring(i + 2, minOf(i + 6, s.length))
                        val v = hex.toIntOrNull(16)
                        if (v != null && hex.length == 4) { sb.append(v.toChar()); i += 6 } else { sb.append(n); i += 2 }
                    }
                    else -> { sb.append(n); i += 2 }
                }
            }
            return sb.toString()
        }

        /** Quotes a value for safe use inside a POSIX shell command. */
        fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
    }
}

private class PinningTrustManager(
    private val expected: String?,
    private val onSeen: (String) -> Unit,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
        val fp = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            .joinToString(":") { "%02X".format(it) }
        onSeen(fp)
        if (expected != null && !expected.equals(fp, ignoreCase = true)) {
            throw CertificateException(CertChangedException(fp))
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}
