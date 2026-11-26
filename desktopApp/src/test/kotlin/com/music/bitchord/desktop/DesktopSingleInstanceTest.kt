package com.music.bitchord.desktop

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopSingleInstanceTest {

    @Test
    fun secondLaunchForwardsShowAndMediaActionsWithoutOpeningAnotherSession() {
        val tempDir = Files.createTempDirectory("bitchord-single-instance-test")
        try {
            val first = DesktopSingleInstance.acquire(tempDir, emptyArray())
            val primary = assertIs<DesktopSingleInstance.Companion.Acquisition.Primary>(first).instance

            val showCount = AtomicInteger(0)
            val actionLatch = CountDownLatch(1)
            val receivedAction = AtomicReference<String?>(null)
            primary.bindActions(
                onShow = { showCount.incrementAndGet() },
                onAction = { cmd ->
                    receivedAction.set(cmd)
                    actionLatch.countDown()
                },
            )

            // Launching again with no args raises existing window cleanly.
            val second = DesktopSingleInstance.acquire(tempDir, emptyArray())
            assertIs<DesktopSingleInstance.Companion.Acquisition.Forwarded>(second)
            assertEquals(1, showCount.get(), "Second launch should trigger show() on primary instance")

            // Launching with desktop shortcut media action forwards action without creating a new window.
            val third = DesktopSingleInstance.acquire(tempDir, arrayOf("--play-pause"))
            assertIs<DesktopSingleInstance.Companion.Acquisition.Forwarded>(third)
            assertTrue(actionLatch.await(2, TimeUnit.SECONDS), "Action should be received by primary")
            assertEquals("--play-pause", receivedAction.get())
            assertEquals(1, showCount.get(), "Media action should not force-show the window")

            // Releasing primary frees lock and removes IPC metadata so a later launch succeeds.
            primary.close()
            assertFalse(Files.exists(tempDir.resolve("instance.ipc")), "IPC file must be removed on close")

            val restarted = DesktopSingleInstance.acquire(tempDir, emptyArray())
            val newPrimary = assertIs<DesktopSingleInstance.Companion.Acquisition.Primary>(restarted).instance
            newPrimary.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun rejectsUnauthenticatedIpcCommands() {
        val tempDir = Files.createTempDirectory("bitchord-single-instance-auth-test")
        try {
            val first = DesktopSingleInstance.acquire(tempDir, emptyArray())
            val primary = assertIs<DesktopSingleInstance.Companion.Acquisition.Primary>(first).instance
            val showCount = AtomicInteger(0)
            primary.bindActions(onShow = { showCount.incrementAndGet() }, onAction = {})

            val port = Files.readAllLines(tempDir.resolve("instance.ipc"), StandardCharsets.UTF_8)
                .first()
                .trim()
                .toInt()

            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1500)
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                writer.write("wrong-token\tSHOW\n")
                writer.flush()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                assertEquals("ERR", reader.readLine())
            }
            assertEquals(0, showCount.get(), "Unauthenticated message must not raise window")

            primary.close()
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
