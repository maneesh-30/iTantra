package com.itantra.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale
import kotlin.concurrent.thread

/**
 * BenchmarkActivity — the speech accuracy test.
 *
 * For each reference sentence: hold the button and read it, release, and the app compares
 * what it heard with the sentence: Word Error Rate (accuracy) and Real-Time Factor
 * (processing time ÷ audio length, speed). Running totals and a final result card follow.
 */
class BenchmarkActivity : AppCompatActivity(), TalkButton.Listener {

    private lateinit var tvProgress: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var tvStatus: TextView
    private lateinit var tvReference: TextView
    private lateinit var tvHyp: TextView
    private lateinit var metricsRow: View
    private lateinit var tvMetricWer: TextView
    private lateinit var tvMetricRtf: TextView
    private lateinit var tvMetricTime: TextView
    private lateinit var tvOverall: TextView
    private lateinit var btnRead: TalkButton
    private lateinit var btnNext: MaterialButton

    private lateinit var language: Language
    private lateinit var sentences: List<String>
    private var index = 0
    private var ready = false
    private var recording = false
    private val recorder = MicRecorder()

    private var totalEdits = 0
    private var totalRefWords = 0
    private var rtfSum = 0.0
    private var rtfCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)

        language = Language.fromCode(intent.getStringExtra("lang"))
        sentences = BenchmarkSentences.forLanguage(language)

        tvProgress = findViewById(R.id.tvProgress)
        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tvStatus)
        tvReference = findViewById(R.id.tvReference)
        tvHyp = findViewById(R.id.tvHyp)
        metricsRow = findViewById(R.id.metricsRow)
        tvMetricWer = findViewById(R.id.tvMetricWer)
        tvMetricRtf = findViewById(R.id.tvMetricRtf)
        tvMetricTime = findViewById(R.id.tvMetricTime)
        tvOverall = findViewById(R.id.tvOverall)
        btnRead = findViewById(R.id.btnRead)
        btnNext = findViewById(R.id.btnNext)

        val header = findViewById<View>(R.id.benchHeader)
        val dock = findViewById<View>(R.id.benchDock)
        val headerStart = header.paddingStart
        val headerTop = header.paddingTop
        val headerEnd = header.paddingEnd
        val dockTop = dock.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.benchRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            header.setPadding(bars.left + headerStart, bars.top + headerTop, bars.right + headerEnd, header.paddingBottom)
            dock.setPadding(bars.left, dockTop, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvBenchLang).text = language.native
        progress.max = sentences.size
        btnRead.listener = this
        btnNext.setOnClickListener { next() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        showSentence()

        // Load (or reuse) the shared recognizer for this language.
        tvStatus.text = getString(R.string.lang_loading, language.display)
        thread {
            val ok = runCatching { Engines.withStt(assets, language) { } }.isSuccess
            runOnUiThread {
                ready = ok
                btnRead.state = if (ok) TalkButton.State.IDLE else TalkButton.State.LOADING
                tvStatus.text = if (ok) getString(R.string.bench_hold) else getString(R.string.lang_failed, language.display)
            }
        }
    }

    private fun showSentence() {
        tvProgress.text = getString(R.string.bench_progress, index + 1, sentences.size)
        progress.setProgressCompat(index, true)
        tvReference.text = sentences[index]
        tvHyp.text = getString(R.string.bench_nothing_yet)
        metricsRow.visibility = View.GONE
        btnNext.isEnabled = false
        btnNext.setText(if (index == sentences.size - 1) R.string.bench_finish else R.string.bench_next)
        // Slide the new sentence in.
        tvReference.alpha = 0f
        tvReference.translationX = 40 * resources.displayMetrics.density
        tvReference.animate().alpha(1f).translationX(0f).setDuration(260).start()
    }

    override fun onTalkStart() {
        if (!ready) return
        val started = recorder.start(
            onLevel = { l -> runOnUiThread { btnRead.setLevel(l) } },
            onLimit = { runOnUiThread { onTalkEnd(false) } },
        )
        if (!started) {
            tvStatus.text = getString(R.string.mic_permission_needed)
            return
        }
        recording = true
        btnRead.state = TalkButton.State.RECORDING
        tvStatus.text = getString(R.string.hint_slide_cancel)
    }

    override fun onCancelArmed(armed: Boolean) {
        tvStatus.text = getString(if (armed) R.string.hint_release_cancel else R.string.hint_slide_cancel)
    }

    override fun onTalkEnd(cancelled: Boolean) {
        if (!recording) return
        recording = false
        if (cancelled) {
            thread { recorder.stop() }
            btnRead.state = TalkButton.State.IDLE
            tvStatus.text = getString(R.string.bench_hold)
            return
        }
        btnRead.state = TalkButton.State.BUSY
        tvStatus.text = getString(R.string.bench_scoring)
        val reference = sentences[index]
        thread {
            val samples = recorder.stop()
            if (samples.size < recorder.sampleRate / 4) {
                runOnUiThread {
                    btnRead.state = TalkButton.State.IDLE
                    tvStatus.text = getString(R.string.hint_too_short)
                }
                return@thread
            }
            val audioSec = samples.size.toDouble() / recorder.sampleRate
            val t0 = SystemClock.elapsedRealtime()
            val hyp = runCatching {
                Engines.withStt(assets, language) { it.transcribe(samples, recorder.sampleRate) }.trim()
            }.getOrDefault("")
            val procMs = SystemClock.elapsedRealtime() - t0
            val rtf = procMs / 1000.0 / audioSec
            val res = Wer.compare(reference, hyp)
            runOnUiThread { showScore(hyp, res, rtf, procMs) }
        }
    }

    private fun showScore(hyp: String, res: Wer.Result, rtf: Double, procMs: Long) {
        totalEdits += res.edits
        totalRefWords += res.refWords
        rtfSum += rtf
        rtfCount++

        btnRead.state = TalkButton.State.IDLE
        tvHyp.text = hyp.ifEmpty { getString(R.string.bench_nothing_heard) }
        tvMetricWer.text = String.format(Locale.US, "%.0f%%", res.wer * 100)
        tvMetricWer.setTextColor(ContextCompat.getColor(this, if (res.edits == 0) R.color.live else R.color.ink))
        tvMetricRtf.text = String.format(Locale.US, "%.2f", rtf)
        tvMetricTime.text = String.format(Locale.US, "%d ms", procMs)
        if (metricsRow.visibility != View.VISIBLE) {
            metricsRow.alpha = 0f
            metricsRow.visibility = View.VISIBLE
            metricsRow.animate().alpha(1f).setDuration(220).start()
        }
        tvOverall.text = getString(R.string.bench_overall, overallWer() * 100, rtfSum / rtfCount)
        btnNext.isEnabled = true
        tvStatus.text = getString(R.string.bench_hold)
    }

    private fun overallWer() = if (totalRefWords > 0) totalEdits.toDouble() / totalRefWords else 0.0

    private fun next() {
        index++
        if (index < sentences.size) {
            showSentence()
            return
        }
        progress.setProgressCompat(sentences.size, true)
        btnNext.isEnabled = false
        btnRead.state = TalkButton.State.LOADING
        tvStatus.text = getString(R.string.bench_done)
        showResult()
    }

    private fun showResult() {
        val v = layoutInflater.inflate(R.layout.dialog_benchmark_result, null)
        v.findViewById<TextView>(R.id.tvDlgSub).text = getString(R.string.result_sub, language.display, rtfCount)
        v.findViewById<TextView>(R.id.tvDlgWer).text = String.format(Locale.US, "%.1f%%", overallWer() * 100)
        v.findViewById<TextView>(R.id.tvDlgRtf).text =
            String.format(Locale.US, "%.2f", if (rtfCount > 0) rtfSum / rtfCount else 0.0)
        v.findViewById<TextView>(R.id.tvDlgFoot).setText(
            if (language == Language.HI) R.string.result_foot_hi else R.string.result_foot_en
        )
        MaterialAlertDialogBuilder(this)
            .setView(v)
            .setPositiveButton(R.string.done) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        if (recording) thread { recorder.stop() }
        super.onDestroy()
    }
}
