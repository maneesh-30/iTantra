package com.itantra.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/**
 * MainActivity — the screen.
 *
 * Push-to-talk (PTT) flow, matching the problem statement's walkie-talkie mode:
 *   1. On app open, load the offline STT model in the background.
 *   2. User HOLDS the button and speaks one sentence. The microphone records only
 *      while the button is held (so background noise is not captured).
 *   3. On RELEASE, the whole recorded clip is transcribed at once (full-clip = accurate)
 *      and the text is added to the transcript.
 *
 * Milestone M2 (corrected): offline full-clip speech -> text, on this one phone.
 * Transport to another phone (M5) and text -> speech (M3) come later.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView

    private var stt: SttEngine? = null

    @Volatile
    private var isRecording = false

    private val sampleRate = 16000
    private val transcript = StringBuilder()

    private val micPermissionCode = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        tvStatus = findViewById(R.id.tvStatus)
        tvTranscript = findViewById(R.id.tvTranscript)
        btnRecord.text = "Hold to Talk"

        // Ask for microphone permission up front so it is ready when the user talks.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode
            )
        }

        // Load the model off the main thread (it takes a moment) and keep the UI responsive.
        btnRecord.isEnabled = false
        tvStatus.text = "Loading model..."
        thread {
            val engine = SttEngine(assets)
            runOnUiThread {
                stt = engine
                btnRecord.isEnabled = true
                tvStatus.text = "Ready. Hold the button and speak (English)."
            }
        }

        // Press and hold = record; release = transcribe.
        btnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick() // for accessibility
                    stopRecordingAndTranscribe()
                    true
                }
                else -> false
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micPermissionCode &&
            (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)
        ) {
            tvStatus.text = "Microphone permission denied. Cannot record."
        }
    }

    // Collected audio chunks for the current press. Concatenated on release.
    private val recordedChunks = ArrayList<FloatArray>()
    private var recordThread: Thread? = null

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val engine = stt ?: return
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            tvStatus.text = "Microphone permission needed."
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeBytes = maxOf(minBuf, sampleRate / 5 * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            tvStatus.text = "Failed to start microphone."
            recorder.release()
            return
        }

        recordedChunks.clear()
        isRecording = true
        tvStatus.text = "Recording... release to send."
        btnRecord.text = "Recording..."
        recorder.startRecording()

        recordThread = thread {
            val buffer = ShortArray(bufferSizeBytes / 2)
            while (isRecording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = FloatArray(read) { buffer[it] / 32768.0f }
                    synchronized(recordedChunks) { recordedChunks.add(chunk) }
                }
            }
            recorder.stop()
            recorder.release()
        }
    }

    private fun stopRecordingAndTranscribe() {
        if (!isRecording) return
        isRecording = false
        btnRecord.text = "Hold to Talk"
        tvStatus.text = "Transcribing..."

        thread {
            recordThread?.join() // wait for the recording loop to finish

            // Concatenate all recorded chunks into one clip.
            val chunks = synchronized(recordedChunks) { recordedChunks.toList() }
            val total = chunks.sumOf { it.size }
            if (total < sampleRate / 4) { // shorter than ~0.25s: ignore
                runOnUiThread { tvStatus.text = "Too short. Hold and speak a bit longer." }
                return@thread
            }
            val samples = FloatArray(total)
            var offset = 0
            for (c in chunks) {
                c.copyInto(samples, offset)
                offset += c.size
            }

            val engine = stt
            val text = engine?.transcribe(samples, sampleRate)?.trim().orEmpty()
            if (text.isNotEmpty()) {
                transcript.append(text).append("\n")
            }
            runOnUiThread {
                tvTranscript.text = transcript.toString()
                tvStatus.text = "Ready. Hold the button and speak (English)."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        recordThread?.join(500)
        stt?.release()
    }
}
