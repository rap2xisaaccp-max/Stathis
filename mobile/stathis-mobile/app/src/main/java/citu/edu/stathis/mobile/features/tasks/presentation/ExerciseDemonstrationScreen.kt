package citu.edu.stathis.mobile.features.tasks.presentation

import android.content.Context
import android.util.Log
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cit.edu.stathis.mobile.BuildConfig
import citu.edu.stathis.mobile.features.tasks.data.api.TaskService
import citu.edu.stathis.mobile.features.tasks.data.model.ExerciseDemonstration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ExerciseDemonstrationViewModel @Inject constructor(
    private val taskService: TaskService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var flow = DemonstrationFlow("", "")
    private var metadata: ExerciseDemonstration? = null
    private var cacheFile: File? = null
    private var loadJob: Job? = null
    private var resumePositionMs: Int = 0

    private val _phase = MutableStateFlow<DemonstrationPhase>(DemonstrationPhase.Checking)
    val phase: StateFlow<DemonstrationPhase> = _phase.asStateFlow()
    val resumePosition: Int get() = resumePositionMs

    fun open(taskId: String, exerciseTemplateId: String) {
        if (flow.taskId == taskId &&
            flow.exerciseTemplateId == exerciseTemplateId &&
            _phase.value !is DemonstrationPhase.Checking
        ) {
            return
        }
        loadJob?.cancel()
        flow = DemonstrationFlow(taskId, exerciseTemplateId)
        metadata = null
        cacheFile = null
        resumePositionMs = 0
        publish()
        loadJob = viewModelScope.launch { loadMetadata() }
    }

    fun retry() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val failedMetadata = flow.phase is DemonstrationPhase.MetadataFailed
            resumePositionMs = 0
            val expected = metadata?.byteSize?.takeIf { it > 0L }
            val current = cacheFile
            if (current != null && !DemonstrationCache.isComplete(current, expected)) {
                DemonstrationCache.delete(current)
                cacheFile = null
            }
            flow.retry()
            publish()
            if (failedMetadata || metadata == null) {
                loadMetadata()
            } else {
                download(metadata!!)
            }
        }
    }

    fun continueToIdentity() {
        flow.continueToIdentity()
        publish()
    }

    fun back() {
        flow.back()
        publish()
    }

    fun rememberPosition(positionMs: Int) {
        if (positionMs >= 0) {
            resumePositionMs = positionMs
        }
    }

    fun onPlaybackFailed() {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "player error cache=${cacheFile?.name}")
        }
        flow.onDownloadFailed(DemonstrationPlayback.UNABLE_TO_PLAY)
        publish()
    }

    private suspend fun loadMetadata() {
        if (!demonstrationApplies("TASK")) {
            flow.onMetadata(false)
            publish()
            return
        }
        try {
            val response = taskService.getExerciseDemonstration(flow.taskId, flow.exerciseTemplateId)
            if (!response.isSuccessful || response.body() == null) {
                flow.onMetadataFailed("Could not check for a demonstration")
            } else if (response.body()!!.available) {
                metadata = response.body()
                flow.onMetadata(true)
                publish()
                download(response.body()!!)
                return
            } else {
                flow.onMetadata(false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            flow.onMetadataFailed("Could not check for a demonstration")
        }
        publish()
    }

    private suspend fun download(demo: ExerciseDemonstration) {
        val directory = File(context.cacheDir, "demonstrations")
        val extension = if (demo.contentType?.contains("webm") == true) "webm" else "mp4"
        val version = DemonstrationCache.versionToken(demo.physicalId, demo.createdAt, demo.byteSize)
        val file = DemonstrationCache.fileFor(directory, flow.taskId, flow.exerciseTemplateId, version, extension)
        val expected = demo.byteSize?.takeIf { it > 0L }
        try {
            if (DemonstrationCache.isComplete(file, expected)) {
                cacheFile = file
                DemonstrationCache.deleteOtherVersions(directory, flow.taskId, flow.exerciseTemplateId, file)
                flow.onDownloadReady(file.absolutePath)
                publish()
                return
            }
            val response = taskService.getExerciseDemonstrationContent(flow.taskId, flow.exerciseTemplateId)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                body?.close()
                debugDownload(response.code(), demo.contentType, expected, 0L, file.name)
                flow.onDownloadFailed("Could not download the demonstration")
                publish()
                return
            }
            val written = body.use { payload ->
                DemonstrationCache.writeAtomically(payload.byteStream(), file, expected)
            }
            cacheFile = file
            DemonstrationCache.deleteOtherVersions(directory, flow.taskId, flow.exerciseTemplateId, file)
            debugDownload(response.code(), demo.contentType, expected, written, file.name)
            flow.onDownloadReady(file.absolutePath)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DemonstrationCache.delete(file)
            cacheFile = null
            debugDownload(0, demo.contentType, expected, 0L, file.name)
            flow.onDownloadFailed("Could not download the demonstration")
        }
        publish()
    }

    private fun debugDownload(status: Int, contentType: String?, expected: Long?, bytes: Long, name: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            LOG_TAG,
            "status=$status type=$contentType expected=$expected bytes=$bytes cache=$name"
        )
    }

    private fun publish() {
        _phase.value = flow.phase
    }

    private companion object {
        const val LOG_TAG = "DemoPlayback"
    }
}

