package com.rassvet.essential.data.recording

import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiarizationEvidenceTest {
    private fun t(start: Float, end: Float, speaker: Int) = OfflineSpeakerDiarizationSegment(start, end, speaker, -2f)

    /** pyannote turns of a real 22 s two-person recording: A = id 1, B split into ids 0 and 2, B interrupts twice. */
    private val interrupted = listOf(
        t(2.63f, 5.58f, 1), t(4.60f, 5.68f, 0), t(6.56f, 9.14f, 1), t(8.60f, 9.80f, 0), t(10.66f, 14.14f, 1),
        t(14.17f, 14.86f, 2), t(15.35f, 16.67f, 1), t(16.67f, 17.43f, 2), t(17.61f, 19.18f, 1), t(19.39f, 20.25f, 2),
    )

    @Test
    fun idsHeardAtTheSameTimeAreDifferentPeople() {
        // 0.98 s + 0.54 s of A and B together; the over-split ids 0 and 2 of B never overlap
        assertEquals(setOf(0 to 1), DiarizationEvidence.simultaneousPairs(interrupted, minMs = 300))
    }

    @Test
    fun shortOrNoOverlapIsNotEvidence() {
        assertTrue(DiarizationEvidence.simultaneousPairs(listOf(t(0f, 2f, 0), t(1.9f, 3f, 1)), minMs = 300).isEmpty())
        assertTrue(DiarizationEvidence.simultaneousPairs(listOf(t(0f, 2f, 0), t(2f, 3f, 1)), minMs = 300).isEmpty())
    }

    @Test
    fun overlapsAddUpPerPair() {
        val turns = listOf(t(0f, 1f, 0), t(0.8f, 2f, 1), t(1.8f, 3f, 0))   // 0.2 s + 0.2 s
        assertEquals(setOf(0 to 1), DiarizationEvidence.simultaneousPairs(turns, minMs = 300))
        assertTrue(DiarizationEvidence.simultaneousPairs(turns, minMs = 500).isEmpty())
    }

    @Test
    fun exclusivePiecesLeaveOutSimultaneousSpeech() {
        val pieces = DiarizationEvidence.exclusivePieces(interrupted)
        // the interrupter's id 0 talks alone only after each overlap: 5.58-5.68 and 9.14-9.80
        val b = pieces.filter { it.speaker == 0 }
        assertEquals(2, b.size)
        assertEquals(5.58f, b[0].start, 1e-4f); assertEquals(5.68f, b[0].end, 1e-4f)
        assertEquals(9.14f, b[1].start, 1e-4f); assertEquals(9.80f, b[1].end, 1e-4f)
        // nothing exclusive inside an overlap
        assertTrue(pieces.none { it.start < 5.5f && it.end > 4.7f })
        // A's first turn is exclusive only until B comes in
        assertEquals(DiarizationEvidence.Piece(2.63f, 4.60f, 1), pieces.first())
    }
}
