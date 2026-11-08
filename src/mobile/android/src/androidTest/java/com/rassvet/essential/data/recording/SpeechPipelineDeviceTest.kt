package com.rassvet.essential.data.recording

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real pipeline on wav files pushed to the app files dir (files/eval). Logs timing, RTF and segments. */
@RunWith(AndroidJUnit4::class)
class SpeechPipelineDeviceTest {
    @Test
    fun transcribesPushedFiles() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(ctx.filesDir, "eval").listFiles { f -> f.extension == "wav" }.orEmpty()
        assumeTrue("no eval files", files.isNotEmpty())
        val pipeline = SpeechPipeline(ctx)
        assertTrue(pipeline.modelsAvailable())
        for (f in files.sortedBy { it.name }) {
            val t0 = System.nanoTime()
            for ((label, opt) in listOf("default" to PipelineOptions())) {
                val t1 = System.nanoTime()
                val segs = pipeline.run(f.absolutePath, { _, _ -> }, options = opt)
                val sec = (System.nanoTime() - t1) / 1e9
                val dur = segs.groupBy { it.speaker }.mapValues { (_, v) -> v.sumOf { it.endMs - it.startMs } / 1000.0 }
                Log.i("SPEECH_EVAL", "${f.name} [$label]: speakers=${dur.size} " + dur.entries.sortedByDescending { it.value }.joinToString { "s${it.key}=${"%.1f".format(it.value)}s" })
                Log.i("SPEECH_EVAL", "${f.name} [$label]: ${"%.1f".format(sec)}s, ${segs.size} seg, spk=${segs.map { it.speaker }.distinct().size}: " + segs.joinToString(" | ") { "s${it.speaker} ${it.startMs}-${it.endMs} ${it.text}" })
            }
        }
    }

    @Test
    fun registeredRecordingIsProcessedAndExported() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val src = File(ctx.filesDir, "eval/russian_test_short_from_t_one.wav")
        assumeTrue(src.exists())
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, com.rassvet.essential.data.local.AppDatabase::class.java).build()
        val repo = RecordingRepository(ctx, db)
        val rec = repo.registerRecordedAudio(src.copyTo(File(ctx.cacheDir, "e2e.wav"), true), "e2e")
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline &&
            db.recordingDao().get(rec.id)?.processingStatus.let { it == "queued" || it == "processing" }) kotlinx.coroutines.delay(500)
        val json = repo.exportJson(rec.id)
        Log.i("SPEECH_EVAL", "e2e status=${db.recordingDao().get(rec.id)?.processingStatus} json=$json")
        assertTrue(db.recordingDao().get(rec.id)?.processingStatus == "completed")
        assertTrue(json.contains("\"speaker_id\""))
        db.close()
    }

    /** Imports files/eval/demo.wav into the REAL app database through the normal pipeline, so the UI can be inspected. */
    @Test
    fun importIntoAppDatabase(): Unit = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val src = File(ctx.filesDir, "eval/demo.wav")
        assumeTrue(src.exists())
        val db = com.rassvet.essential.data.local.AppDatabase.build(ctx)
        val repo = RecordingRepository(ctx, db)
        val rec = repo.registerRecordedAudio(src.copyTo(File(ctx.filesDir, "recordings/demo-${System.currentTimeMillis()}.wav"), true), "Совещание, 3 голоса")
        val deadline = System.currentTimeMillis() + 180_000
        while (System.currentTimeMillis() < deadline && db.recordingDao().get(rec.id)?.processingStatus.let { it == "queued" || it == "processing" }) kotlinx.coroutines.delay(500)
        Log.i("SPEECH_EVAL", "demo status=${db.recordingDao().get(rec.id)?.processingStatus} segs=${db.recordingDao().segments(rec.id).size}")
    }

    /** Cosine similarity of CAM++ embeddings between six lines (A,B,A,B,A,B) to judge how separable the voices are. */
    @Test
    fun embeddingSimilarityMatrix(): Unit = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val files = (0..5).map { File(ctx.filesDir, "lines/line$it.wav") }
        assumeTrue(files.all { it.exists() })
        val dec = MediaCodecAudioDecoder()
        SpeakerMatcher(SpeechModelSource.assets(ctx)).use { m ->
            val e = files.map { m.embed(DspChain.floats(dec.decode16kMono(it.absolutePath))) }
            for (i in 0..5) Log.i("SPEECH_EVAL", "sim row $i: " + (0..5).joinToString(" ") { j -> "%.2f".format(SpeakerMatcher.cosine(e[i]!!, e[j]!!)) })
        }
    }
}
