package com.itantra.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.concurrent.thread

/**
 * BenchmarkActivity — reads fixed reference sentences aloud and measures accuracy + speed.
 *
 * For each sentence: record while the button is held, transcribe on release, then compute the
 * Word Error Rate (transcription vs reference) and the Real-Time Factor (processing time ÷
 * audio length). Running overall WER and average RTF are shown at the bottom — the numbers the
 * problem statement grades (accuracy + latency).
 */
class BenchmarkActivity : AppCompatActivity() {

    private lateinit var tvProgress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvReference: TextView
    private lateinit var btnRead: Button
    private lateinit var tvHyp: TextView
    private lateinit var tvSentenceMetrics: TextView
    private lateinit var btnNext: Button
    private lateinit var tvOverall: TextView

    private lateinit var language: Language
    private lateinit var sentences: List<String>
    private var index = 0

    private var stt: SttEngine? = null

    private var totalEdits = 0
    private var totalRefWords = 0
    private var rtfSum = 0.0
    private var rtfCount = 0

    private val sampleRate = 16000
    @Volatile private var isRecording = false
    private val recordedChunks = ArrayList<FloatArray>()
    private var recordThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)

        language = Language.fromCode(intent.getStringExtra("lang") ?: "en")
        sentences = BenchmarkSentences.forLanguage(language)

        tvProgress = findViewById(R.id.tvProgress)
        tvStatus = findViewById(R.id.tvStatus)
        tvReference = findViewById(R.id.tvReference)
        btnRead = findViewById(R.id.btnRead)
        tvHyp = findViewById(R.id.tvHyp)
        tvSentenceMetrics = findViewById(R.id.tvSentenceMetrics)
        btnNext = findViewById(R.id.btnNext)
        tvOverall = findViewById(R.id.tvOverall)

        btnRead.isEnabled = false
        showSentence()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        // Load the STT model for this language in the background.
        tvStatus.text = "Loading ${language.display} model…"
        thread {
            try {
                val engine = SttEngine(assets, language)
                runOnUiThread {
                    stt = engine
                    btnRead.isEnabled = true
                    tvStatus.text = "Hold the button and read the sentence aloud."
                }
            } catch (e: Throwable) {
                android.util.Log.e("iTantraBench", "model load failed", e)
                runOnUiThread { tvStatus.text = "Failed to load model. See logs." }
            }
        }

        btnRead.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick(); stopAndScore(); true
                }
                else -> false
            }
        }

        btnNext.setOnClickListener {
            index++
            if (index >= sentences.size) {
                finishBenchmark()
            } else {
                tvHyp.text = "—"
                tvSentenceMetrics.text = ""
                btnNext.isEnabled = false
                showSentence()
            }
        }
    }

    private fun showSentence() {
        tvProgress.text = "${index + 1} / ${sentences.size}"
        tvReference.text = sentences[index]
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        stt ?: return
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) { tvStatus.text = "Microphone permission needed."; return }

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSizeBytes = maxOf(minBuf, sampleRate / 5 * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeBytes
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            tvStatus.text = "Failed to start microphone."; recorder.release(); return
        }

        recordedChunks.clear()
        isRecording = true
        btnRead.text = "Listening…"
        tvStatus.text = "Reading… release when done."
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
            recorder.stop(); recorder.release()
        }
    }

    private fun stopAndScore() {
        if (!isRecording) return
        isRecording = false
        btnRead.text = "Hold to Read"
        tvStatus.text = "Scoring…"

        thread {
            recordThread?.join()
            val chunks = synchronized(recordedChunks) { recordedChunks.toList() }
            val total = chunks.sumOf { it.size }
            if (total < sampleRate / 4) {
                runOnUiThread { tvStatus.text = "Too short. Hold and read again." }
                return@thread
            }
            val samples = FloatArray(total)
            var offset = 0
            for (c in chunks) { c.copyInto(samples, offset); offset += c.size }

            val audioSec = total.toDouble() / sampleRate
            val engine = stt
            val t0 = SystemClock.elapsedRealtime()
            val hyp = engine?.transcribe(samples, sampleRate)?.trim().orEmpty()
            val procMs = SystemClock.elapsedRealtime() - t0
            val rtf = (procMs / 1000.0) / audioSec

            val reference = sentences[index]
            val res = Wer.compare(reference, hyp)
            totalEdits += res.edits
            totalRefWords += res.refWords
            rtfSum += rtf
            rtfCount++

            val overallWer = if (totalRefWords > 0) totalEdits.toDouble() / totalRefWords else 0.0
            val avgRtf = if (rtfCount > 0) rtfSum / rtfCount else 0.0

            runOnUiThread {
                tvHyp.text = if (hyp.isEmpty()) "(nothing recognised)" else hyp
                tvSentenceMetrics.text = String.format(
                    Locale.US, "WER %.0f%%   ·   RTF %.2f   ·   STT %d ms",
                    res.wer * 100, rtf, procMs
                )
                tvOverall.text = String.format(
                    Locale.US, "Overall WER: %.1f%%     Avg RTF: %.2f",
                    overallWer * 100, avgRtf
                )
                btnNext.isEnabled = true
                btnNext.text = if (index == sentences.size - 1) "Finish" else "Next"
                tvStatus.text = "Hold to read, or tap ${btnNext.text}."
            }
        }
    }

    private fun finishBenchmark() {
        val overallWer = if (totalRefWords > 0) totalEdits.toDouble() / totalRefWords else 0.0
        val avgRtf = if (rtfCount > 0) rtfSum / rtfCount else 0.0
        btnRead.isEnabled = false
        btnNext.isEnabled = false
        tvReference.text = "Benchmark complete."
        tvHyp.text = "—"
        tvSentenceMetrics.text = ""
        tvProgress.text = "${sentences.size} / ${sentences.size}"
        tvStatus.text = "${language.display} · ${rtfCount} sentences scored."
        tvOverall.text = String.format(
            Locale.US, "Overall WER: %.1f%%     Avg RTF: %.2f", overallWer * 100, avgRtf
        )
        showResultDialog(overallWer * 100, avgRtf)
    }

    /** Popup card with the final numbers — makes a clean screenshot for the deck. */
    private fun showResultDialog(werPct: Double, avgRtf: Double) {
        val v = layoutInflater.inflate(R.layout.dialog_benchmark_result, null)
        v.findViewById<TextView>(R.id.tvDlgSub).text = "${language.display} · $rtfCount sentences"
        v.findViewById<TextView>(R.id.tvDlgWer).text = String.format(Locale.US, "%.1f%%", werPct)
        v.findViewById<TextView>(R.id.tvDlgRtf).text = String.format(Locale.US, "%.2f", avgRtf)
        v.findViewById<TextView>(R.id.tvDlgFoot).text =
            if (language == Language.HI) "On-device · offline · IndicConformer int8"
            else "On-device · offline · Moonshine"

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(v)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.86).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        v.findViewById<Button>(R.id.btnDlgClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        recordThread?.join(500)
        stt?.release()
    }
}
