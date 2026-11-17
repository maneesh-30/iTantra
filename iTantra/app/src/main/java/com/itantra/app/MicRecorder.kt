package com.itantra.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * MicRecorder — records 16 kHz mono speech while the talk button is held.
 * Shared by the talk screen and the accuracy test.
 */
class MicRecorder(val sampleRate: Int = 16000, private val maxSeconds: Int = 30) {

    @Volatile private var running = false
    private var worker: Thread? = null
    private val chunks = ArrayList<FloatArray>()

    /**
     * Start recording. Returns false if the microphone could not be opened (no permission,
     * or another app is using it).
     * @param onLevel loudness 0..1, about 10 times a second (on the recording thread)
     * @param onLimit called once if recording stops itself at [maxSeconds]
     */
    @SuppressLint("MissingPermission")
    fun start(onLevel: (Float) -> Unit, onLimit: () -> Unit): Boolean {
        if (running) return false
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, sampleRate / 5 * 2),
            )
        } catch (_: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        synchronized(chunks) { chunks.clear() }
        running = true
        recorder.startRecording()
        worker = thread(name = "itantra-mic") {
            val buf = ShortArray(sampleRate / 10)  // 100 ms per read keeps the level meter lively
            var total = 0
            while (running) {
                val n = recorder.read(buf, 0, buf.size)
                if (n <= 0) continue
                val chunk = FloatArray(n) { buf[it] / 32768f }
                synchronized(chunks) { chunks.add(chunk) }
                onLevel(level(chunk))
                total += n
                if (total >= maxSeconds * sampleRate) {
                    running = false
                    onLimit()
                }
            }
            recorder.stop()
            recorder.release()
        }
        return true
    }

    /** Stop and return everything recorded. Blocks briefly; call off the main thread. */
    fun stop(): FloatArray {
        running = false
        worker?.join()
        worker = null
        val list = synchronized(chunks) { chunks.toList().also { chunks.clear() } }
        val out = FloatArray(list.sumOf { it.size })
        var offset = 0
        for (c in list) { c.copyInto(out, offset); offset += c.size }
        return out
    }

    /** RMS loudness mapped from roughly -50..-10 dBFS onto 0..1. */
    private fun level(chunk: FloatArray): Float {
        var sum = 0.0
        for (s in chunk) sum += s * s
        val rms = sqrt(sum / chunk.size)
        val db = 20 * log10(rms + 1e-6)
        return ((db + 50) / 40).toFloat().coerceIn(0f, 1f)
    }
}
