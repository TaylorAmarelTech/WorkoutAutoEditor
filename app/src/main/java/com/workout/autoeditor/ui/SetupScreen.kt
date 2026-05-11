package com.workout.autoeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.workout.autoeditor.pipeline.ModelDownloader
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

@Composable
fun SetupScreen(onReady: () -> Unit) {
    val ctx = LocalContext.current
    val downloader = remember { ModelDownloader(ctx) }
    var status by remember { mutableStateOf("checking") }
    var downloaded by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(-1L) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (downloader.isModelPresent()) {
            status = "ready"
            onReady()
        } else {
            status = "downloading"
            try {
                downloader.download()
                    .catch { error = it.message; status = "error" }
                    .collect { ev ->
                        when (ev) {
                            is ModelDownloader.Event.Progress -> {
                                downloaded = ev.downloadedBytes
                                total = ev.totalBytes
                            }
                            is ModelDownloader.Event.Done -> {
                                status = "ready"
                                onReady()
                            }
                            is ModelDownloader.Event.Failed -> {
                                error = ev.reason
                                status = "error"
                            }
                        }
                    }
            } catch (t: Throwable) {
                error = t.message
                status = "error"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Workout Auto Editor", style = MaterialTheme.typography.headlineMedium)
        Text(
            "First launch downloads the on-device Gemma model (~500 MB).",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        when (status) {
            "downloading" -> {
                val pct = if (total > 0) (downloaded.toFloat() / total) else 0f
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.padding(top = 24.dp).fillMaxSize(0.8f),
                )
                Text(
                    "${downloaded / 1_000_000} MB / ${if (total > 0) total / 1_000_000 else "?"} MB",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            "error" -> {
                Text(
                    "Download failed: ${error ?: "unknown"}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(
                    onClick = { status = "downloading" },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Retry") }
            }
            "ready" -> {
                Text("Ready", modifier = Modifier.padding(top = 24.dp))
                Button(
                    onClick = onReady,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Continue") }
            }
            else -> Text("Checking model...", modifier = Modifier.padding(top = 24.dp))
        }
    }
}
