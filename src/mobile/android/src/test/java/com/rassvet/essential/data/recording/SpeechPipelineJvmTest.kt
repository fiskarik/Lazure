package com.rassvet.essential.data.recording

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Runs the real SpeechPipeline (VAD, GigaAM, diarization, speaker refinement, overlap marking) on the JVM with the
 * sherpa-onnx Linux JNI build, so pipeline changes can be checked on real recordings without a phone. DTLN and
 * MossFormer2 need onnxruntime-android and are skipped. Skipped unless these are set:
 *
 *   LAZURE_SHERPA_JNI_DIR  directory with libsherpa-onnx-jni.so (sherpa-onnx-v2.0.1-linux-x64-jni.tar.bz2, lib/)
 *   LAZURE_MODELS_DIR      directory laid out like assets/ (contains models/asr-gigaam-v3-ru/..., models/vad/...)
 *   LAZURE_EVAL_DIR        directory with 16 kHz mono PCM16 .wav files (ffmpeg -i in.m4a -ac 1 -ar 16000 x.wav)
 *   LAZURE_NUM_SPEAKERS    optional fixed speaker count
 *
 * For every file it prints the segments and writes <name>.json (competition format) to LAZURE_EVAL_DIR/out, ready for
 * tools/eval/evaluate.py.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechPipelineJvmTest {
    @Test
    fun transcribesEvalFiles() = runBlocking {
        val models = System.getenv("LAZURE_MODELS_DIR")?.let(::File)
        val eval = System.getenv("LAZURE_EVAL_DIR")?.let(::File)
        assumeTrue("LAZURE_MODELS_DIR / LAZURE_EVAL_DIR not set", models != null && eval != null)
        ShadowLog.stream = System.out
        val pipeline = SpeechPipeline(RuntimeEnvironment.getApplication(), SpeechModelSource.directory(models!!))
        assertTrue("models missing in $models", pipeline.modelsAvailable())
        val out = File(eval, "out").apply { mkdirs() }
        val numSpeakers = System.getenv("LAZURE_NUM_SPEAKERS")?.toIntOrNull() ?: -1
        val files = eval!!.listFiles { f -> f.extension == "wav" }!!.sorted()
        assumeTrue("no .wav files in $eval", files.isNotEmpty())
        for (wav in files) {
            val pcm = readPcm16kMono(wav)
            val t0 = System.nanoTime()
            val segments = pipeline.process({ pcm }, onProgress = { _, _ -> },
                options = PipelineOptions(denoise = false, separate = false, numSpeakers = numSpeakers))
            val sec = (System.nanoTime() - t0) / 1e9
            // Same speaker ids as RecordingProcessor
            val ids = segments.map { it.speaker }.distinct().sorted()
            val items = segments.map { TranscriptExportItem("speaker_${ids.indexOf(it.speaker) + 1}", it.startMs, it.endMs, it.text) }
            println("SPEECH_JVM ${wav.name}: ${"%.1f".format(pcm.size / 16_000.0)} s audio, ${"%.1f".format(sec)} s, ${ids.size} speakers")
            segments.forEachIndexed { i, s ->
                println("SPEECH_JVM   ${items[i].speakerId} ${s.startMs}-${s.endMs}${if (s.overlap) " [overlap]" else ""}: ${s.text}")
            }
            File(out, wav.nameWithoutExtension + ".json").writeText(TranscriptExport.encode(items))
        }
    }

    private fun readPcm16kMono(f: File): ShortArray {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(String(b.array(), 0, 4) == "RIFF" && String(b.array(), 8, 4) == "WAVE") { "${f.name}: not a WAV file" }
        var pos = 12
        var channels = 0; var rate = 0; var bits = 0
        while (pos + 8 <= b.limit()) {
            val id = String(b.array(), pos, 4); val size = b.getInt(pos + 4)
            if (id == "fmt ") { channels = b.getShort(pos + 10).toInt(); rate = b.getInt(pos + 12); bits = b.getShort(pos + 22).toInt() }
            if (id == "data") {
                require(channels == 1 && rate == 16_000 && bits == 16) { "${f.name}: need 16 kHz mono PCM16, got $rate Hz $channels ch $bits bit" }
                val n = minOf(size, b.limit() - pos - 8) / 2
                return ShortArray(n) { b.getShort(pos + 8 + it * 2) }
            }
            pos += 8 + size + (size and 1)
        }
        error("${f.name}: no data chunk")
    }
}
