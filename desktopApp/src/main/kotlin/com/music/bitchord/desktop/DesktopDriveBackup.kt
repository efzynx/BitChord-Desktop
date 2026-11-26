package com.music.bitchord.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress
import java.util.UUID
import java.util.prefs.Preferences

enum class DesktopBackupFrequency(val intervalMs: Long) {
    OFF(0L),
    DAILY(24L * 60 * 60 * 1000),
    WEEKLY(7L * 24 * 60 * 60 * 1000),
    MONTHLY(30L * 24 * 60 * 60 * 1000),
}

/**
 * Manual & automatic Google Drive backup and restore coordinator for Desktop.
 *
 * Stores compressed snapshots in Google Drive's hidden `appDataFolder` under this device's ID,
 * and allows listing or restoring backups from this computer or any synced backup snapshot.
 */
internal object DesktopDriveBackup {

    private const val KIND_KEY = "kind"
    private const val KIND_DESKTOP_BACKUP = "desktop_backup"
    private const val DEVICE_ID_KEY = "deviceId"
    private const val DEVICE_NAME_KEY = "deviceName"
    private const val UPDATED_AT_KEY = "updatedAt"
    private const val APP_VERSION_KEY = "versionName"

    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_ACCOUNT_NAME = "account_name"
    private const val KEY_FREQUENCY = "backup_frequency"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_NEEDS_SIGN_IN = "needs_sign_in"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"

    private const val SCHEDULER_TICK_MS = 30L * 60 * 1000
    private const val SCHEDULER_INITIAL_DELAY_MS = 15_000L

    data class Settings(
        val accountEmail: String? = null,
        val accountName: String? = null,
        val frequency: DesktopBackupFrequency = DesktopBackupFrequency.DAILY,
        val lastBackupAt: Long = 0L,
        val lastError: String? = null,
        val needsSignIn: Boolean = false,
        val deviceId: String = "",
        val deviceName: String = "",
        val hasRefreshToken: Boolean = false,
    ) {
        val connected: Boolean get() = accountEmail != null
    }

    data class RemoteBackup(
        val fileId: String,
        val name: String,
        val deviceId: String,
        val deviceName: String,
        val updatedAtMs: Long,
        val sizeBytes: Long,
        val versionName: String,
    )

    sealed interface Progress {
        data object Idle : Progress
        data class Running(val step: String) : Progress
    }

    sealed interface BackupsState {
        data object Unknown : BackupsState
        data object Loading : BackupsState
        data class Loaded(val items: List<RemoteBackup>) : BackupsState
        data class Failed(val message: String) : BackupsState
    }

    sealed interface ActionResult {
        data class BackedUp(val fileName: String, val sizeBytes: Long, val timestampMs: Long) : ActionResult
        data class Restored(val summary: DesktopBackup.Summary, val sourceName: String) : ActionResult
        data class Failed(val message: String, val restoring: Boolean) : ActionResult
    }

    private val prefs: Preferences = Preferences.userRoot().node("com.music.bitchord.desktop.drive")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var schedulerJob: Job? = null

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _progress = MutableStateFlow<Progress>(Progress.Idle)
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _backups = MutableStateFlow<BackupsState>(BackupsState.Unknown)
    val backups: StateFlow<BackupsState> = _backups.asStateFlow()

    private val _result = MutableStateFlow<ActionResult?>(null)
    val result: StateFlow<ActionResult?> = _result.asStateFlow()

    fun defaultDeviceName(): String =
        runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: System.getProperty("user.name")?.takeIf(String::isNotBlank)?.let { "$it's Computer" }
            ?: "Desktop"

    fun clearResult() {
        _result.value = null
    }

    fun setFrequency(frequency: DesktopBackupFrequency) {
        prefs.put(KEY_FREQUENCY, frequency.name)
        runCatching { prefs.flush() }
        _settings.update { it.copy(frequency = frequency) }
    }

    fun setDeviceName(name: String) {
        val clean = name.trim().ifBlank { defaultDeviceName() }
        prefs.put(KEY_DEVICE_NAME, clean)
        runCatching { prefs.flush() }
        _settings.update { it.copy(deviceName = clean) }
    }

