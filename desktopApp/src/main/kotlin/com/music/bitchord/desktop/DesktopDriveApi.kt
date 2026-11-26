package com.music.bitchord.desktop

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Google Drive v3 `appDataFolder` client for desktop backup & restore.
 */
internal object DesktopDriveApi {

    const val LEGACY_FILE_NAME = "bitchord-backup.json.gz"
    private const val MIME = "application/gzip"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    private const val FIELDS = "id,name,md5Checksum,modifiedTime,size,appProperties"

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=UTF-8".toMediaType()

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    class Unauthorized : IOException("Google Drive authorization expired or was revoked")
    class DriveError(val code: Int, message: String) : IOException(message)

    data class DriveFile(
        val id: String,
        val name: String,
        val md5: String?,
        val modifiedAt: String,
        val sizeBytes: Long,
        val properties: Map<String, String>,
    )

    data class Profile(val email: String?, val name: String?)

    suspend fun profile(token: String): Profile = withContext(Dispatchers.IO) {
        val url = "$API/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(emailAddress,displayName)")
            .build()
        val body = execute(Request.Builder().url(url).authorized(token).build()).use { it.bodyJson() }
        val user = body["user"]?.jsonObject
        Profile(
            email = user?.get("emailAddress")?.jsonPrimitive?.contentOrNull,
            name = user?.get("displayName")?.jsonPrimitive?.contentOrNull,
        )
    }

    suspend fun listAll(token: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<DriveFile>()
        var page: String? = null
        do {
            ensureActive()
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "trashed = false")
                .addQueryParameter("fields", "nextPageToken,files($FIELDS)")
                .addQueryParameter("pageSize", "1000")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val body = execute(Request.Builder().url(url).authorized(token).build()).use { it.bodyJson() }
            body["files"]?.jsonArray?.mapNotNullTo(files) { it.jsonObject.toDriveFile() }
            page = body["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (page != null)
        files
    }

    suspend fun put(
        token: String,
        name: String,
        bytes: ByteArray,
        properties: Map<String, String>,
        existingId: String?,
    ): DriveFile = withContext(Dispatchers.IO) {
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", MIME)
            put("appProperties", buildJsonObject { properties.forEach { (k, v) -> put(k, v) } })
            if (existingId == null) put("parents", buildJsonArray { add(JsonPrimitive("appDataFolder")) })
        }.toString().toRequestBody(jsonType)
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata)
            .addPart(bytes.toRequestBody(MIME.toMediaType()))
            .build()
        val base = if (existingId == null) "$UPLOAD/files" else "$UPLOAD/files/$existingId"
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", FIELDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .authorized(token)
            .method(if (existingId == null) "POST" else "PATCH", body)
            .build()
        execute(request).use { it.bodyJson().toDriveFile() ?: error("Drive returned no file metadata") }
    }

    suspend fun download(
        token: String,
        id: String,
        expectedBytes: Long = 0L,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray = withContext(Dispatchers.IO) {
        val url = "$API/files/$id".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        execute(Request.Builder().url(url).authorized(token).build()).use { response ->
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: expectedBytes
            val out = ByteArrayOutputStream(total.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            var received = 0L
            onProgress(0, total)
            body.byteStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    received += read
                    onProgress(received, total)
                }
            }
            out.toByteArray()
        }
    }

    suspend fun delete(token: String, id: String): Unit = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$API/files/$id").authorized(token).delete().build()).close()
    }

    private fun Request.Builder.authorized(token: String): Request.Builder =
        header("Authorization", "Bearer $token")

    private fun execute(request: Request): Response {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) return response
        val code = response.code
        val message = response.use { extractErrorMessage(it.body.string(), code) }
        if (code == 401) throw Unauthorized()
        throw DriveError(code, message)
    }

    internal fun extractErrorMessage(raw: String, code: Int): String = runCatching {
        json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Google Drive returned HTTP $code"

    private fun JsonObject.toDriveFile(): DriveFile? {
        val id = get("id")?.jsonPrimitive?.contentOrNull ?: return null
        val name = get("name")?.jsonPrimitive?.contentOrNull ?: return null
        val props = get("appProperties")?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
            .orEmpty()
        return DriveFile(
            id = id,
            name = name,
            md5 = get("md5Checksum")?.jsonPrimitive?.contentOrNull,
            modifiedAt = get("modifiedTime")?.jsonPrimitive?.contentOrNull.orEmpty(),
            sizeBytes = get("size")?.jsonPrimitive?.longOrNull ?: 0L,
            properties = props,
        )
    }

    private fun Response.bodyJson(): JsonObject = json.parseToJsonElement(body.string()).jsonObject
}
