package com.music.bitchord.desktop

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface

private const val RESERVE_INTERFACE = "org.freedesktop.ReserveDevice1"
private const val RESERVE_PREFIX = "org.freedesktop.ReserveDevice1.Audio"
private const val RESERVE_PATH_PREFIX = "/org/freedesktop/ReserveDevice1/Audio"
private const val RESERVE_PRIORITY = 10_000
private const val RELEASE_SETTLE_MS = 120L

@DBusInterfaceName(RESERVE_INTERFACE)
@DBusProperty(name = "Priority", type = Int::class, access = Access.READ)
@DBusProperty(name = "ApplicationName", type = String::class, access = Access.READ)
@DBusProperty(name = "ApplicationDeviceName", type = String::class, access = Access.READ)
internal interface FreeDesktopReserveDevice1 : DBusInterface {
    fun RequestRelease(priority: Int): Boolean
}

/**
 * Coordinates ALSA hardware ownership with PipeWire / WirePlumber / PulseAudio using the
 * FreeDesktop `org.freedesktop.ReserveDevice1` session D-Bus protocol so bit-perfect direct
 * `hw:card,dev` opens succeed without manually killing the user's sound server.
 */
internal object DesktopLinuxAudioReservation {

    private val lock = Any()
    private var connection: DBusConnection? = null
    private var heldBusName: String? = null
    private var heldObjectPath: String? = null

    fun acquire(hwDevice: String): Boolean {
        if (!DesktopPlatform.isLinux) return false
        val card = DesktopLinuxAudio.parseAlsaCard(hwDevice) ?: return false
        val busName = "$RESERVE_PREFIX$card"
        val objectPath = "$RESERVE_PATH_PREFIX$card"

        return synchronized(lock) {
            if (heldObjectPath == objectPath) return@synchronized true
            releaseLocked()
            runCatching {
                val bus = connection ?: DBusConnectionBuilder.forSessionBus().build().also {
                    connection = it
                }
                val granted = runCatching {
                    val holder = bus.getRemoteObject(busName, objectPath, FreeDesktopReserveDevice1::class.java)
                    holder.RequestRelease(RESERVE_PRIORITY)
                }.getOrDefault(true)
                if (!granted) {
                    DesktopTrackLog.log("ALSA D-Bus reservation denied by current holder for $busName")
                    return@runCatching false
                }
                // Give PipeWire/PulseAudio a brief moment to close its ALSA PCM descriptor before we open hw:X,Y.
                Thread.sleep(RELEASE_SETTLE_MS)
                val exported = object : FreeDesktopReserveDevice1 {
                    override fun getObjectPath(): String = objectPath
                    override fun RequestRelease(priority: Int): Boolean = priority > RESERVE_PRIORITY
                }
                bus.exportObject(objectPath, exported)
                heldObjectPath = objectPath
                runCatching {
                    bus.requestBusName(busName)
                    heldBusName = busName
                }.onFailure { error ->
                    DesktopTrackLog.log("ALSA D-Bus bus name request skipped for $busName: ${error.message}")
                }
                true
            }.onFailure { error ->
                DesktopTrackLog.log("ALSA D-Bus reservation unavailable: ${error.message}")
            }.getOrDefault(false)
        }
    }

    fun release() {
        synchronized(lock) {
            releaseLocked()
        }
    }

    private fun releaseLocked() {
        val bus = connection ?: return
        heldBusName?.let { name ->
            runCatching { bus.releaseBusName(name) }
            heldBusName = null
        }
        heldObjectPath?.let { path ->
            runCatching { bus.unExportObject(path) }
            heldObjectPath = null
        }
    }
}
