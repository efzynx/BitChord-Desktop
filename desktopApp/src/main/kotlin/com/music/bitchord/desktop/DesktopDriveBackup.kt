package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences

enum class DesktopBackupFrequency(val intervalMs: Long) {
    OFF(0L),
    DAILY(24L * 60 * 60 * 1000),
    WEEKLY(7L * 24 * 60 * 60 * 1000),
    MONTHLY(30L * 24 * 60 * 60 * 1000),
}

/** How many backup snapshots are kept per device in Google Drive before pruning the oldest. */
enum class DesktopBackupRetention(val maxCount: Int) {
    FIVE(5),
    TEN(10),
    TWENTY(20),
    UNLIMITED(0),
}

enum class RemoteDeviceKind {
    DESKTOP,
    MOBILE,
}

/**
 * Multi-device & multi-snapshot Google Drive backup and restore coordinator for Desktop.
 *
 * Each computer maintains its own device ID session and appends timestamped backup snapshots
 * in Google Drive's hidden `appDataFolder` instead of overwriting a single file. Also reads
 * other Desktop sessions and Android (`kind = "device"`) sessions on the same Google account for
 * cross-device Replay listening aggregation and selectable restore.
 */
internal object DesktopDriveBackup {

    private const val KIND_KEY = "kind"
    private const val KIND_DESKTOP_BACKUP = "desktop_backup"
    private const val KIND_MOBILE_DEVICE = "device"
    private const val KIND_MOBILE_REPLAY = "replay"
    private const val DEVICE_ID_KEY = "deviceId"
    private const val DEVICE_NAME_KEY = "deviceName"
    private const val BACKUP_ID_KEY = "backupId"
    private const val UPDATED_AT_KEY = "updatedAt"
    private const val APP_VERSION_KEY = "versionName"
    private const val TRIGGER_KEY = "trigger"
    private const val SUMMARY_KEY = "summary"
    private const val MONTH_KEY = "month"

    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_ACCOUNT_NAME = "account_name"
    private const val KEY_FREQUENCY = "backup_frequency"
    private const val KEY_RETENTION = "backup_retention"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_NEEDS_SIGN_IN = "needs_sign_in"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_DEVICE_READY = "device_ready"
    private const val KEY_PEER_STAMP_PREFIX = "peer_stamp_"

    private const val SCHEDULER_TICK_MS = 30L * 60 * 1000
    private const val SCHEDULER_INITIAL_DELAY_MS = 15_000L

    private val FILENAME_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    private val MOBILE_MONTH_REGEX = Regex("""\d{4}-\d{2}""")
    private val SUMMARY_REGEX = Regex("""(\d+)p,(\d+)l,(\d+)m""")
    private val mobileJson = Json { ignoreUnknownKeys = true }

    data class Settings(
        val accountEmail: String? = null,
        val accountName: String? = null,
        val frequency: DesktopBackupFrequency = DesktopBackupFrequency.DAILY,
        val retention: DesktopBackupRetention = DesktopBackupRetention.TEN,
        val lastBackupAt: Long = 0L,
        val lastError: String? = null,
        val needsSignIn: Boolean = false,
        val needsDeviceSetup: Boolean = false,
        val deviceId: String = "",
        val deviceName: String = "",
        val hasRefreshToken: Boolean = false,
    ) {
        val connected: Boolean get() = accountEmail != null
    }

    data class RemoteBackup(
        val fileId: String,
        val name: String,
        val backupId: String = "",
        val deviceId: String,
        val deviceName: String,
        val updatedAtMs: Long,
        val sizeBytes: Long,
        val versionName: String,
        val isAutoBackup: Boolean = false,
        val playlistsCount: Int? = null,
        val likedCount: Int? = null,
        val monthsCount: Int? = null,
        val md5: String? = null,
        val deviceKind: RemoteDeviceKind = RemoteDeviceKind.DESKTOP,
    )

    data class RemoteDeviceGroup(
        val deviceId: String,
        val deviceName: String,
        val kind: RemoteDeviceKind,
        val latestUpdatedAtMs: Long,
        val totalSizeBytes: Long,
        val versionName: String,
        val snapshots: List<RemoteBackup>,
        val mobileMonthFiles: Map<String, DesktopDriveApi.DriveFile> = emptyMap(),
        val mobileMonthsCount: Int = 0,
    ) {
        val snapshotCount: Int get() = if (kind == RemoteDeviceKind.MOBILE) 1 else snapshots.size
        val latestSnapshot: RemoteBackup? get() = snapshots.firstOrNull()
    }

    sealed interface Progress {
        data object Idle : Progress
        data class Running(val step: String) : Progress
    }

    sealed interface BackupsState {
        data object Unknown : BackupsState
        data object Loading : BackupsState
        data class Loaded(
            val items: List<RemoteBackup>,
            val devices: List<RemoteDeviceGroup> = emptyList(),
        ) : BackupsState
        data class Failed(val message: String) : BackupsState
    }

    sealed interface ActionResult {
        data class BackedUp(val fileName: String, val sizeBytes: Long, val timestampMs: Long) : ActionResult
        data class Restored(
            val summary: DesktopBackup.Summary,
            val sourceName: String,
            val fromMobile: Boolean = false,
            val settingsApplied: Int = summary.settingsCount,
        ) : ActionResult
        data class Failed(val message: String, val restoring: Boolean) : ActionResult
    }

    @Serializable
    internal data class MobilePrefValue(
        val type: String = "",
        val value: String? = null,
        val values: List<String> = emptyList(),
    )

