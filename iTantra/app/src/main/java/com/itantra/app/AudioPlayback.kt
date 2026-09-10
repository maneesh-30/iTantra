package com.itantra.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * AudioPlayback — plays generated speech through the speaker.
 *
 * Normal messages play at the current media volume. Alert messages play at MAXIMUM
 * volume and run to completion without being interrupted, as the problem statement
 * requires for emergency alerts.
 */
class AudioPlayback(private val context: Context) {

    @Volatile
    private var track: AudioTrack? = null

    /**
     * Play audio samples (floats in -1.0..1.0).
     * @param alert if true, force maximum volume for an emergency alert.
     */
    fun play(samplesIn: FloatArray, sampleRate: Int, alert: Boolean = false) {
        stop() // stop anything currently playing

        var samples = samplesIn
        if (alert) {
            // 1) Force the media stream to maximum volume.
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            // 2) Software-boost: peak-normalize so it is louder even if volume was already high.
            samples = normalize(samplesIn)
        }

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = maxOf(minBuf, samples.size * 4) // 4 bytes per float

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        audioTrack.play()
        // Blocking write; call this from a background thread.
        // In stream mode this returns once the data is buffered, NOT when it finishes
        // playing, so we must wait for the buffer to drain before stopping.
        audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)

        val durationMs = samples.size * 1000L / sampleRate
        val deadline = System.currentTimeMillis() + durationMs + 500
        while (track === audioTrack &&
            audioTrack.playbackHeadPosition < samples.size &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(20)
        }

        try {
            audioTrack.stop()
        } catch (_: Exception) {
        }
        audioTrack.release()
        if (track === audioTrack) track = null
    }

    /** Peak-normalize to ~0.99 so alert audio uses the full volume range (louder). */
    private fun normalize(samples: FloatArray): FloatArray {
        var peak = 0f
        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (a > peak) peak = a
        }
        if (peak <= 0f) return samples
        val gain = 0.99f / peak
        if (gain <= 1f) return samples // already loud; don't reduce
        return FloatArray(samples.size) { samples[it] * gain }
    }

    fun stop() {
        track?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        track = null
    }
}
