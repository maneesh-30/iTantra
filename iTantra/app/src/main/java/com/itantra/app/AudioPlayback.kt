package com.itantra.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * AudioPlayback — plays generated speech through the speaker. Used only by [Speaker], which
 * plays one clip at a time and decides what may interrupt what.
 *
 * Alert messages play at maximum media volume and are peak-normalised so they are clearly
 * louder; the user's volume is restored afterwards.
 */
class AudioPlayback(private val context: Context) {

    @Volatile private var track: AudioTrack? = null

    /**
     * Play audio samples (floats in -1.0..1.0) and block until done.
     * @return true if it played to the end, false if [stop] cut it short.
     */
    fun play(samplesIn: FloatArray, sampleRate: Int, alert: Boolean = false): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var restoreVolume = -1
        var samples = samplesIn
        if (alert) {
            restoreVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            samples = normalize(samplesIn)
        }

        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
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
            .setBufferSizeInBytes(maxOf(minBuf, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        try {
            audioTrack.play()
            // In stream mode write() returns once the data is buffered, not when it has played,
            // so wait for the playback head to reach the end before stopping.
            audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            val deadline = System.currentTimeMillis() + samples.size * 1000L / sampleRate + 500
            while (track === audioTrack &&
                audioTrack.playbackHeadPosition < samples.size &&
                System.currentTimeMillis() < deadline
            ) {
                Thread.sleep(20)
            }
        } catch (_: Exception) {
            // stop() released the track mid-write
        }
        val finished = track === audioTrack
        if (finished) track = null
        runCatching { audioTrack.stop() }
        audioTrack.release()
        if (restoreVolume >= 0) am.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0)
        return finished
    }

    /** Cut the current clip short (play() then returns false). */
    fun stop() {
        val t = track ?: return
        track = null
        runCatching { t.pause(); t.flush() }
    }

    /** Peak-normalize to ~0.99 so alert audio uses the full volume range (louder). */
    private fun normalize(samples: FloatArray): FloatArray {
        var peak = 0f
        for (s in samples) peak = maxOf(peak, kotlin.math.abs(s))
        if (peak <= 0f) return samples
        val gain = 0.99f / peak
        if (gain <= 1f) return samples
        return FloatArray(samples.size) { samples[it] * gain }
    }
}
