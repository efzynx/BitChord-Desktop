package com.music.bitchord.desktop

import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopCloudflareTunnelTest {

    @Test
    fun `isPortAvailable accurately identifies available and bound ports`() {
        val testPort = ServerSocket(0).use { it.localPort }
        assertTrue(DesktopCloudflareTunnel.isPortAvailable(testPort), "ephemeral port should be available after close")

        // Bind the port and verify isPortAvailable returns false
        ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", testPort))
            assertFalse(DesktopCloudflareTunnel.isPortAvailable(testPort), "port should be unavailable while bound")
        }

        // Port should be available again after socket closes
        assertTrue(DesktopCloudflareTunnel.isPortAvailable(testPort), "port should be free after closing socket")
    }

    @Test
    fun `resolveServerPort automatically falls back to an available port when preferred port is occupied`() {
        val preferredPort = ServerSocket(0).use { it.localPort }

        // When free, preferred port is chosen without switching
        val (chosenWhenFree, wasSwitchedFree) = DesktopCloudflareTunnel.resolveServerPort(preferredPort)
        assertEquals(preferredPort, chosenWhenFree)
        assertFalse(wasSwitchedFree)

        // Simulate busy port: bind preferredPort
        ServerSocket().use { busySocket ->
            busySocket.reuseAddress = true
            busySocket.bind(InetSocketAddress("127.0.0.1", preferredPort))

            val (chosenWhenBusy, wasSwitchedBusy) = DesktopCloudflareTunnel.resolveServerPort(preferredPort)
            assertNotEquals(preferredPort, chosenWhenBusy, "should switch to another port when preferred is occupied")
            assertTrue(wasSwitchedBusy, "wasSwitched should be true when port is occupied")
            assertTrue(DesktopCloudflareTunnel.isPortAvailable(chosenWhenBusy), "chosen replacement port must be available")
        }
    }

    @Test
    fun `findPartyServerBinary locates local binary when present`() {
        val exeName = if (DesktopPlatform.isWindows) "party-server.exe" else "party-server"
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val target = File(userDir, exeName)

        val hadFile = target.exists()
        if (!hadFile) {
            target.writeText("mock-binary")
            target.setExecutable(true)
        }
        try {
            val found = DesktopCloudflareTunnel.findPartyServerBinary()
            assertNotNull(found, "party-server binary should be found in candidate dir")
            assertTrue(found.isFile, "found binary must be a file")
        } finally {
            if (!hadFile && target.exists()) {
                target.delete()
            }
        }
    }
}
