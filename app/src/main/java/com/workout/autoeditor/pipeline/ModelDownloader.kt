package com.workout.autoeditor.pipeline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Resumable HTTP download with progress events.
 *
 * Default URL is the LiteRT-community Gemma 3 1B INT4 task file (~530 MB).
 * Override via setModelUrl() at runtime for custom hosts.
 *
 * Validates by file size after download; safe to abort and resume.
 */
class ModelDownloader(private val ctx: Context) {

    companion object {
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"
        private const val MODELS_DIR = "models"
        private const val MODEL_FILE = "gemma.task"
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun modelFile(): File {
        val dir = File(ctx.getExternalFilesDir(null), MODELS_DIR).apply { mkdirs() }
        return File(dir, MODEL_FILE)
    }

    fun isModelPresent(minBytes: Long = 100_000_000L): Boolean {
        val f = modelFile()
        return f.exists() && f.length() >= minBytes
    }

    sealed class Event {
        data class Progress(val downloadedBytes: Long, val totalBytes: Long) : Event()
        data class Done(val file: File) : Event()
        data class Failed(val reason: String) : Event()
    }

    fun download(url: String = DEFAULT_MODEL_URL): Flow<Event> = flow<Event> {
        val target = modelFile()
        val partial = File(target.parentFile, target.name + ".part")
        val resumeFrom = if (partial.exists()) partial.length() else 0L

        val req = Request.Builder()
            .url(url)
            .apply {
                if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            }
            .build()
        val call = client.newCall(req)

        val response = try {
            call.execute()
        } catch (t: Throwable) {
            emit(Event.Failed("network: ${t.message}"))
            return@flow
        }

        response.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) {
                emit(Event.Failed("http ${resp.code}"))
                return@flow
            }
            val body = resp.body ?: run {
                emit(Event.Failed("no body"))
                return@flow
            }
            val totalAdditional = body.contentLength().takeIf { it >= 0L } ?: -1L
            val totalBytes = if (totalAdditional > 0) resumeFrom + totalAdditional else -1L

            partial.parentFile?.mkdirs()
            val out = if (resumeFrom > 0) {
                java.io.RandomAccessFile(partial, "rw").apply { seek(resumeFrom) }
            } else null
            val sink: java.io.OutputStream = if (out != null) {
                object : java.io.OutputStream() {
                    override fun write(b: Int) { out.write(b) }
                    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len) }
                    override fun close() { out.close() }
                }
            } else java.io.FileOutputStream(partial)

            val src = body.byteStream()
            try {
                val buf = ByteArray(64 * 1024)
                var written = resumeFrom
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    sink.write(buf, 0, n)
                    written += n
                    emit(Event.Progress(written, totalBytes))
                }
                sink.flush()
            } catch (t: Throwable) {
                emit(Event.Failed("io: ${t.message}"))
                return@flow
            } finally {
                try { sink.close() } catch (_: Throwable) {}
                try { src.close() } catch (_: Throwable) {}
            }
        }

        if (target.exists()) target.delete()
        if (!partial.renameTo(target)) {
            emit(Event.Failed("rename"))
            return@flow
        }
        emit(Event.Done(target))
    }.flowOn(Dispatchers.IO)
}
