package citu.edu.stathis.mobile.features.tasks.presentation

import android.content.Context
import android.util.Log
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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

    fun rememberPosition(positionMs: Int) {
        if (positionMs >= 0) {
            resumePositionMs = positionMs
        }
    }

    fun onPlaybackFailed() {
        if (BuildConfig.DEBUG) {
            Log.d(PLAYER_LOG, "player error cache=${cacheFile?.name}")
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
                flow.onMetadataFailed(DemonstrationPlayback.UNABLE_TO_PLAY)
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
            flow.onMetadataFailed(DemonstrationPlayback.UNABLE_TO_PLAY)
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
                flow.onDownloadFailed(DemonstrationPlayback.UNABLE_TO_PLAY)
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
            flow.onDownloadFailed(DemonstrationPlayback.UNABLE_TO_PLAY)
        }
        publish()
    }

    private fun debugDownload(status: Int, contentType: String?, expected: Long?, bytes: Long, name: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            PLAYER_LOG,
            "status=$status type=$contentType expected=$expected bytes=$bytes cache=$name"
        )
    }

    private fun publish() {
        _phase.value = flow.phase
    }
}

@Composable
fun ExerciseWithDemonstration(
    taskId: String,
    exerciseTemplateId: String,
    onShowingDemonstration: (Boolean) -> Unit = {},
    viewModel: ExerciseDemonstrationViewModel = hiltViewModel(),
    exercise: @Composable () -> Unit
) {
    val phase by viewModel.phase.collectAsState()
    val showingDemonstration = phase is DemonstrationPhase.Demo || phase is DemonstrationPhase.MetadataFailed
    LaunchedEffect(taskId, exerciseTemplateId) {
        viewModel.open(taskId, exerciseTemplateId)
    }
    LaunchedEffect(showingDemonstration) {
        onShowingDemonstration(showingDemonstration)
    }
    DisposableEffect(Unit) {
        onDispose { onShowingDemonstration(false) }
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
                    DemonstrationPlayback.LOADING,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        is DemonstrationPhase.MetadataFailed -> {
            DemonstrationMessage(
                message = current.message,
                onRetry = { viewModel.retry() }
            )
        }
        is DemonstrationPhase.Demo -> {
            DemonstrationPlayer(
                phase = current,
                resumePositionMs = viewModel.resumePosition,
                onPosition = viewModel::rememberPosition,
                onRetry = { viewModel.retry() },
                onPlaybackFailed = { viewModel.onPlaybackFailed() },
                onContinue = { viewModel.continueToIdentity() }
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
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!phase.ready && phase.error == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(DemonstrationPlayback.LOADING)
            }
        }
        phase.error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Retry")
            }
        }
        phase.filePath?.let { path ->
            LocalDemonstrationPlayer(
                path = path,
                resumePositionMs = resumePositionMs,
                onPosition = onPosition,
                onPlaybackFailed = onPlaybackFailed
            )
        }
        Text(
            DemonstrationPlayback.INSTRUCTION,
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Continue")
        }
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
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
                    val host = this
                    host.post {
                        val scale = DemonstrationPlayback.fitScale(
                            player.videoWidth,
                            player.videoHeight,
                            host.width,
                            host.height
                        )
                        if (scale != null) {
                            host.scaleX = scale.first
                            host.scaleY = scale.second
                        }
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
    }

    DisposableEffect(path) {
        onDispose {
            val view = videoView
            view?.setOnErrorListener(null)
            view?.setOnPreparedListener(null)
            view?.setOnCompletionListener(null)
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

    if (!prepared) {
        Text(
            DemonstrationPlayback.PREPARING,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
    Text(
        "${DemonstrationPlayback.formatClock(positionMs)} / ${DemonstrationPlayback.formatClock(durationMs)}",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
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
            .heightIn(min = 48.dp)
            .semantics { contentDescription = "Seek demonstration" }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = {
                val view = videoView ?: return@FilledIconButton
                if (!prepared) return@FilledIconButton
                if (finished || !playing) {
                    if (finished) {
                        view.seekTo(0)
                        positionMs = 0
                        onPosition(0)
                        finished = false
                    }
                    view.start()
                    playing = true
                } else {
                    view.pause()
                    playing = false
                    val current = view.currentPosition.coerceAtLeast(0)
                    positionMs = current
                    onPosition(current)
                }
            },
            enabled = prepared,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = if (playing && !finished) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing && !finished) "Pause" else "Play"
            )
        }
        Spacer(Modifier.size(20.dp))
        FilledTonalButton(
            onClick = {
                val view = videoView ?: return@FilledTonalButton
                if (!prepared) return@FilledTonalButton
                view.seekTo(0)
                positionMs = 0
                onPosition(0)
                finished = false
                view.start()
                playing = true
            },
            enabled = prepared,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Filled.Replay, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Replay")
        }
    }
}

@Composable
private fun DemonstrationMessage(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text("Retry")
        }
    }
}

private const val PLAYER_LOG = "DemoPlayback"
