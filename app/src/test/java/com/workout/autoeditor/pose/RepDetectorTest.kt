package com.workout.autoeditor.pose

import com.workout.autoeditor.data.PoseFrame
import com.workout.autoeditor.data.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepDetectorTest {

    private fun frame(t: Long, kneeAngleDeg: Float): PoseFrame {
        val lm = MutableList(33) {
            PoseLandmark(0f, 0f, 0f, 1f, 1f)
        }
        lm[23] = PoseLandmark(0f, 0f, 0f, 1f, 1f)
        lm[27] = PoseLandmark(0f, 1f, 0f, 1f, 1f)
        val rad = Math.toRadians(kneeAngleDeg.toDouble())
        lm[25] = PoseLandmark(
            x = Math.sin(rad).toFloat() * 0.5f,
            y = Math.cos(rad).toFloat() * 0.5f,
            z = 0f, visibility = 1f, presence = 1f,
        )
        return PoseFrame(t, lm)
    }

    @Test
    fun counts_three_squats_when_alternating_angles() {
        val det = RepDetector(RepDetector.SQUAT)
        val pattern = floatArrayOf(170f, 90f, 170f, 90f, 170f, 90f, 170f)
        for ((i, a) in pattern.withIndex()) det.update(frame((i * 100).toLong(), a))
        assertEquals(3, det.reps())
    }

    @Test
    fun does_not_count_partial_reps_above_down_threshold() {
        val det = RepDetector(RepDetector.SQUAT)
        val pattern = floatArrayOf(170f, 130f, 170f, 130f, 170f)
        for ((i, a) in pattern.withIndex()) det.update(frame((i * 100).toLong(), a))
        assertEquals(0, det.reps())
    }

    @Test
    fun reset_clears_state() {
        val det = RepDetector(RepDetector.SQUAT)
        for ((i, a) in floatArrayOf(170f, 90f, 170f).withIndex()) det.update(frame((i * 100).toLong(), a))
        assertTrue(det.reps() == 1)
        det.reset()
        assertEquals(0, det.reps())
    }
}