    fun startScheduler() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch {
            delay(SCHEDULER_INITIAL_DELAY_MS)
            while (isActive) {
                runCatching {
                    if (isAutoBackupDue(_settings.value, System.currentTimeMillis())) {
                        backupNow(automatic = true)
                    }
                }
                delay(SCHEDULER_TICK_MS)
            }
        }
    }

    internal fun isAutoBackupDue(
        state: Settings,
        nowMs: Long,
        hasToken: Boolean = DesktopDriveAuth.hasAnyToken(),
    ): Boolean {
        if (!state.connected || state.needsSignIn || state.frequency == DesktopBackupFrequency.OFF) return false
        if (!hasToken) return false
        if (state.lastBackupAt <= 0L) return true
        return nowMs - state.lastBackupAt >= state.frequency.intervalMs
    }

    suspend fun connectWithBuiltIn(): Result<Settings> = runCatching {
        val token = DesktopDriveAuth.signInWithBuiltIn()
        completeConnection(token)
    }

    suspend fun connectWithBrowser(clientId: String, clientSecret: String): Result<Settings> = runCatching {
        val token = DesktopDriveAuth.signInWithBrowser(clientId, clientSecret, saveAsByo = true)
        completeConnection(token)
    }

    suspend fun connectWithAccessToken(token: String): Result<Settings> = runCatching {
        DesktopDriveAuth.storeDirectAccessToken(token)
        completeConnection(token.trim())
    }

    private suspend fun completeConnection(token: String): Settings {
        val profile = DesktopDriveApi.profile(token)
        val email = profile.email ?: error("Google Drive did not return an account email")
        prefs.put(KEY_ACCOUNT_EMAIL, email)
        prefs.put(KEY_ACCOUNT_NAME, profile.name ?: email)
        prefs.putBoolean(KEY_NEEDS_SIGN_IN, false)
        prefs.remove(KEY_LAST_ERROR)
        ensureDeviceIdentity()
        runCatching { prefs.flush() }
        val updated = loadSettings()
        _settings.value = updated
        refreshBackups()
        return updated
    }

    suspend fun disconnect() {
        syncMutex.withLock {
            DesktopDriveAuth.revokeAndClear()
            DesktopListeningStats.clearAllPeerBuckets()
            prefs.remove(KEY_ACCOUNT_EMAIL)
            prefs.remove(KEY_ACCOUNT_NAME)
            prefs.remove(KEY_LAST_BACKUP_AT)
            prefs.remove(KEY_LAST_ERROR)
            prefs.putBoolean(KEY_NEEDS_SIGN_IN, false)
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
            _backups.value = BackupsState.Unknown
            _result.value = null
        }
    }

    fun isCurrentDevice(backup: RemoteBackup): Boolean =
        backup.deviceId.isNotBlank() && backup.deviceId == _settings.value.deviceId

    fun backupNowAsync(onSynced: () -> Unit = {}) {
        scope.launch {
            if (backupNow(automatic = false)) {
                syncPeerReplayInternal()
                onSynced()
            }
        }
    }

    private suspend fun syncPeerReplayInternal() = syncMutex.withLock {
        val state = _settings.value
        if (!state.connected) return@withLock
        val myDeviceId = state.deviceId
        runCatching {
            val remoteList = (_backups.value as? BackupsState.Loaded)?.items ?: return@runCatching
            val otherDevices = remoteList.filter { it.deviceId.isNotBlank() && it.deviceId != myDeviceId }
            DesktopListeningStats.retainOnlyPeerDevices(otherDevices.mapTo(HashSet()) { it.deviceId })
            withAuthorizedToken { token ->
                otherDevices.forEach { remote ->
                    val raw = DesktopDriveApi.download(token, remote.fileId, remote.sizeBytes)
                    val jsonBytes = if (raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
                        DesktopBackup.gunzip(raw)
                    } else {
                        raw
                    }
                    val parsed = DesktopBackup.decodeJsonBytes(jsonBytes)
                    DesktopListeningStats.setPeerBuckets(remote.deviceId, parsed.listening)
                }
            }
        }
    }

    suspend fun backupNow(automatic: Boolean = false): Boolean = syncMutex.withLock {
        val state = _settings.value
        if (!state.connected) return@withLock false
        _progress.value = Progress.Running(if (automatic) "Auto-backing up..." else "Preparing backup...")
        try {
            val deviceId = ensureDeviceIdentity()
            val deviceName = _settings.value.deviceName.ifBlank { defaultDeviceName() }
            val snapshot = DesktopBackup.captureSnapshot()
            val compressed = DesktopBackup.gzip(DesktopBackup.encodeJsonBytes(snapshot, pretty = false))
            val nowMs = System.currentTimeMillis()
            val fileName = "bitchord-desktop-backup-$deviceId.json.gz"

            _progress.value = Progress.Running("Uploading to Google Drive...")
            val uploaded = withAuthorizedToken { token ->
                val files = DesktopDriveApi.listAll(token)
                val existing = files.firstOrNull {
                    it.properties[KIND_KEY] == KIND_DESKTOP_BACKUP && it.properties[DEVICE_ID_KEY] == deviceId
                } ?: files.firstOrNull { it.name == fileName }
                DesktopDriveApi.put(
                    token = token,
                    name = fileName,
                    bytes = compressed,
                    properties = mapOf(
                        KIND_KEY to KIND_DESKTOP_BACKUP,
                        DEVICE_ID_KEY to deviceId,
                        DEVICE_NAME_KEY to deviceName,
                        UPDATED_AT_KEY to nowMs.toString(),
                        APP_VERSION_KEY to DesktopSystemInfo.appVersion,
                    ),
                    existingId = existing?.id,
                )
            }
            prefs.putLong(KEY_LAST_BACKUP_AT, nowMs)
            prefs.putBoolean(KEY_NEEDS_SIGN_IN, false)
            prefs.remove(KEY_LAST_ERROR)
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
            if (!automatic) {
                _result.value = ActionResult.BackedUp(uploaded.name, compressed.size.toLong(), nowMs)
            }
            refreshBackupsLocked()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: DesktopDriveApi.Unauthorized) {
            prefs.putBoolean(KEY_NEEDS_SIGN_IN, true)
            prefs.put(KEY_LAST_ERROR, e.message ?: "Sign in again")
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
            if (!automatic) _result.value = ActionResult.Failed(e.message ?: "Sign in again", restoring = false)
            false
        } catch (e: Throwable) {
            val msg = e.message ?: "Google Drive backup failed"
            prefs.put(KEY_LAST_ERROR, msg)
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
            if (!automatic) _result.value = ActionResult.Failed(msg, restoring = false)
            false
        } finally {
            _progress.value = Progress.Idle
        }
    }

    fun refreshBackupsAsync() {
        scope.launch { refreshBackups() }
    }

    suspend fun refreshBackups() {
        if (!_settings.value.connected) return
        syncMutex.withLock { refreshBackupsLocked() }
    }

    private suspend fun refreshBackupsLocked() {
        _backups.value = BackupsState.Loading
        runCatching {
            withAuthorizedToken { token ->
                DesktopDriveApi.listAll(token)
                    .filter {
                        it.properties[KIND_KEY] == KIND_DESKTOP_BACKUP ||
                            it.name.startsWith("bitchord-desktop-backup-") ||
                            it.name == DesktopDriveApi.LEGACY_FILE_NAME
                    }
                    .map { file ->
                        RemoteBackup(
                            fileId = file.id,
                            name = file.name,
                            deviceId = file.properties[DEVICE_ID_KEY].orEmpty(),
                            deviceName = file.properties[DEVICE_NAME_KEY]?.takeIf(String::isNotBlank)
                                ?: file.name.removePrefix("bitchord-desktop-backup-").removeSuffix(".json.gz"),
                            updatedAtMs = file.properties[UPDATED_AT_KEY]?.toLongOrNull() ?: 0L,
                            sizeBytes = file.sizeBytes,
                            versionName = file.properties[APP_VERSION_KEY].orEmpty(),
                        )
                    }
                    .sortedByDescending { it.updatedAtMs }
            }
        }.fold(
            onSuccess = { _backups.value = BackupsState.Loaded(it) },
            onFailure = { e ->
                if (e is DesktopDriveApi.Unauthorized) {
                    prefs.putBoolean(KEY_NEEDS_SIGN_IN, true)
                    _settings.value = loadSettings()
                }
                _backups.value = BackupsState.Failed(e.message ?: "Could not load backups from Google Drive")
            },
        )
    }

    fun restoreBackupAsync(backup: RemoteBackup, onRestored: () -> Unit) {
        scope.launch {
            syncMutex.withLock {
                _progress.value = Progress.Running("Downloading ${backup.deviceName}...")
                try {
                    val rawBytes = withAuthorizedToken { token ->
                        DesktopDriveApi.download(token, backup.fileId, backup.sizeBytes)
                    }
                    _progress.value = Progress.Running("Restoring backup...")
                    val jsonBytes = if (rawBytes.size >= 2 && rawBytes[0] == 0x1f.toByte() && rawBytes[1] == 0x8b.toByte()) {
                        DesktopBackup.gunzip(rawBytes)
                    } else {
                        rawBytes
                    }
                    val parsed = DesktopBackup.decodeJsonBytes(jsonBytes)
                    val summary = DesktopBackup.applySnapshot(parsed)
                    _result.value = ActionResult.Restored(summary, backup.deviceName)
                    onRestored()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    _result.value = ActionResult.Failed(e.message ?: "Restore failed", restoring = true)
                } finally {
                    _progress.value = Progress.Idle
                }
            }
        }
    }

    fun deleteBackupAsync(backup: RemoteBackup, onDeleted: () -> Unit = {}) {
        scope.launch {
            syncMutex.withLock {
                runCatching {
                    withAuthorizedToken { token -> DesktopDriveApi.delete(token, backup.fileId) }
                    if (backup.deviceId.isNotBlank() && backup.deviceId != _settings.value.deviceId) {
                        DesktopListeningStats.removePeerDevice(backup.deviceId)
                    }
                    refreshBackupsLocked()
                    onDeleted()
                }.onFailure { e ->
                    _result.value = ActionResult.Failed(e.message ?: "Delete failed", restoring = false)
                }
            }
        }
    }

    private suspend fun <T> withAuthorizedToken(block: suspend (token: String) -> T): T {
        val firstToken = DesktopDriveAuth.freshAccessToken(forceRefresh = false)
            ?: throw DesktopDriveApi.Unauthorized()
        return try {
            block(firstToken)
        } catch (_: DesktopDriveApi.Unauthorized) {
            val refreshed = DesktopDriveAuth.freshAccessToken(forceRefresh = true)
                ?: throw DesktopDriveApi.Unauthorized()
            block(refreshed)
        }
    }

    private fun ensureDeviceIdentity(): String {
        val existing = prefs.get(KEY_DEVICE_ID, null)?.takeIf(String::isNotBlank)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.put(KEY_DEVICE_ID, created)
        if (prefs.get(KEY_DEVICE_NAME, null).isNullOrBlank()) {
            prefs.put(KEY_DEVICE_NAME, defaultDeviceName())
        }
        return created
    }

    private fun loadSettings(): Settings {
        val freqName = prefs.get(KEY_FREQUENCY, DesktopBackupFrequency.DAILY.name)
        val freq = DesktopBackupFrequency.entries.firstOrNull { it.name == freqName } ?: DesktopBackupFrequency.DAILY
        return Settings(
            accountEmail = prefs.get(KEY_ACCOUNT_EMAIL, null)?.takeIf(String::isNotBlank),
            accountName = prefs.get(KEY_ACCOUNT_NAME, null)?.takeIf(String::isNotBlank),
            frequency = freq,
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, 0L),
            lastError = prefs.get(KEY_LAST_ERROR, null)?.takeIf(String::isNotBlank),
            needsSignIn = prefs.getBoolean(KEY_NEEDS_SIGN_IN, false),
            deviceId = prefs.get(KEY_DEVICE_ID, null).orEmpty(),
            deviceName = prefs.get(KEY_DEVICE_NAME, null)?.takeIf(String::isNotBlank) ?: defaultDeviceName(),
            hasRefreshToken = DesktopDriveAuth.hasRefreshToken(),
        )
    }
}