    @Serializable
    internal data class MobileDeviceManifest(
        val schema: Int = 1,
        val deviceId: String = "",
        val name: String = "",
        val ownerInstall: String = "",
        val updatedAt: Long = 0L,
        val versionName: String = "",
        val months: Map<String, String> = emptyMap(),
        val settings: Map<String, MobilePrefValue> = emptyMap(),
    )

    @Serializable
    internal data class MobileTrackEntry(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val album: String? = null,
        val albumId: String? = null,
        val art: String? = null,
        val ms: Long = 0L,
        val plays: Int = 0,
    )

    @Serializable
    internal data class MobileStoredBucket(
        val version: Int = 1,
        val month: String = "",
        val tracks: List<MobileTrackEntry> = emptyList(),
        val hours: List<Long> = emptyList(),
        val days: Map<Int, Long> = emptyMap(),
    )

    private val prefs: Preferences = Preferences.userRoot().node("com.music.bitchord.desktop.drive")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val mobileManifestCache = ConcurrentHashMap<String, Pair<String?, MobileDeviceManifest>>()
    private var schedulerJob: Job? = null
    private var renameSyncJob: Job? = null

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

    fun setRetention(retention: DesktopBackupRetention) {
        prefs.put(KEY_RETENTION, retention.name)
        runCatching { prefs.flush() }
        _settings.update { it.copy(retention = retention) }
    }

    fun setDeviceName(name: String) {
        val clean = name.trim().take(60).ifBlank { defaultDeviceName() }
        prefs.put(KEY_DEVICE_NAME, clean)
        runCatching { prefs.flush() }
        _settings.update { it.copy(deviceName = clean) }
        val currentId = _settings.value.deviceId
        if (currentId.isNotBlank()) {
            _backups.update { bs ->
                if (bs is BackupsState.Loaded) {
                    bs.copy(
                        devices = bs.devices.map { group ->
                            if (group.kind == RemoteDeviceKind.DESKTOP && group.deviceId == currentId) {
                                group.copy(deviceName = clean)
                            } else {
                                group
                            }
                        },
                    )
                } else {
                    bs
                }
            }
            if (_settings.value.connected && !_settings.value.needsDeviceSetup) {
                renameSyncJob?.cancel()
                renameSyncJob = scope.launch { syncDeviceNameToDrive(currentId) }
            }
        }
    }

