package citu.edu.stathis.mobile.features.common.refresh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Refetches when the screen returns to RESUMED (foreground or back-navigation).
 * Optional [pollIntervalMs] runs only while RESUMED so backgrounded screens stop polling.
 */
@Composable
fun VisibleScreenRefresh(
    refreshKey: Any?,
    pollIntervalMs: Long? = null,
    onResume: () -> Unit,
    onPoll: (() -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner, refreshKey) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    onResume()
                }
                Lifecycle.Event.ON_PAUSE -> resumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val interval = pollIntervalMs
    val poll = onPoll ?: onResume
    LaunchedEffect(resumed, refreshKey, interval) {
        if (!StudentDataFreshness.shouldPollWhileVisible(resumed, interval) || interval == null) {
            return@LaunchedEffect
        }
        while (true) {
            delay(interval)
            poll()
        }
    }
}