@Composable
fun ExerciseWithDemonstration(
    taskId: String,
    exerciseTemplateId: String,
    onBack: () -> Unit,
    viewModel: ExerciseDemonstrationViewModel = hiltViewModel(),
    exercise: @Composable () -> Unit
) {
    val phase by viewModel.phase.collectAsState()
    LaunchedEffect(taskId, exerciseTemplateId) {
        viewModel.open(taskId, exerciseTemplateId)
    }
    when (val current = phase) {
        is DemonstrationPhase.Checking -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text(
                    "Loading demonstration...",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        is DemonstrationPhase.MetadataFailed -> {
            DemonstrationMessage(
                message = current.message,
                continueEnabled = false,
                onRetry = { viewModel.retry() },
                onContinue = {},
                onBack = {
                    viewModel.back()
                    onBack()
                }
            )
        }
        is DemonstrationPhase.Demo -> {
            DemonstrationPlayer(
                phase = current,
                resumePositionMs = viewModel.resumePosition,
                onPosition = viewModel::rememberPosition,
                onRetry = { viewModel.retry() },
                onPlaybackFailed = { viewModel.onPlaybackFailed() },
                onContinue = { viewModel.continueToIdentity() },
                onBack = {
                    viewModel.back()
                    onBack()
                }
            )
        }
        is DemonstrationPhase.Identity -> exercise()
        is DemonstrationPhase.TaskDetails -> Unit
    }
}

@Composable
private fun DemonstrationPlayer(
    phase: DemonstrationPhase.Demo,
    resumePositionMs: Int,
    onPosition: (Int) -> Unit,
    onRetry: () -> Unit,
    onPlaybackFailed: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Demonstration", style = MaterialTheme.typography.headlineSmall)
        Text("You can continue when you are ready. You do not have to watch the whole video.")
        if (!phase.ready && phase.error == null) {
            CircularProgressIndicator()
            Text("Loading demonstration...")
        }
        phase.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        phase.filePath?.let { path ->
            LocalDemonstrationPlayer(
                path = path,
                resumePositionMs = resumePositionMs,
                onPosition = onPosition,
                onPlaybackFailed = onPlaybackFailed
            )
        }
        if (phase.error != null) {
            Button(onClick = onRetry) { Text("Retry") }
        }
        Button(onClick = onContinue) { Text("Continue") }
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun LocalDemonstrationPlayer(
    path: String,
    resumePositionMs: Int,
    onPosition: (Int) -> Unit,
    onPlaybackFailed: () -> Unit
) {
    var videoView by remember(path) { mutableStateOf<VideoView?>(null) }
    var prepared by remember(path) { mutableStateOf(false) }
    var playing by remember(path) { mutableStateOf(false) }
    var finished by remember(path) { mutableStateOf(false) }
    var seeking by remember(path) { mutableStateOf(false) }
    var durationMs by remember(path) { mutableIntStateOf(0) }
    var positionMs by remember(path) { mutableIntStateOf(resumePositionMs.coerceAtLeast(0)) }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        factory = { context ->
            VideoView(context).apply {
                setOnPreparedListener { player ->
                    val duration = player.duration.coerceAtLeast(0)
                    durationMs = duration
                    prepared = true
                    val startAt = DemonstrationPlayback.clampSeek(resumePositionMs, duration)
                    if (startAt > 0 && DemonstrationPlayback.canSeek(true, duration)) {
                        player.seekTo(startAt)
                        positionMs = startAt
                    }
                    player.start()
                    playing = true
                    finished = false
                }
                setOnCompletionListener {
                    playing = false
                    finished = true
                    val end = durationMs.coerceAtLeast(0)
                    positionMs = end
                    onPosition(end)
                }
                setOnErrorListener { _, what, extra ->
                    if (BuildConfig.DEBUG) {
                        Log.d(PLAYER_LOG, "what=$what extra=$extra cache=${File(path).name}")
                    }
                    onPlaybackFailed()
                    true
                }
            }.also { videoView = it }
        },
        update = { view ->
            if (view.tag != path) {
                view.tag = path
                view.setVideoPath(path)
            }
        }
    )

    DisposableEffect(path) {
        onDispose {
            val view = videoView
            view?.setOnErrorListener(null)
            view?.setOnPreparedListener(null)
            view?.pause()
            view?.stopPlayback()
            onPosition(positionMs)
        }
    }

    LaunchedEffect(playing, prepared, seeking, path) {
        while (playing && prepared && !seeking) {
            val current = videoView?.currentPosition ?: positionMs
            if (current >= 0) {
                positionMs = current
                onPosition(current)
            }
            delay(250)
        }
    }

    val status = when {
        !prepared -> "Preparing video..."
        finished -> "Video finished"
        playing -> "Playing"
        else -> "Paused"
    }
    Text(status)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            enabled = prepared,
            onClick = {
                val view = videoView ?: return@Button
                if (!prepared) return@Button
                if (finished) {
                    view.seekTo(0)
                    positionMs = 0
                    onPosition(0)
                    finished = false
                    view.start()
                    playing = true
                } else if (playing) {
                    view.pause()
                    playing = false
                    val current = view.currentPosition.coerceAtLeast(0)
                    positionMs = current
                    onPosition(current)
                } else {
                    view.start()
                    playing = true
                }
            }
        ) {
            Text(if (finished) "Replay" else if (playing) "Pause" else "Play")
        }
        Text("${DemonstrationPlayback.formatClock(positionMs)} / ${DemonstrationPlayback.formatClock(durationMs)}")
    }
    Text("Drag the bar to review a specific part of the demonstration.")
    Slider(
        value = if (durationMs > 0) {
            positionMs.coerceIn(0, durationMs).toFloat()
        } else {
            0f
        },
        onValueChange = { next ->
            if (!DemonstrationPlayback.canSeek(prepared, durationMs)) return@Slider
            seeking = true
            positionMs = DemonstrationPlayback.clampSeek(next.toInt(), durationMs)
        },
        onValueChangeFinished = {
            val view = videoView
            if (view != null && DemonstrationPlayback.canSeek(prepared, durationMs)) {
                val target = DemonstrationPlayback.clampSeek(positionMs, durationMs)
                view.seekTo(target)
                positionMs = target
                onPosition(target)
                finished = target >= durationMs && durationMs > 0
            }
            seeking = false
        },
        valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
        enabled = DemonstrationPlayback.canSeek(prepared, durationMs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun DemonstrationMessage(
    message: String,
    continueEnabled: Boolean,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("Retry") }
        if (continueEnabled) {
            Button(onClick = onContinue) { Text("Continue") }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}

private const val PLAYER_LOG = "DemoPlayback"
