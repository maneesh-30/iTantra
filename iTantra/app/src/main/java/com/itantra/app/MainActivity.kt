package com.itantra.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/**
 * MainActivity — the single screen.
 *
 * Push-to-talk (PTT) walkie-talkie flow:
 *   1. Pick a language; the offline STT + TTS models load in the background.
 *   2. Type your name + a channel, then Connect. Everyone on the same channel is in the room.
 *   3. HOLD the button and speak one sentence; on RELEASE the whole clip is transcribed
 *      (full-clip = accurate), shown, and the text is sent to the channel.
 *   4. Incoming messages are shown and spoken aloud (TTS). Alerts play at max volume.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var tvConnPill: TextView

    private lateinit var etName: EditText
    private lateinit var etChannel: EditText
    private lateinit var btnConnect: Button
    private lateinit var cbSendAlert: CheckBox
    private lateinit var spinnerLang: Spinner
    private lateinit var pillContainer: LinearLayout

    private var currentLanguage = Language.EN

    private var stt: SttEngine? = null
    private var tts: TtsEngine? = null
    private val playback by lazy { AudioPlayback(this) }
    private val transport: Transport by lazy { WifiTransport(this) }

    @Volatile
    private var connected = false

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
        tvConnPill = findViewById(R.id.tvConnPill)
        etName = findViewById(R.id.etName)
        etChannel = findViewById(R.id.etChannel)
        btnConnect = findViewById(R.id.btnConnect)
        cbSendAlert = findViewById(R.id.cbSendAlert)
        pillContainer = findViewById(R.id.pillContainer)

        btnRecord.isEnabled = false

        // Ask for microphone permission up front so it is ready when the user talks.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode
            )
        }

        transport.setOnMessage { msg -> onIncoming(msg) }

        btnConnect.setOnClickListener {
            if (connected) {
                transport.stop()
                connected = false
                btnConnect.text = "Connect"
                tvStatus.text = "Disconnected."
                setConnPill(false)
                pillContainer.removeAllViews()
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    tvStatus.text = "Enter your name first."
                    return@setOnClickListener
                }
                val ch = etChannel.text.toString().trim()
                if (ch.isEmpty()) {
                    tvStatus.text = "Enter a channel name first."
                    return@setOnClickListener
                }
                transport.setDisplayName(name)
                transport.start(ch)
                connected = true
                btnConnect.text = "Disconnect"
                tvStatus.text = "On channel \"$ch\". Hold to talk."
                setConnPill(true)
                // Show your own name pill in the room.
                pillContainer.removeAllViews()
                addPill("$name (you)")
                // Keep the screen on while connected so WiFi stays awake to receive.
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

        // --- Language picker. Its first selection also triggers the initial engine load. ---
        spinnerLang = findViewById(R.id.spinnerLang)
        val langs = Language.entries
        spinnerLang.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, langs.map { it.display }
        )
        spinnerLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                if (langs[pos] != currentLanguage || stt == null) reloadEngines(langs[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    /** Update the header connection pill. */
    private fun setConnPill(online: Boolean) {
        tvConnPill.text = if (online) "On air" else "Offline"
        tvConnPill.setBackgroundResource(
            if (online) R.drawable.pill_status_online else R.drawable.pill_status_offline
        )
    }

    /** Add one name pill to the "in this room" row. */
    private fun addPill(name: String) {
        val dp = resources.displayMetrics.density
        val pill = TextView(this).apply {
            text = name
            setBackgroundResource(R.drawable.pill_name)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.brand_dark))
            textSize = 13f
            val padH = (12 * dp).toInt()
            val padV = (5 * dp).toInt()
            setPadding(padH, padV, padH, padV)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (8 * dp).toInt() }
        pillContainer.addView(pill, lp)
    }

    /** Release any current engines and load STT + TTS for the chosen language. */
    private fun reloadEngines(lang: Language) {
        currentLanguage = lang
        btnRecord.isEnabled = false
        tvStatus.text = "Loading ${lang.display} models..."
        thread {
            stt?.release()
            tts?.release()
            stt = null
            tts = null
            try {
                val newStt = SttEngine(assets, lang)
                val newTts = TtsEngine(this, lang)
                runOnUiThread {
                    stt = newStt
                    tts = newTts
                    btnRecord.isEnabled = true
                    tvStatus.text = "Ready (${lang.display}). Hold to talk."
                }
            } catch (e: Throwable) {
                android.util.Log.e("iTantraStt", "failed to load ${lang.display} models", e)
                runOnUiThread {
                    tvStatus.text = "Failed to load ${lang.display} models. See logs."
                }
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
        tvStatus.text = "Listening... release to send."
        btnRecord.text = "Listening..."
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
            android.util.Log.i("iTantraStt", "lang=${engine?.language} text=[$text]")
            if (text.isNotEmpty()) {
                appendLine("You: $text")
                if (connected) transport.send(text, cbSendAlert.isChecked, currentLanguage.code)
            }
            runOnUiThread {
                tvStatus.text =
                    if (connected) "On channel. Hold to talk." else "Ready. Hold to talk."
            }
        }
    }

    /** A message arrived from another phone: show it and speak it aloud. */
    private fun onIncoming(msg: Message) {
        appendLine("${msg.from}: ${msg.text}")
        val engine = tts ?: return
        thread {
            try {
                val audio = engine.synthesize(msg.text)
                playback.play(audio.samples, audio.sampleRate, msg.alert)
            } catch (e: Throwable) {
                android.util.Log.e("iTantraTTS", "failed to speak incoming message", e)
            }
        }
    }

    /** Append a line to the message list, on the UI thread. */
    private fun appendLine(line: String) {
        transcript.append(line).append("\n")
        runOnUiThread { tvTranscript.text = transcript.toString() }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        recordThread?.join(500)
        transport.stop()
        playback.stop()
        stt?.release()
        tts?.release()
    }
}
