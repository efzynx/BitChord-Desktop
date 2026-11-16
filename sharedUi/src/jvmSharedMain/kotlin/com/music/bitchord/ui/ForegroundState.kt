package com.music.bitchord.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val isAndroidPlatform: Boolean by lazy {
    runCatching { Class.forName("android.os.Build") }.isSuccess
}

/**
 * Whether the app is actually on screen.
 *
 * On Android, this follows the Activity's RESUMED lifecycle state so background
 * playback does not spin unnecessary UI frame loops.
 * On Desktop JVM, there is no Android Activity lifecycle; the window is active
 * and animations/lyrics should always advance when playing.
 */
@Composable
fun rememberIsForeground(): Boolean {
    if (!isAndroidPlatform) {
        return true
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { owner, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return foreground
}
