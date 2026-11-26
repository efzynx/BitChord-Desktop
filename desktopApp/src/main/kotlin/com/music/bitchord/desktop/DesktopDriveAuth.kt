package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.awt.Desktop
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences

/**
 * Desktop OAuth 2.0 + PKCE authentication for Google Drive (`drive.appdata` scope).
 *
 * Uses a local loopback HTTP server (`http://127.0.0.1:<ephemeral>/callback`) with the system
 * browser so desktop installs obtain a real refresh token for scheduled background backups.
 * Also supports direct OAuth access-token entry for quick manual MVP testing.
 */
internal object DesktopDriveAuth {

    const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
    private const val AUTH_TIMEOUT_MS = 180_000L

    private val prefs: Preferences = Preferences.userRoot().node("com.music.bitchord.desktop.drive")
    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    private const val KEY_CLIENT_ID = "oauth_client_id"
    private const val KEY_CLIENT_SECRET = "oauth_client_secret"
    private const val KEY_CLIENT_SECRET_DPAPI = "oauth_client_secret_dpapi"
    private const val KEY_OAUTH_MODE = "oauth_client_mode"
    private const val MODE_BUILT_IN = "built_in"
    private const val MODE_BYO = "byo"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_ACCESS_TOKEN_DPAPI = "access_token_dpapi"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_REFRESH_TOKEN_DPAPI = "refresh_token_dpapi"
    private const val KEY_EXPIRES_AT_MS = "expires_at_ms"

    /** Maintainer-supplied Google Cloud Desktop OAuth Client ID (injected via Gradle/env). */
    fun builtInClientId(): String =
        System.getProperty("bitchord.gdrive.clientId")?.trim().orEmpty()
            .ifBlank { System.getenv("BITCHORD_GDRIVE_CLIENT_ID").orEmpty().trim() }

    /** Optional Client Secret corresponding to [builtInClientId]. */
    fun builtInClientSecret(): String =
        System.getProperty("bitchord.gdrive.clientSecret")?.trim().orEmpty()
            .ifBlank { System.getenv("BITCHORD_GDRIVE_CLIENT_SECRET").orEmpty().trim() }

    fun hasBuiltInClient(): Boolean = builtInClientId().isNotBlank()

    fun savedClientId(): String =
        DesktopPreferenceChunks.read(prefs, KEY_CLIENT_ID)?.trim().orEmpty()

    fun savedClientSecret(): String = readProtected(KEY_CLIENT_SECRET_DPAPI, KEY_CLIENT_SECRET)

    fun saveOAuthClient(clientId: String, clientSecret: String) {
        DesktopPreferenceChunks.write(prefs, KEY_CLIENT_ID, clientId.trim())
        writeProtected(KEY_CLIENT_SECRET_DPAPI, KEY_CLIENT_SECRET, clientSecret.trim())
        runCatching { prefs.flush() }
    }

    fun hasRefreshToken(): Boolean = readProtected(KEY_REFRESH_TOKEN_DPAPI, KEY_REFRESH_TOKEN).isNotBlank()

    fun hasAnyToken(): Boolean =
        readProtected(KEY_ACCESS_TOKEN_DPAPI, KEY_ACCESS_TOKEN).isNotBlank() || hasRefreshToken()

    suspend fun signInWithBuiltIn(): String {
        val cid = builtInClientId()
        require(cid.isNotBlank()) {
            "Built-in Google OAuth Client ID is not configured in this build. Use Custom OAuth (BYO) instead."
        }
        return signInWithBrowser(
            clientIdInput = cid,
            clientSecretInput = builtInClientSecret(),
            saveAsByo = false,
        )
    }

    suspend fun signInWithBrowser(
        clientIdInput: String = savedClientId(),
        clientSecretInput: String = savedClientSecret(),
        saveAsByo: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        val clientId = clientIdInput.trim()
        require(clientId.isNotBlank()) {
            "Enter a Google OAuth Desktop Client ID first"
        }
        if (saveAsByo) {
            saveOAuthClient(clientId, clientSecretInput)
            prefs.put(KEY_OAUTH_MODE, MODE_BYO)
        } else {
            prefs.put(KEY_OAUTH_MODE, MODE_BUILT_IN)
        }
        runCatching { prefs.flush() }

        val state = randomUrlSafe(24)
        val verifier = randomUrlSafe(48)
        val challenge = pkceS256(verifier)
        val callbackResult = CompletableDeferred<Result<String>>()

        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1)
        val port = server.address.port
        val redirectUri = "http://127.0.0.1:$port/callback"

