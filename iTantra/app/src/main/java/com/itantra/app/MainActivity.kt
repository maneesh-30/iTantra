package com.itantra.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * MainActivity — three screens in one activity:
 *   1. Join  — enter name, channel, language, then Join.
 *   2. Talk  — mission header + chat transcript (bubbles + timestamps) + hold-to-talk bar.
 *   3. Radar — view-only "who's in the room" (everyone you've heard on the channel).
 * The Talk and Radar screens share the mission header and a bottom tab bar.
 */
class MainActivity : AppCompatActivity() {

    // Join screen
    private lateinit var joinScreen: View
    private lateinit var etName: EditText
    private lateinit var etChannel: EditText
    private lateinit var spinnerLang: Spinner
    private lateinit var btnJoin: Button
    private lateinit var tvJoinStatus: TextView
    private lateinit var btnBenchmark: TextView

    // App (Talk + Radar)
    private lateinit var appScreen: View
    private lateinit var tvChannel: TextView
    private lateinit var tvPeople: TextView
    private lateinit var tvTimer: TextView
    private lateinit var btnLeave: TextView
    private lateinit var chatScreen: View
    private lateinit var radarScreen: View
    private lateinit var radar: RadarView
    private lateinit var messagesContainer: LinearLayout
    private lateinit var scrollMessages: ScrollView
    private lateinit var tvStatus: TextView
    private lateinit var cbSendAlert: CheckBox
    private lateinit var btnRecord: Button
    private lateinit var btnTabChat: TextView
    private lateinit var btnTabRadar: TextView

    private var currentLanguage = Language.EN
    private var myName = ""

    private var stt: SttEngine? = null
    private var tts: TtsEngine? = null
    private val playback by lazy { AudioPlayback(this) }
    private val transport: Transport by lazy { WifiTransport(this) }

    @Volatile private var connected = false
    @Volatile private var isRecording = false

    private val sampleRate = 16000
    private val micPermissionCode = 1

    // Other people currently on the channel (from the presence heartbeat), for radar + count.
    private var roomOthers: List<String> = emptyList()

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val timerHandler = Handler(Looper.getMainLooper())
    private var joinedAt = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            val s = (SystemClock.elapsedRealtime() - joinedAt) / 1000
            tvTimer.text = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        joinScreen = findViewById(R.id.joinScreen)
        etName = findViewById(R.id.etName)
        etChannel = findViewById(R.id.etChannel)
        spinnerLang = findViewById(R.id.spinnerLang)
        btnJoin = findViewById(R.id.btnJoin)
        tvJoinStatus = findViewById(R.id.tvJoinStatus)
        btnBenchmark = findViewById(R.id.btnBenchmark)

        appScreen = findViewById(R.id.appScreen)
        tvChannel = findViewById(R.id.tvChannel)
        tvPeople = findViewById(R.id.tvPeople)
        tvTimer = findViewById(R.id.tvTimer)
        btnLeave = findViewById(R.id.btnLeave)
        chatScreen = findViewById(R.id.chatScreen)
        radarScreen = findViewById(R.id.radarScreen)
        radar = findViewById(R.id.radar)
        messagesContainer = findViewById(R.id.messagesContainer)
        scrollMessages = findViewById(R.id.scrollMessages)
        tvStatus = findViewById(R.id.tvStatus)
        cbSendAlert = findViewById(R.id.cbSendAlert)
        btnRecord = findViewById(R.id.btnRecord)
        btnTabChat = findViewById(R.id.btnTabChat)
        btnTabRadar = findViewById(R.id.btnTabRadar)

        btnRecord.isEnabled = false

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), micPermissionCode
            )
        }

        transport.setOnMessage { msg -> onIncoming(msg) }
        transport.setOnRoster { names -> runOnUiThread { updateRoom(names) } }

        // Language picker (on the Join screen). Its first selection loads the engines.
        val langs = Language.entries
        spinnerLang.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, langs.map { it.display }
        )
        spinnerLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (langs[pos] != currentLanguage || stt == null) reloadEngines(langs[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btnJoin.setOnClickListener { joinChannel() }
        btnBenchmark.setOnClickListener {
            startActivity(Intent(this, BenchmarkActivity::class.java).putExtra("lang", currentLanguage.code))
        }
        btnLeave.setOnClickListener { leaveChannel() }
        btnTabChat.setOnClickListener { showTab(chat = true) }
        btnTabRadar.setOnClickListener { showTab(chat = false) }

        btnRecord.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick(); stopRecordingAndTranscribe(); true
                }
                else -> false
            }
        }
    }

    private fun joinChannel() {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) { tvJoinStatus.text = "Enter your name first."; return }
        val ch = etChannel.text.toString().trim()
        if (ch.isEmpty()) { tvJoinStatus.text = "Enter a channel name first."; return }

        myName = name
        transport.setDisplayName(name)
        transport.start(ch)
        connected = true

        tvChannel.text = ch
        messagesContainer.removeAllViews()
        updateRoom(emptyList())
        showTab(chat = true)

        joinScreen.visibility = View.GONE
        appScreen.visibility = View.VISIBLE

        joinedAt = SystemClock.elapsedRealtime()
        timerHandler.post(timerRunnable)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun leaveChannel() {
        transport.stop()
        connected = false
        timerHandler.removeCallbacks(timerRunnable)
        appScreen.visibility = View.GONE
        joinScreen.visibility = View.VISIBLE
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showTab(chat: Boolean) {
        chatScreen.visibility = if (chat) View.VISIBLE else View.GONE
        radarScreen.visibility = if (chat) View.GONE else View.VISIBLE
        btnTabChat.setTextColor(ContextCompat.getColor(this, if (chat) R.color.amber else R.color.muted))
        btnTabRadar.setTextColor(ContextCompat.getColor(this, if (chat) R.color.muted else R.color.amber))
        if (!chat) radar.setPeople(roomOthers)
    }

    /** Refresh the radar + "N in room" from the live presence roster. */
    private fun updateRoom(others: List<String>) {
        roomOthers = others
        tvPeople.text = "${others.size + 1} in room"
        radar.setPeople(others)
    }

    private fun reloadEngines(lang: Language) {
        currentLanguage = lang
        btnRecord.isEnabled = false
        tvJoinStatus.text = "Loading ${lang.display} models…"
        thread {
            stt?.release(); tts?.release(); stt = null; tts = null
            try {
                val newStt = SttEngine(assets, lang)
                val newTts = TtsEngine(this, lang)
                runOnUiThread {
                    stt = newStt; tts = newTts
                    btnRecord.isEnabled = true
                    tvJoinStatus.text = "Ready (${lang.display})."
                    tvStatus.text = "Hold to talk"
                }
            } catch (e: Throwable) {
                android.util.Log.e("iTantraStt", "failed to load ${lang.display} models", e)
                runOnUiThread { tvJoinStatus.text = "Failed to load ${lang.display} models. See logs." }
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
            tvJoinStatus.text = "Microphone permission denied. Cannot record."
        }
    }

    private val recordedChunks = ArrayList<FloatArray>()
    private var recordThread: Thread? = null

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
        tvStatus.text = "Listening… release to send."
        btnRecord.text = "Listening…"
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

    private fun stopRecordingAndTranscribe() {
        if (!isRecording) return
        isRecording = false
        btnRecord.text = "Hold to Talk"
        tvStatus.text = "Transcribing…"

        thread {
            recordThread?.join()
            val chunks = synchronized(recordedChunks) { recordedChunks.toList() }
            val total = chunks.sumOf { it.size }
            if (total < sampleRate / 4) {
                runOnUiThread { tvStatus.text = "Too short. Hold and speak a bit longer." }
                return@thread
            }
            val samples = FloatArray(total)
            var offset = 0
            for (c in chunks) { c.copyInto(samples, offset); offset += c.size }

            val engine = stt
            val text = engine?.transcribe(samples, sampleRate)?.trim().orEmpty()
            android.util.Log.i("iTantraStt", "lang=${engine?.language} text=[$text]")
            val alert = cbSendAlert.isChecked
            runOnUiThread {
                if (text.isNotEmpty()) {
                    addBubble("You", text, mine = true, alert = alert)
                    if (connected) transport.send(text, alert, currentLanguage.code)
                }
                tvStatus.text = "Hold to talk"
            }
        }
    }

    /** A message arrived from another phone: show it, speak it, and note the sender. */
    private fun onIncoming(msg: Message) {
        addBubble(msg.from, msg.text, mine = false, alert = msg.alert)
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

    /** Build one chat bubble (name + timestamp + text) and append it. Call on the UI thread. */
    private fun addBubble(from: String, text: String, mine: Boolean, alert: Boolean) {
        val dp = resources.displayMetrics.density
        val maxW = (resources.displayMetrics.widthPixels * 0.78).toInt()
        val now = timeFmt.format(Date())

        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * dp).toInt() }
            orientation = LinearLayout.HORIZONTAL
            gravity = if (mine) Gravity.END else Gravity.START
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(
                when { alert -> R.drawable.bubble_alert; mine -> R.drawable.bubble_own; else -> R.drawable.bubble_other }
            )
            val ph = (11 * dp).toInt(); val pv = (7 * dp).toInt()
            setPadding(ph, pv, ph, pv)
        }
        val header = TextView(this).apply {
            textSize = 10f
            this.text = when {
                alert -> "⚠ $from · $now"
                mine -> now
                else -> "$from · $now"
            }
            setTextColor(ContextCompat.getColor(this@MainActivity,
                when { alert -> R.color.danger; mine -> R.color.own_ink; else -> R.color.name_accent }))
        }
        val body = TextView(this).apply {
            this.text = text
            textSize = 15f
            maxWidth = maxW
            setTextColor(ContextCompat.getColor(this@MainActivity,
                when { alert -> R.color.danger_text; mine -> R.color.own_ink; else -> R.color.ink }))
        }
        bubble.addView(header)
        bubble.addView(body)
        row.addView(bubble)
        messagesContainer.addView(row)
        scrollMessages.post { scrollMessages.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        recordThread?.join(500)
        timerHandler.removeCallbacks(timerRunnable)
        transport.stop()
        playback.stop()
        stt?.release()
        tts?.release()
    }
}