    private suspend fun syncDeviceNameToDrive(deviceId: String) = syncMutex.withLock {
        val state = _settings.value
        if (!state.connected || state.needsDeviceSetup || state.deviceId != deviceId) return@withLock
        val targetName = clampDriveProperty(DEVICE_NAME_KEY, state.deviceName.ifBlank { defaultDeviceName() })
        runCatching {
            withAuthorizedToken { token ->
                val files = DesktopDriveApi.listAll(token)
                val ownFiles = files.filter { file ->
                    isDesktopBackupFile(file) && resolvedDesktopDeviceId(file) == deviceId
                }
                var updatedAny = false
                ownFiles.forEach { file ->
                    if (file.properties[DEVICE_NAME_KEY] != targetName) {
                        DesktopDriveApi.updateProperties(
                            token = token,
                            fileId = file.id,
                            properties = mapOf(DEVICE_NAME_KEY to targetName),
                        )
                        updatedAny = true
                    }
                }
                if (updatedAny) {
                    val refreshedFiles = DesktopDriveApi.listAll(token)
                    val current = _settings.value
                    val groups = loadDeviceGroupsFromFiles(token, refreshedFiles, current.deviceId, current.deviceName)
                    _backups.value = BackupsState.Loaded(
                        items = flattenDeviceSnapshots(groups),
                        devices = groups,
                    )
                }
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            if (e is DesktopDriveApi.Unauthorized) {
                prefs.putBoolean(KEY_NEEDS_SIGN_IN, true)
                runCatching { prefs.flush() }
                _settings.value = loadSettings()
            }
            DesktopTrackLog.log("drive: device name sync skipped (${e.message})")
        }
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
        if (!state.connected || state.needsSignIn || state.needsDeviceSetup || state.frequency == DesktopBackupFrequency.OFF) {
            return false
        }
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

    private suspend fun completeConnection(token: String): Settings = syncMutex.withLock {
        val profile = DesktopDriveApi.profile(token)
        val email = profile.email ?: error("Google Drive did not return an account email")
        prefs.put(KEY_ACCOUNT_EMAIL, email)
        prefs.put(KEY_ACCOUNT_NAME, profile.name ?: email)
        prefs.putBoolean(KEY_NEEDS_SIGN_IN, false)
        prefs.remove(KEY_LAST_ERROR)

        val existingId = prefs.get(KEY_DEVICE_ID, null)?.takeIf(String::isNotBlank)
        val existingReady = prefs.getBoolean(KEY_DEVICE_READY, existingId != null)
        val files = DesktopDriveApi.listAll(token)
        val groups = loadDeviceGroupsFromFiles(token, files, existingId.orEmpty(), prefs.get(KEY_DEVICE_NAME, null))
        if (existingId != null && existingReady) {
            prefs.putBoolean(KEY_DEVICE_READY, true)
        } else if (groups.isEmpty()) {
            initializeNewDeviceLocked(defaultDeviceName())
        } else {
            // Existing devices already live on this Google account — let the user pick whether to
            // start a separate device session for this computer or restore/adopt one below.
            prefs.putBoolean(KEY_DEVICE_READY, false)
        }
        runCatching { prefs.flush() }
        val updated = loadSettings()
        _settings.value = updated
        val allItems = flattenDeviceSnapshots(groups)
        _backups.value = BackupsState.Loaded(items = allItems, devices = groups)
        syncPeerReplayLocked(token, groups)
        updated
    }

    suspend fun disconnect() {
        syncMutex.withLock {
            DesktopDriveAuth.revokeAndClear()
            DesktopListeningStats.clearAllPeerBuckets()
            clearAllPeerStamps()
            mobileManifestCache.clear()
            prefs.remove(KEY_ACCOUNT_EMAIL)
            prefs.remove(KEY_ACCOUNT_NAME)
            prefs.remove(KEY_LAST_BACKUP_AT)
            prefs.remove(KEY_LAST_ERROR)
            prefs.putBoolean(KEY_NEEDS_SIGN_IN, false)
            prefs.remove(KEY_DEVICE_READY)
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
            _backups.value = BackupsState.Unknown
            _result.value = null
        }
    }

    fun isCurrentDevice(backup: RemoteBackup): Boolean =
        backup.deviceKind == RemoteDeviceKind.DESKTOP &&
            backup.deviceId.isNotBlank() &&
            !_settings.value.needsDeviceSetup &&
            backup.deviceId == _settings.value.deviceId

    fun isCurrentDevice(group: RemoteDeviceGroup): Boolean =
        group.kind == RemoteDeviceKind.DESKTOP &&
            group.deviceId.isNotBlank() &&
            !_settings.value.needsDeviceSetup &&
            group.deviceId == _settings.value.deviceId

    fun chooseNewDeviceAsync(
        deviceName: String = _settings.value.deviceName,
        syncImmediately: Boolean = true,
        onSynced: () -> Unit = {},
    ) {
        scope.launch {
            syncMutex.withLock {
                initializeNewDeviceLocked(deviceName)
                prefs.remove(KEY_LAST_BACKUP_AT)
                prefs.remove(KEY_LAST_ERROR)
                runCatching { prefs.flush() }
                _settings.value = loadSettings()
            }
            if (syncImmediately) {
                if (backupNow(automatic = false)) {
                    onSynced()
                }
            } else {
                refreshBackups(onReplaySynced = onSynced)
            }
        }
    }

    fun startNewDeviceSessionAsync(
        deviceName: String = defaultDeviceName(),
        onDone: () -> Unit = {},
    ) {
        chooseNewDeviceAsync(deviceName = deviceName, syncImmediately = false, onSynced = onDone)
    }

    fun backupNowAsync(onSynced: () -> Unit = {}) {
        scope.launch {
            if (backupNow(automatic = false)) {
                onSynced()
            }
        }
    }

    suspend fun backupNow(automatic: Boolean = false): Boolean = syncMutex.withLock {
        val state = _settings.value
        if (!state.connected || state.needsDeviceSetup) return@withLock false
        _progress.value = Progress.Running(if (automatic) "Auto-backing up..." else "Preparing backup...")
        try {
            val deviceId = ensureDeviceIdentityLocked()
            val deviceName = _settings.value.deviceName.ifBlank { defaultDeviceName() }
            val snapshot = DesktopBackup.captureSnapshot()
            val compressed = DesktopBackup.gzip(DesktopBackup.encodeJsonBytes(snapshot, pretty = false))
            val nowMs = System.currentTimeMillis()
            val backupId = UUID.randomUUID().toString().replace("-", "").take(12)
            val fileName = buildBackupFilename(deviceId, nowMs, backupId)
            val properties = buildBackupProperties(
                deviceId = deviceId,
                deviceName = deviceName,
                backupId = backupId,
                updatedAtMs = nowMs,
                versionName = DesktopSystemInfo.appVersion,
                automatic = automatic,
                playlists = snapshot.playlists.size,
                liked = snapshot.likedIds.size,
                months = snapshot.listening.size,
            )

            _progress.value = Progress.Running("Uploading to Google Drive...")
            val uploaded = withAuthorizedToken { token ->
                val putFile = DesktopDriveApi.put(
                    token = token,
                    name = fileName,
                    bytes = compressed,
                    properties = properties,
                    existingId = null,
                )
                pruneExcessDeviceSnapshotsLocked(token, deviceId, _settings.value.retention.maxCount)
                putFile
            }
            prefs.putLong(KEY_LAST_BACKUP_AT, nowMs)
            prefs.putBoolean(KEY_NEEDS_SIGN_IN, false)
            prefs.putBoolean(KEY_DEVICE_READY, true)
            prefs.remove(KEY_LAST_ERROR)
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
            if (!automatic) {
                _result.value = ActionResult.BackedUp(uploaded.name, compressed.size.toLong(), nowMs)
            }
            refreshBackupsAndSyncPeersLocked()
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

    fun refreshBackupsAsync(onReplaySynced: () -> Unit = {}) {
        scope.launch { refreshBackups(silent = false, onReplaySynced = onReplaySynced) }
    }

    suspend fun refreshBackups(
        silent: Boolean = false,
        onReplaySynced: () -> Unit = {},
    ) {
        if (!_settings.value.connected) return
        val syncedChanged = syncMutex.withLock { refreshBackupsAndSyncPeersLocked(silent = silent) }
        if (syncedChanged) onReplaySynced()
    }

    private suspend fun refreshBackupsAndSyncPeersLocked(silent: Boolean = false): Boolean {
        if (!silent || _backups.value !is BackupsState.Loaded) {
            _backups.value = BackupsState.Loading
        }
        var replayChanged = false
        runCatching {
            withAuthorizedToken { token ->
                val files = DesktopDriveApi.listAll(token)
                val current = _settings.value
                val groups = loadDeviceGroupsFromFiles(token, files, current.deviceId, current.deviceName)
                replayChanged = syncPeerReplayLocked(token, groups)
                groups
            }
        }.fold(
            onSuccess = { groups ->
                _backups.value = BackupsState.Loaded(
                    items = flattenDeviceSnapshots(groups),
                    devices = groups,
                )
            },
            onFailure = { e ->
                if (e is DesktopDriveApi.Unauthorized) {
                    prefs.putBoolean(KEY_NEEDS_SIGN_IN, true)
                    runCatching { prefs.flush() }
                    _settings.value = loadSettings()
                }
                if (!silent || _backups.value !is BackupsState.Loaded) {
                    _backups.value = BackupsState.Failed(e.message ?: "Could not load backups from Google Drive")
                }
            },
        )
        return replayChanged
    }

    private suspend fun pruneExcessDeviceSnapshotsLocked(token: String, deviceId: String, keepCount: Int) {
        if (keepCount <= 0) return
        val files = DesktopDriveApi.listAll(token)
        val deviceSnapshots = files
            .filter(::isDesktopBackupFile)
            .map { toRemoteBackup(it) }
            .filter { it.deviceId == deviceId }
        val toDelete = selectSnapshotsToPrune(deviceSnapshots, keepCount)
        toDelete.forEach { old ->
            runCatching { DesktopDriveApi.delete(token, old.fileId) }
        }
    }

    private suspend fun syncPeerReplayLocked(token: String, groups: List<RemoteDeviceGroup>): Boolean {
        val state = _settings.value
        if (!state.connected) return false
        val myDeviceId = state.deviceId
        val peers = selectLatestPeerDevices(groups, myDeviceId)
        val peerIds = peers.mapTo(HashSet()) { it.deviceId }
        val previousCount = DesktopListeningStats.peerDeviceCount()
        DesktopListeningStats.retainOnlyPeerDevices(peerIds)
        retainOnlyPeerStamps(peerIds)
        var changed = DesktopListeningStats.peerDeviceCount() != previousCount

        peers.forEach { peer ->
            val stamp = peerSyncStamp(peer)
            val cachedStamp = prefs.get(peerStampKey(peer.deviceId), null)
            if (cachedStamp == stamp && DesktopListeningStats.hasPeerDevice(peer.deviceId)) {
                return@forEach
            }
            runCatching {
                when (peer.kind) {
                    RemoteDeviceKind.DESKTOP -> {
                        val latest = peer.latestSnapshot ?: return@runCatching
                        val raw = DesktopDriveApi.download(token, latest.fileId, latest.sizeBytes)
                        val parsed = DesktopBackup.decodeJsonBytes(maybeGunzip(raw))
                        DesktopListeningStats.setPeerBuckets(peer.deviceId, parsed.listening)
                    }
                    RemoteDeviceKind.MOBILE -> {
                        val buckets = downloadMobileBuckets(token, peer.mobileMonthFiles)
                        DesktopListeningStats.setPeerBuckets(peer.deviceId, buckets)
                    }
                }
                prefs.put(peerStampKey(peer.deviceId), stamp)
                changed = true
            }
        }
        if (changed) runCatching { prefs.flush() }
        return changed
    }

    fun restoreBackupAsync(
        backup: RemoteBackup,
        adoptDeviceSession: Boolean = false,
        onRestored: () -> Unit,
    ) {
        scope.launch {
            syncMutex.withLock {
                _progress.value = Progress.Running("Downloading ${backup.deviceName}...")
                try {
                    if (backup.deviceKind == RemoteDeviceKind.MOBILE) {
                        restoreMobileDeviceLocked(backup)
                    } else {
                        restoreDesktopBackupLocked(backup, adoptDeviceSession)
                    }
                    refreshBackupsAndSyncPeersLocked()
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

    private suspend fun restoreDesktopBackupLocked(backup: RemoteBackup, adoptDeviceSession: Boolean) {
        val rawBytes = withAuthorizedToken { token ->
            DesktopDriveApi.download(token, backup.fileId, backup.sizeBytes)
        }
        _progress.value = Progress.Running("Restoring backup...")
        val parsed = DesktopBackup.decodeJsonBytes(maybeGunzip(rawBytes))
        val summary = DesktopBackup.applySnapshot(parsed)

        if (adoptDeviceSession && backup.deviceId.isNotBlank() && backup.deviceId != LEGACY_DEVICE_ID) {
            prefs.put(KEY_DEVICE_ID, backup.deviceId)
            prefs.put(KEY_DEVICE_NAME, backup.deviceName.ifBlank { defaultDeviceName() })
            prefs.putBoolean(KEY_DEVICE_READY, true)
            if (backup.updatedAtMs > 0L) {
                prefs.putLong(KEY_LAST_BACKUP_AT, backup.updatedAtMs)
            }
            DesktopListeningStats.removePeerDevice(backup.deviceId)
            prefs.remove(peerStampKey(backup.deviceId))
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
        } else if (_settings.value.needsDeviceSetup || _settings.value.deviceId.isBlank()) {
            initializeNewDeviceLocked(defaultDeviceName())
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
        }

        _result.value = ActionResult.Restored(summary = summary, sourceName = backup.deviceName, fromMobile = false)
    }

    private suspend fun restoreMobileDeviceLocked(backup: RemoteBackup) {
        val currentLoaded = (_backups.value as? BackupsState.Loaded)?.devices.orEmpty()
        val group = currentLoaded.firstOrNull {
            it.kind == RemoteDeviceKind.MOBILE && it.deviceId == backup.deviceId
        }
        val (manifest, buckets) = withAuthorizedToken { token ->
            val manifestBytes = maybeGunzip(DesktopDriveApi.download(token, backup.fileId))
            val parsedManifest = decodeMobileManifest(manifestBytes)
            val monthFiles = group?.mobileMonthFiles ?: run {
                DesktopDriveApi.listAll(token)
                    .filter { it.properties[KIND_KEY] == KIND_MOBILE_REPLAY && it.properties[DEVICE_ID_KEY] == backup.deviceId }
                    .mapNotNull { file -> file.properties[MONTH_KEY]?.let { it to file } }
                    .toMap()
            }
            val downloaded = downloadMobileBuckets(token, monthFiles)
            parsedManifest to downloaded
        }
        _progress.value = Progress.Running("Restoring Replay & preferences...")
        if (buckets.isNotEmpty()) {
            DesktopListeningStats.importBackupBuckets(buckets)
        }
        val appliedPrefs = applyMobileSettingsToDesktop(manifest.settings)
        if (_settings.value.needsDeviceSetup || _settings.value.deviceId.isBlank()) {
            initializeNewDeviceLocked(defaultDeviceName())
            runCatching { prefs.flush() }
            _settings.value = loadSettings()
        }
        val summary = DesktopBackup.Summary(
            months = buckets.size,
            playlists = 0,
            likedCount = 0,
            settingsCount = appliedPrefs,
            fromVersion = manifest.versionName,
            exportedAt = manifest.updatedAt.toString(),
        )
        _result.value = ActionResult.Restored(
            summary = summary,
            sourceName = manifest.name.ifBlank { backup.deviceName },
            fromMobile = true,
            settingsApplied = appliedPrefs,
        )
    }

    fun deleteBackupAsync(backup: RemoteBackup, onDeleted: () -> Unit = {}) {
        scope.launch {
            syncMutex.withLock {
                runCatching {
                    withAuthorizedToken { token ->
                        if (backup.deviceKind == RemoteDeviceKind.MOBILE) {
                            deleteMobileDeviceFilesLocked(token, backup.deviceId)
                        } else {
                            DesktopDriveApi.delete(token, backup.fileId)
                        }
                    }
                    refreshBackupsAndSyncPeersLocked()
                    onDeleted()
                }.onFailure { e ->
                    _result.value = ActionResult.Failed(e.message ?: "Delete failed", restoring = false)
                }
            }
        }
    }

    fun deleteDeviceAsync(device: RemoteDeviceGroup, onDeleted: () -> Unit = {}) {
        scope.launch {
            syncMutex.withLock {
                runCatching {
                    withAuthorizedToken { token ->
                        if (device.kind == RemoteDeviceKind.MOBILE) {
                            deleteMobileDeviceFilesLocked(token, device.deviceId)
                        } else {
                            device.snapshots.forEach { snap ->
                                DesktopDriveApi.delete(token, snap.fileId)
                            }
                        }
                    }
                    if (isCurrentDevice(device)) {
                        prefs.remove(KEY_LAST_BACKUP_AT)
                        prefs.put(KEY_FREQUENCY, DesktopBackupFrequency.OFF.name)
                        runCatching { prefs.flush() }
                        _settings.value = loadSettings()
                    } else if (device.deviceId.isNotBlank()) {
                        DesktopListeningStats.removePeerDevice(device.deviceId)
                        prefs.remove(peerStampKey(device.deviceId))
                        runCatching { prefs.flush() }
                    }
                    refreshBackupsAndSyncPeersLocked()
                    onDeleted()
                }.onFailure { e ->
                    _result.value = ActionResult.Failed(e.message ?: "Delete failed", restoring = false)
                }
            }
        }
    }

    private suspend fun deleteMobileDeviceFilesLocked(token: String, deviceId: String) {
        val files = DesktopDriveApi.listAll(token)
        files.filter { file ->
            file.properties[DEVICE_ID_KEY] == deviceId &&
                (file.properties[KIND_KEY] == KIND_MOBILE_DEVICE || file.properties[KIND_KEY] == KIND_MOBILE_REPLAY)
        }.forEach { file ->
            DesktopDriveApi.delete(token, file.id)
        }
        DesktopListeningStats.removePeerDevice(deviceId)
        prefs.remove(peerStampKey(deviceId))
        runCatching { prefs.flush() }
    }

    // ---- Pure Helpers & Parsers (also tested directly) ---------------------

    internal fun clampDriveProperty(key: String, value: String, maxTotalBytes: Int = 124): String {
        val maxValBytes = (maxTotalBytes - key.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)
        if (value.toByteArray(Charsets.UTF_8).size <= maxValBytes) return value
        val sb = StringBuilder()
        var used = 0
        for (ch in value) {
            val charBytes = ch.toString().toByteArray(Charsets.UTF_8).size
            if (used + charBytes > maxValBytes) break
            sb.append(ch)
            used += charBytes
        }
        return sb.toString()
    }

    internal fun buildBackupFilename(deviceId: String, timestampMs: Long, backupId: String): String {
        val stamp = FILENAME_TIME_FMT.format(Instant.ofEpochMilli(timestampMs))
        val cleanId = deviceId.ifBlank { LEGACY_DEVICE_ID }
        return "bitchord-desktop-backup-$cleanId-$stamp-$backupId.json.gz"
    }

    internal fun buildBackupProperties(
        deviceId: String,
        deviceName: String,
        backupId: String,
        updatedAtMs: Long,
        versionName: String,
        automatic: Boolean,
        playlists: Int,
        liked: Int,
        months: Int,
    ): Map<String, String> = mapOf(
        KIND_KEY to KIND_DESKTOP_BACKUP,
        DEVICE_ID_KEY to clampDriveProperty(DEVICE_ID_KEY, deviceId),
        DEVICE_NAME_KEY to clampDriveProperty(DEVICE_NAME_KEY, deviceName),
        BACKUP_ID_KEY to clampDriveProperty(BACKUP_ID_KEY, backupId),
        UPDATED_AT_KEY to updatedAtMs.toString(),
        APP_VERSION_KEY to clampDriveProperty(APP_VERSION_KEY, versionName),
        TRIGGER_KEY to if (automatic) "auto" else "manual",
        SUMMARY_KEY to "${playlists}p,${liked}l,${months}m",
    )

    internal fun selectSnapshotsToPrune(snapshots: List<RemoteBackup>, keepCount: Int): List<RemoteBackup> {
        if (keepCount <= 0 || snapshots.size <= keepCount) return emptyList()
        return snapshots
            .sortedWith(compareByDescending<RemoteBackup> { it.updatedAtMs }.thenByDescending { it.name })
            .drop(keepCount)
    }

    internal fun selectLatestPeerDevices(groups: List<RemoteDeviceGroup>, myDeviceId: String): List<RemoteDeviceGroup> =
        groups.filter { group ->
            group.deviceId.isNotBlank() &&
                group.deviceId != LEGACY_DEVICE_ID &&
                !(group.kind == RemoteDeviceKind.DESKTOP && group.deviceId == myDeviceId)
        }

    internal fun buildRemoteDeviceGroups(
        files: List<DesktopDriveApi.DriveFile>,
        mobileManifests: Map<String, MobileDeviceManifest>,
        currentDeviceId: String,
        currentDeviceName: String? = null,
    ): List<RemoteDeviceGroup> {
        val desktopGroups = files
            .filter(::isDesktopBackupFile)
            .map(::toRemoteBackup)
            .groupBy { it.deviceId.ifBlank { LEGACY_DEVICE_ID } }
            .map { (devId, snaps) ->
                val sorted = snaps.sortedWith(
                    compareByDescending<RemoteBackup> { it.updatedAtMs }.thenByDescending { it.name },
                )
                val newest = sorted.first()
                val resolvedName = when {
                    devId == currentDeviceId && !currentDeviceName.isNullOrBlank() -> currentDeviceName
                    newest.deviceName.isNotBlank() -> newest.deviceName
                    else -> "Desktop"
                }
                RemoteDeviceGroup(
                    deviceId = devId,
                    deviceName = resolvedName,
                    kind = RemoteDeviceKind.DESKTOP,
                    latestUpdatedAtMs = newest.updatedAtMs,
                    totalSizeBytes = sorted.sumOf { it.sizeBytes },
                    versionName = newest.versionName,
                    snapshots = sorted,
                )
            }

        val mobileMonthFilesByDevice = files
            .filter { it.properties[KIND_KEY] == KIND_MOBILE_REPLAY }
            .groupBy { it.properties[DEVICE_ID_KEY].orEmpty() }
            .filterKeys { it.isNotBlank() }
            .mapValues { (_, list) ->
                list.mapNotNull { file -> file.properties[MONTH_KEY]?.takeIf(String::isNotBlank)?.let { it to file } }.toMap()
            }

        val mobileGroups = files
            .filter { it.properties[KIND_KEY] == KIND_MOBILE_DEVICE }
            .mapNotNull { file ->
                val devId = file.properties[DEVICE_ID_KEY]?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val manifest = mobileManifests[devId] ?: return@mapNotNull null
                val monthFiles = mobileMonthFilesByDevice[devId].orEmpty()
                val monthsCount = manifest.months.keys.count { it in monthFiles }
                val totalBytes = file.sizeBytes + monthFiles.values.sumOf { it.sizeBytes }
                val updatedAt = manifest.updatedAt.takeIf { it > 0L } ?: parseIsoInstantMs(file.modifiedAt) ?: 0L
                val cleanName = manifest.name.ifBlank { "Mobile Device" }
                val synthetic = RemoteBackup(
                    fileId = file.id,
                    name = file.name,
                    backupId = devId,
                    deviceId = devId,
                    deviceName = cleanName,
                    updatedAtMs = updatedAt,
                    sizeBytes = totalBytes,
                    versionName = manifest.versionName,
                    monthsCount = monthsCount,
                    md5 = file.md5,
                    deviceKind = RemoteDeviceKind.MOBILE,
                )
                RemoteDeviceGroup(
                    deviceId = devId,
                    deviceName = cleanName,
                    kind = RemoteDeviceKind.MOBILE,
                    latestUpdatedAtMs = updatedAt,
                    totalSizeBytes = totalBytes,
                    versionName = manifest.versionName,
                    snapshots = listOf(synthetic),
                    mobileMonthFiles = monthFiles,
                    mobileMonthsCount = monthsCount,
                )
            }

        return (desktopGroups + mobileGroups).sortedWith(
            compareByDescending<RemoteDeviceGroup> {
                it.kind == RemoteDeviceKind.DESKTOP && it.deviceId.isNotBlank() && it.deviceId == currentDeviceId
            }.thenByDescending { it.latestUpdatedAtMs },
        )
    }

    internal fun parseMobileStoredBucket(jsonBytes: ByteArray): DesktopListeningStats.BackupBucket? {
        val raw = runCatching { mobileJson.decodeFromString(MobileStoredBucket.serializer(), jsonBytes.decodeToString()) }
            .getOrNull() ?: return null
        val month = raw.month.takeIf(String::isNotBlank) ?: return null
        val tracks = raw.tracks.mapNotNull { t ->
            if (t.id.isBlank()) return@mapNotNull null
            val song = Song(
                videoId = t.id,
                title = t.title,
                artist = t.artist,
                thumbnailUrl = t.art,
                albumName = t.album,
                albumId = t.albumId,
            )
            DesktopListeningStats.BackupTrack(
                songEncoded = DesktopPersistence.encodeSongFields(song),
                listenedMs = t.ms.coerceAtLeast(0L),
                plays = t.plays.coerceAtLeast(0),
            )
        }
        val days = buildMap {
            if (MOBILE_MONTH_REGEX.matches(month)) {
                raw.days.forEach { (dayInt, ms) ->
                    if (dayInt in 1..31 && ms > 0L) {
                        put("$month-${dayInt.toString().padStart(2, '0')}", ms)
                    }
                }
            }
        }
        return DesktopListeningStats.BackupBucket(
            month = month,
            tracks = tracks,
            hours = raw.hours.take(24),
            days = days,
        )
    }

    internal fun applyMobileSettingsToDesktop(
        settings: Map<String, MobilePrefValue>,
        persistence: DesktopPersistence = DesktopPersistence(),
    ): Int {
        val booleans = LinkedHashMap<String, Boolean>()
        val strings = LinkedHashMap<String, String>()
        val ints = LinkedHashMap<String, Int>()
        settings.forEach { (key, pref) ->
            when (pref.type) {
                "bool" -> pref.value?.toBooleanStrictOrNull()?.let { booleans[key] = it }
                "string" -> pref.value?.let { strings[key] = it }
                "int" -> pref.value?.toIntOrNull()?.let { ints[key] = it }
            }
        }
        return DesktopBackup.applyMobilePreferences(booleans, strings, ints, persistence)
    }

    private fun decodeMobileManifest(bytes: ByteArray): MobileDeviceManifest =
        mobileJson.decodeFromString(MobileDeviceManifest.serializer(), bytes.decodeToString())

    private suspend fun loadDeviceGroupsFromFiles(
        token: String,
        files: List<DesktopDriveApi.DriveFile>,
        currentDeviceId: String,
        currentDeviceName: String?,
    ): List<RemoteDeviceGroup> {
        val mobileFiles = files.filter { it.properties[KIND_KEY] == KIND_MOBILE_DEVICE }
        val manifests = LinkedHashMap<String, MobileDeviceManifest>()
        mobileFiles.forEach { file ->
            val devId = file.properties[DEVICE_ID_KEY]?.takeIf(String::isNotBlank) ?: return@forEach
            val cached = mobileManifestCache[file.id]
            val parsed = if (cached != null && cached.first == file.md5 && file.md5 != null) {
                cached.second
            } else {
                runCatching {
                    val decoded = decodeMobileManifest(maybeGunzip(DesktopDriveApi.download(token, file.id, file.sizeBytes)))
                    mobileManifestCache[file.id] = file.md5 to decoded
                    decoded
                }.getOrNull()
            }
            if (parsed != null) manifests[devId] = parsed
        }
        return buildRemoteDeviceGroups(files, manifests, currentDeviceId, currentDeviceName)
    }

    private suspend fun downloadMobileBuckets(
        token: String,
        monthFiles: Map<String, DesktopDriveApi.DriveFile>,
    ): List<DesktopListeningStats.BackupBucket> = monthFiles.values.mapNotNull { file ->
        runCatching {
            val raw = DesktopDriveApi.download(token, file.id, file.sizeBytes)
            parseMobileStoredBucket(maybeGunzip(raw))
        }.getOrNull()
    }

    private fun flattenDeviceSnapshots(groups: List<RemoteDeviceGroup>): List<RemoteBackup> =
        groups.flatMap { it.snapshots }.sortedByDescending { it.updatedAtMs }

    private fun isDesktopBackupFile(file: DesktopDriveApi.DriveFile): Boolean =
        file.properties[KIND_KEY] == KIND_DESKTOP_BACKUP ||
            file.name.startsWith("bitchord-desktop-backup-") ||
            file.name == DesktopDriveApi.LEGACY_FILE_NAME

    private fun resolvedDesktopDeviceId(file: DesktopDriveApi.DriveFile): String =
        file.properties[DEVICE_ID_KEY]?.takeIf(String::isNotBlank)
            ?: extractLegacyDeviceId(file.name)
            ?: LEGACY_DEVICE_ID

    private fun toRemoteBackup(file: DesktopDriveApi.DriveFile): RemoteBackup {
        val deviceId = resolvedDesktopDeviceId(file)
        val deviceName = file.properties[DEVICE_NAME_KEY]?.takeIf(String::isNotBlank)
            ?: if (deviceId != LEGACY_DEVICE_ID) deviceId.take(8) else "Desktop Backup"
        val updatedAtMs = file.properties[UPDATED_AT_KEY]?.toLongOrNull()
            ?: parseIsoInstantMs(file.modifiedAt)
            ?: 0L
        val summaryMatch = file.properties[SUMMARY_KEY]?.let { SUMMARY_REGEX.matchEntire(it) }
        return RemoteBackup(
            fileId = file.id,
            name = file.name,
            backupId = file.properties[BACKUP_ID_KEY].orEmpty(),
            deviceId = deviceId,
            deviceName = deviceName,
            updatedAtMs = updatedAtMs,
            sizeBytes = file.sizeBytes,
            versionName = file.properties[APP_VERSION_KEY].orEmpty(),
            isAutoBackup = file.properties[TRIGGER_KEY] == "auto",
            playlistsCount = summaryMatch?.groupValues?.get(1)?.toIntOrNull(),
            likedCount = summaryMatch?.groupValues?.get(2)?.toIntOrNull(),
            monthsCount = summaryMatch?.groupValues?.get(3)?.toIntOrNull(),
            md5 = file.md5,
            deviceKind = RemoteDeviceKind.DESKTOP,
        )
    }

    private fun extractLegacyDeviceId(fileName: String): String? {
        if (!fileName.startsWith("bitchord-desktop-backup-")) return null
        val core = fileName.removePrefix("bitchord-desktop-backup-").removeSuffix(".json.gz")
        return if (core.length == 36) core else core.take(36).takeIf { it.count { ch -> ch == '-' } == 4 }
    }

    private fun parseIsoInstantMs(raw: String): Long? =
        if (raw.isBlank()) null else runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()

    private fun maybeGunzip(raw: ByteArray): ByteArray =
        if (raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) DesktopBackup.gunzip(raw) else raw

    private fun peerSyncStamp(peer: RemoteDeviceGroup): String = when (peer.kind) {
        RemoteDeviceKind.DESKTOP -> {
            val latest = peer.latestSnapshot
            "${latest?.fileId.orEmpty()}|${latest?.md5.orEmpty()}|${latest?.updatedAtMs ?: 0L}"
        }
        RemoteDeviceKind.MOBILE -> {
            peer.mobileMonthFiles.entries.sortedBy { it.key }
                .joinToString(";") { "${it.key}:${it.value.md5.orEmpty()}" }
        }
    }

    private fun peerStampKey(deviceId: String): String = "$KEY_PEER_STAMP_PREFIX$deviceId"

    private fun retainOnlyPeerStamps(activeIds: Set<String>) {
        runCatching {
            prefs.keys()
                .filter { it.startsWith(KEY_PEER_STAMP_PREFIX) }
                .forEach { key ->
                    val id = key.removePrefix(KEY_PEER_STAMP_PREFIX)
                    if (id !in activeIds) prefs.remove(key)
                }
        }
    }

    private fun clearAllPeerStamps() {
        runCatching {
            prefs.keys().filter { it.startsWith(KEY_PEER_STAMP_PREFIX) }.forEach(prefs::remove)
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

    private fun initializeNewDeviceLocked(name: String): String {
        val created = UUID.randomUUID().toString()
        val cleanName = name.trim().take(60).ifBlank { defaultDeviceName() }
        prefs.put(KEY_DEVICE_ID, created)
        prefs.put(KEY_DEVICE_NAME, cleanName)
        prefs.putBoolean(KEY_DEVICE_READY, true)
        return created
    }

    private fun ensureDeviceIdentityLocked(): String {
        val existing = prefs.get(KEY_DEVICE_ID, null)?.takeIf(String::isNotBlank)
        if (existing != null) {
            prefs.putBoolean(KEY_DEVICE_READY, true)
            return existing
        }
        return initializeNewDeviceLocked(defaultDeviceName())
    }

    private fun loadSettings(): Settings {
        val freqName = prefs.get(KEY_FREQUENCY, DesktopBackupFrequency.DAILY.name)
        val freq = DesktopBackupFrequency.entries.firstOrNull { it.name == freqName } ?: DesktopBackupFrequency.DAILY
        val retName = prefs.get(KEY_RETENTION, DesktopBackupRetention.TEN.name)
        val retention = DesktopBackupRetention.entries.firstOrNull { it.name == retName } ?: DesktopBackupRetention.TEN
        val account = prefs.get(KEY_ACCOUNT_EMAIL, null)?.takeIf(String::isNotBlank)
        val deviceId = prefs.get(KEY_DEVICE_ID, null).orEmpty()
        val deviceReady = prefs.getBoolean(KEY_DEVICE_READY, deviceId.isNotBlank())
        return Settings(
            accountEmail = account,
            accountName = prefs.get(KEY_ACCOUNT_NAME, null)?.takeIf(String::isNotBlank),
            frequency = freq,
            retention = retention,
            lastBackupAt = prefs.getLong(KEY_LAST_BACKUP_AT, 0L),
            lastError = prefs.get(KEY_LAST_ERROR, null)?.takeIf(String::isNotBlank),
            needsSignIn = prefs.getBoolean(KEY_NEEDS_SIGN_IN, false),
            needsDeviceSetup = account != null && (!deviceReady || deviceId.isBlank()),
            deviceId = deviceId,
            deviceName = prefs.get(KEY_DEVICE_NAME, null)?.takeIf(String::isNotBlank) ?: defaultDeviceName(),
            hasRefreshToken = DesktopDriveAuth.hasRefreshToken(),
        )
    }

    private const val LEGACY_DEVICE_ID = "legacy"
}
