package com.workout.autoeditor.pipeline

import android.content.Context
import android.net.Uri
import com.workout.autoeditor.audio.AudioAnalyzer
import com.workout.autoeditor.data.CutList
import com.workout.autoeditor.data.EditPolicy
import com.workout.autoeditor.edit.CutListBuilder
import com.workout.autoeditor.edit.VideoEditor
import com.workout.autoeditor.llm.GemmaService
import com.workout.autoeditor.llm.InstructionParser
import com.workout.autoeditor.llm.KeyframeAnnotator
import com.workout.autoeditor.ml.KnnClassifier
import com.workout.autoeditor.ml.TrainingDataStore
import com.workout.autoeditor.pose.ExerciseClassifier
import com.workout.autoeditor.pose.PoseAnalyzer
import com.workout.autoeditor.pose.TimelineBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Orchestrates the full pipeline. Resources are opened and closed in sequence
 * so we never hold Gemma + Pose Landmarker in memory simultaneously.
 *
 *   Phase A: parseInstruction(...)  -> EditPolicy   (~3 s, cheap, returns immediately)
 *   Phase B: runFromPolicy(...)     -> File          (heavy: pose / audio / Gemma / render)
 *
 * UI calls A, displays the parsed policy for confirmation, then calls B.
 */
class EditPipeline(private val ctx: Context) {

    sealed class Stage {
        data object Idle : Stage()
        data object ParsingInstruction : Stage()
        data object Pose : Stage()
        data object Audio : Stage()
        data object Timeline : Stage()
        data object Keyframes : Stage()
        data object Cutting : Stage()
        data object Rendering : Stage()
        data class Done(val outputFile: File) : Stage()
        data class Failed(val reason: String) : Stage()
    }

    suspend fun parseInstruction(instruction: String, modelPath: String): EditPolicy {
        val gemma = GemmaService(ctx, modelPath)
        return try {
            InstructionParser(gemma).parse(instruction)
        } finally {
            gemma.close()
        }
    }

    fun runFromPolicy(
        sourceUri: Uri,
        policy: EditPolicy,
        modelPath: String,
        outputFile: File,
    ): Flow<Stage> = flow {
        try {
            emit(Stage.Pose)
            val store = TrainingDataStore(ctx)
            val knn = if (store.count() >= 10) KnnClassifier(store.load()) else null
            val classifier = ExerciseClassifier(knn)

            val poseAnalyzer = PoseAnalyzer(ctx)
            val frames = try {
                poseAnalyzer.analyze(sourceUri, sampleFps = 5f)
            } finally {
                poseAnalyzer.close()
            }

            emit(Stage.Audio)
            val envelope = AudioAnalyzer(ctx).analyze(sourceUri)

            emit(Stage.Timeline)
            val segments = TimelineBuilder(classifier).build(frames, envelope)

            emit(Stage.Keyframes)
            val gemma = GemmaService(ctx, modelPath)
            val annotated = try {
                KeyframeAnnotator(gemma).annotate(segments)
            } finally {
                gemma.close()
            }

            emit(Stage.Cutting)
            val cutList: CutList = CutListBuilder(policy).build(sourceUri.toString(), annotated)
            if (cutList.items.isEmpty()) {
                emit(Stage.Failed("no segments survived the cut policy"))
                return@flow
            }

            emit(Stage.Rendering)
            val out = VideoEditor(ctx).render(cutList, outputFile)
            emit(Stage.Done(out))
        } catch (t: Throwable) {
            emit(Stage.Failed(t.message ?: t::class.simpleName ?: "unknown"))
        }
    }
}