        server.createContext("/callback") { exchange ->
            val query = exchange.requestURI.rawQuery.orEmpty()
            val params = parseQuery(query)
            val returnedState = params["state"]
            val code = params["code"]
            val err = params["error"]
            val html = when {
                err != null -> {
                    callbackResult.complete(Result.failure(IllegalStateException("Google sign-in denied: $err")))
                    "<html><body style='font-family:sans-serif;padding:32px'><h2>Sign-in cancelled</h2><p>$err</p></body></html>"
                }
                returnedState != state || code.isNullOrBlank() -> {
                    callbackResult.complete(Result.failure(IllegalStateException("Invalid OAuth callback state")))
                    "<html><body style='font-family:sans-serif;padding:32px'><h2>Invalid authorization response</h2></body></html>"
                }
                else -> {
                    callbackResult.complete(Result.success(code))
                    "<html><body style='font-family:sans-serif;padding:32px'><h2>BitChord Connected</h2><p>Google Drive access granted. You can close this tab and return to BitChord.</p></body></html>"
                }
            }
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val authUrl = AUTH_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("redirect_uri", redirectUri)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("scope", SCOPE_APPDATA)
                .addQueryParameter("code_challenge", challenge)
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("state", state)
                .addQueryParameter("access_type", "offline")
                .addQueryParameter("prompt", "consent")
                .build()
                .toString()

            openBrowser(authUrl)
            val code = withTimeout(AUTH_TIMEOUT_MS) { callbackResult.await() }.getOrThrow()
            val tokens = exchangeCode(
                clientId = clientId,
                clientSecret = clientSecretInput.trim(),
                code = code,
                verifier = verifier,
                redirectUri = redirectUri,
            )
            storeTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresAtMs)
            tokens.accessToken
        } finally {
            server.stop(0)
        }
    }

    fun storeDirectAccessToken(token: String) {
        val cleaned = token.trim()
        require(cleaned.isNotBlank()) { "Access token cannot be empty" }
        // OAuth playground / direct tokens typically live ~1 hour.
        storeTokens(cleaned, refreshToken = null, expiresAtMs = System.currentTimeMillis() + 50 * 60_000L)
    }

    suspend fun freshAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val current = readProtected(KEY_ACCESS_TOKEN_DPAPI, KEY_ACCESS_TOKEN)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
        if (!forceRefresh && current.isNotBlank() && (expiresAt == 0L || System.currentTimeMillis() < expiresAt - 60_000L)) {
            return@withContext current
        }

        val refresh = readProtected(KEY_REFRESH_TOKEN_DPAPI, KEY_REFRESH_TOKEN)
        val useBuiltIn = prefs.get(KEY_OAUTH_MODE, MODE_BYO) == MODE_BUILT_IN
        val clientId = if (useBuiltIn) builtInClientId().ifBlank { savedClientId() } else savedClientId().ifBlank { builtInClientId() }
        if (refresh.isBlank() || clientId.isBlank()) {
            return@withContext current.takeIf { !forceRefresh && it.isNotBlank() }
        }

        val bodyBuilder = FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", refresh)
            .add("grant_type", "refresh_token")
        val secret = if (useBuiltIn) builtInClientSecret() else savedClientSecret()
        if (secret.isNotBlank()) bodyBuilder.add("client_secret", secret)

        val request = Request.Builder().url(TOKEN_ENDPOINT).post(bodyBuilder.build()).build()
        val response = Http.client.newCall(request).execute()
        val payload = response.use { resp ->
            val raw = resp.body.string()
            val obj = json.parseToJsonElement(raw).jsonObject
            if (!resp.isSuccessful) {
                val msg = obj["error_description"]?.jsonPrimitive?.contentOrNull
                    ?: obj["error"]?.jsonPrimitive?.contentOrNull
                    ?: "Token refresh failed (HTTP ${resp.code})"
                throw DesktopDriveApi.DriveError(resp.code, msg)
            }
            obj
        }
        val nextAccess = payload["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("No access_token returned on refresh")
        val expiresIn = payload["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        val nextRefresh = payload["refresh_token"]?.jsonPrimitive?.contentOrNull
        storeTokens(nextAccess, nextRefresh, System.currentTimeMillis() + expiresIn * 1000L)
        nextAccess
    }

    suspend fun revokeAndClear() = withContext(Dispatchers.IO) {
        val token = readProtected(KEY_REFRESH_TOKEN_DPAPI, KEY_REFRESH_TOKEN)
            .ifBlank { readProtected(KEY_ACCESS_TOKEN_DPAPI, KEY_ACCESS_TOKEN) }
        if (token.isNotBlank()) {
            runCatching {
                val request = Request.Builder()
                    .url(REVOKE_ENDPOINT)
                    .post(FormBody.Builder().add("token", token).build())
                    .build()
                Http.client.newCall(request).execute().close()
            }
        }
        writeProtected(KEY_ACCESS_TOKEN_DPAPI, KEY_ACCESS_TOKEN, "")
        writeProtected(KEY_REFRESH_TOKEN_DPAPI, KEY_REFRESH_TOKEN, "")
        prefs.remove(KEY_EXPIRES_AT_MS)
        runCatching { prefs.flush() }
    }

    internal fun pkceS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    internal fun parseQuery(rawQuery: String): Map<String, String> =
        rawQuery.split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null
                else {
                    val k = java.net.URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8)
                    val v = java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8)
                    k to v
                }
            }.toMap()

    private data class TokenBundle(val accessToken: String, val refreshToken: String?, val expiresAtMs: Long)

    private fun exchangeCode(
        clientId: String,
        clientSecret: String,
        code: String,
        verifier: String,
        redirectUri: String,
    ): TokenBundle {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
        if (clientSecret.isNotBlank()) form.add("client_secret", clientSecret)

        val response = Http.client.newCall(Request.Builder().url(TOKEN_ENDPOINT).post(form.build()).build()).execute()
        return response.use { resp ->
            val raw = resp.body.string()
            val obj = json.parseToJsonElement(raw).jsonObject
            if (!resp.isSuccessful) {
                val msg = obj["error_description"]?.jsonPrimitive?.contentOrNull
                    ?: obj["error"]?.jsonPrimitive?.contentOrNull
                    ?: "OAuth exchange failed (HTTP ${resp.code})"
                error(msg)
            }
            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull
                ?: error("OAuth response omitted access_token")
            val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
            TokenBundle(access, refresh, System.currentTimeMillis() + expiresIn * 1000L)
        }
    }

    private fun storeTokens(accessToken: String, refreshToken: String?, expiresAtMs: Long) {
        writeProtected(KEY_ACCESS_TOKEN_DPAPI, KEY_ACCESS_TOKEN, accessToken)
        if (!refreshToken.isNullOrBlank()) {
            writeProtected(KEY_REFRESH_TOKEN_DPAPI, KEY_REFRESH_TOKEN, refreshToken)
        }
        prefs.putLong(KEY_EXPIRES_AT_MS, expiresAtMs)
        runCatching { prefs.flush() }
    }

    private fun readProtected(dpapiKey: String, plainKey: String): String {
        val sealed = DesktopPreferenceChunks.read(prefs, dpapiKey)
        if (!sealed.isNullOrBlank()) {
            val unsealed = runCatching { Base64.getDecoder().decode(sealed) }.getOrNull()
                ?.let(DesktopWindowsCrypto::unprotect)
                ?.toString(StandardCharsets.UTF_8)
            if (unsealed != null) return unsealed
        }
        return DesktopPreferenceChunks.read(prefs, plainKey).orEmpty()
    }

    private fun writeProtected(dpapiKey: String, plainKey: String, value: String) {
        if (value.isBlank()) {
            DesktopPreferenceChunks.remove(prefs, dpapiKey)
            DesktopPreferenceChunks.remove(prefs, plainKey)
            return
        }
        if (DesktopPlatform.isWindows) {
            val sealed = DesktopWindowsCrypto.protect(value.toByteArray(StandardCharsets.UTF_8))
                ?: error("Windows could not protect Drive OAuth token")
            DesktopPreferenceChunks.write(prefs, dpapiKey, Base64.getEncoder().encodeToString(sealed))
            DesktopPreferenceChunks.remove(prefs, plainKey)
        } else {
            DesktopPreferenceChunks.write(prefs, plainKey, value)
        }
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun openBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            runCatching { Desktop.getDesktop().browse(URI(url)) }.onSuccess { return }
        }
        when {
            DesktopPlatform.isLinux -> ProcessBuilder("xdg-open", url).start()
            DesktopPlatform.isMac -> ProcessBuilder("open", url).start()
            DesktopPlatform.isWindows -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
            else -> error("Could not launch system browser for Google sign-in")
        }
    }
}
