package citu.edu.stathis.mobile.features.tasks.presentation

import android.content.Context
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import citu.edu.stathis.mobile.features.tasks.data.api.TaskService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
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
    private var cacheFile: File? = null

    private val _phase = MutableStateFlow<DemonstrationPhase>(DemonstrationPhase.Checking)
    val phase: StateFlow<DemonstrationPhase> = _phase.asStateFlow()

    fun open(taskId: String, exerciseTemplateId: String) {
        if (flow.taskId == taskId &&
            flow.exerciseTemplateId == exerciseTemplateId &&
            _phase.value !is DemonstrationPhase.Checking
        ) {
            return
        }
        flow = DemonstrationFlow(taskId, exerciseTemplateId)
        publish()
        viewModelScope.launch { loadMetadata() }
    }

    fun retry() {
        viewModelScope.launch {
            val failedMetadata = flow.phase is DemonstrationPhase.MetadataFailed
            flow.retry()
            publish()
            if (failedMetadata) {
                loadMetadata()
            } else {
                download()
            }
        }
    }

    fun continueToIdentity() {
        flow.continueToIdentity()
        deleteCache()
        publish()
    }

    fun back() {
        flow.back()
        deleteCache()
        publish()
    }

    fun onPlaybackFailed() {
        flow.onDownloadFailed("Could not play the demonstration")
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
                flow.onMetadata(true)
                publish()
                download()
                return
            } else {
                flow.onMetadata(false)
            }
        } catch (_: Exception) {
            flow.onMetadataFailed("Could not check for a demonstration")
        }
        publish()
    }

    private suspend fun download() {
        try {
            val response = taskService.getExerciseDemonstrationContent(flow.taskId, flow.exerciseTemplateId)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                body?.close()
                flow.onDownloadFailed("Could not download the demonstration")
                publish()
                return
            }
            val file = cacheFileFor(body.contentType()?.toString())
            cacheFile = file
            body.byteStream().use { input -> DemonstrationCache.write(input, file) }
            flow.onDownloadReady(file.absolutePath)
        } catch (_: Exception) {
            deleteCache()
            flow.onDownloadFailed("Could not download the demonstration")
        }
        publish()
    }

    private fun cacheFileFor(contentType: String?): File {
        deleteCache()
        val extension = if (contentType?.contains("webm") == true) "webm" else "mp4"
        val safe = "${flow.taskId}_${flow.exerciseTemplateId}".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(context.cacheDir, "demonstrations")
        return File(dir, "$safe.$extension")
    }

    private fun deleteCache() {
        DemonstrationCache.delete(cacheFile)
        cacheFile = null
    }

    private fun publish() {
        _phase.value = flow.phase
    }

    override fun onCleared() {
        deleteCache()
        super.onCleared()
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
        }
        phase.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        phase.filePath?.let { path ->
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                factory = { context ->
                    VideoView(context).apply {
                        setVideoPath(path)
                        setOnPreparedListener { it.start() }
                        setOnErrorListener { _, _, _ ->
                            onPlaybackFailed()
                            true
                        }
                    }
                },
                update = { view ->
                    if (view.tag != path) {
                        view.tag = path
                        view.setVideoPath(path)
                        view.start()
                    }
                }
            )
            DisposableEffect(path) {
                onDispose { DemonstrationCache.delete(File(path)) }
            }
        }
        if (phase.error != null || !phase.ready) {
            Button(onClick = onRetry) { Text("Retry") }
        }
        Button(onClick = onContinue) { Text("Continue") }
        Button(onClick = onBack) { Text("Back") }
    }
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
