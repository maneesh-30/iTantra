package com.itantra.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * MainActivity — two screens in one activity:
 *   Join: your name, a channel and the language you speak.
 *   Talk: header (channel, people, timer), Messages / People tabs, and the talk dock
 *         (alert toggle, hold-to-talk button, type instead).
 */
class MainActivity : AppCompatActivity(), TalkButton.Listener, ChatAdapter.Listener {

    // Join screen
    private lateinit var root: View
    private lateinit var joinScreen: View
    private lateinit var tilName: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var tilChannel: TextInputLayout
    private lateinit var etChannel: TextInputEditText
    private lateinit var channelChips: ChipGroup
    private lateinit var langGroup: MaterialButtonToggleGroup
    private lateinit var langProgress: View
    private lateinit var langReady: View
    private lateinit var tvLangStatus: TextView
    private lateinit var netRow: View
    private lateinit var netIcon: ImageView
    private lateinit var netTitle: TextView
    private lateinit var netBody: TextView

    // Talk screen
    private lateinit var talkScreen: View
    private lateinit var header: View
    private lateinit var tvChannel: TextView
    private lateinit var tvPeople: TextView
    private lateinit var tvTimer: TextView
    private lateinit var liveDot: View
    private lateinit var btnLangChip: MaterialButton
    private lateinit var tabGroup: MaterialButtonToggleGroup
    private lateinit var tabPeople: MaterialButton
    private lateinit var messagesPane: View
    private lateinit var peoplePane: View
    private lateinit var chatList: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvEmptyBody: TextView
    private lateinit var radar: RadarView
    private lateinit var peopleList: LinearLayout
    private lateinit var netBanner: View
    private lateinit var alertBanner: View
    private lateinit var tvAlertFrom: TextView
    private lateinit var tvAlertText: TextView
    private lateinit var dock: View
    private lateinit var tvHint: TextView
    private lateinit var tvHintSub: TextView
    private lateinit var micRow: View
    private lateinit var typeRow: View
    private lateinit var btnAlert: MaterialButton
    private lateinit var btnSend: MaterialButton
    private lateinit var talkButton: TalkButton
    private lateinit var etMessage: EditText

    // State
    private val prefs by lazy { getSharedPreferences("itantra", MODE_PRIVATE) }
    private var language = Language.EN
    private var sttReady = false
    private var sttFailed = false
    private var loadGeneration = 0
    private val voices = HashMap<Language, TtsEngine>()
    private val transport: Transport by lazy { WifiTransport(this) }
    private val speaker by lazy { Speaker(this) { voiceFor(it) } }
    private val recorder = MicRecorder()
    private val chat = ChatAdapter(this)
    private var joined = false
    private var channel = ""
    private var myName = ""
    private var peers: List<Peer> = emptyList()
    private var joinedAt = 0L
    private var alertArmed = false
    private var textMode = false
    private var recording = false
    private var transcribing = false
    private var cancelArmed = false
    private var recordStart = 0L

    private val ui = Handler(Looper.getMainLooper())
    private val clock = object : Runnable {
        override fun run() {
            tvTimer.text = elapsed(SystemClock.elapsedRealtime() - joinedAt)
            if (recording) updateHint()
            ui.postDelayed(this, 500)
        }
    }
    private val netPoll = object : Runnable {
        override fun run() {
            updateNetRow()
            ui.postDelayed(this, 2000)
        }
    }
    private val restoreHint = Runnable { updateHint() }
    private val hideAlert = Runnable { hideAlertBanner() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        applyInsets()
        setupJoin()
        setupTalk()

        transport.setOnMessage { onIncoming(it) }
        transport.setOnRoster { updatePeople(it) }
        transport.setOnHeard { id, by -> chat.markHeard(id, by.name) }
        transport.setOnLinkChange { up -> showNetBanner(!up) }
        speaker.readAloud = prefs.getBoolean("readAloud", true)
        speaker.speed = prefs.getFloat("speed", 1f)
        speaker.onSpeaking = { chat.setSpeaking(it) }

        onBackPressedDispatcher.addCallback(this) {
            when {
                textMode -> setTextMode(false)
                joined -> confirmLeave()
                else -> finish()
            }
        }

        setLanguage(Language.fromCode(prefs.getString("lang", null)), force = true)
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        joinScreen = findViewById(R.id.joinScreen)
        tilName = findViewById(R.id.tilName)
        etName = findViewById(R.id.etName)
        tilChannel = findViewById(R.id.tilChannel)
        etChannel = findViewById(R.id.etChannel)
        channelChips = findViewById(R.id.channelChips)
        langGroup = findViewById(R.id.langGroup)
        langProgress = findViewById(R.id.langProgress)
        langReady = findViewById(R.id.langReady)
        tvLangStatus = findViewById(R.id.tvLangStatus)
        netRow = findViewById(R.id.netRow)
        netIcon = findViewById(R.id.netIcon)
        netTitle = findViewById(R.id.netTitle)
        netBody = findViewById(R.id.netBody)

        talkScreen = findViewById(R.id.talkScreen)
        header = findViewById(R.id.header)
        tvChannel = findViewById(R.id.tvChannel)
        tvPeople = findViewById(R.id.tvPeople)
        tvTimer = findViewById(R.id.tvTimer)
        liveDot = findViewById(R.id.liveDot)
        btnLangChip = findViewById(R.id.btnLangChip)
        tabGroup = findViewById(R.id.tabGroup)
        tabPeople = findViewById(R.id.tabPeople)
        messagesPane = findViewById(R.id.messagesPane)
        peoplePane = findViewById(R.id.peoplePane)
        chatList = findViewById(R.id.chatList)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyBody = findViewById(R.id.tvEmptyBody)
        radar = findViewById(R.id.radar)
        peopleList = findViewById(R.id.peopleList)
        netBanner = findViewById(R.id.netBanner)
        alertBanner = findViewById(R.id.alertBanner)
        tvAlertFrom = findViewById(R.id.tvAlertFrom)
        tvAlertText = findViewById(R.id.tvAlertText)
        dock = findViewById(R.id.dock)
        tvHint = findViewById(R.id.tvHint)
        tvHintSub = findViewById(R.id.tvHintSub)
        micRow = findViewById(R.id.micRow)
        typeRow = findViewById(R.id.typeRow)
        btnAlert = findViewById(R.id.btnAlert)
        btnSend = findViewById(R.id.btnSend)
        talkButton = findViewById(R.id.talkButton)
        etMessage = findViewById(R.id.etMessage)
    }

    /** The app draws edge to edge: keep content clear of the status bar, nav bar and keyboard. */
    private fun applyInsets() {
        val joinBar = findViewById<View>(R.id.joinBar)
        val joinBarBottom = joinBar.paddingBottom
        val headerBottom = header.paddingBottom
        val dockTop = dock.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = max(bars.bottom, ime.bottom)
            joinScreen.setPadding(bars.left, bars.top, bars.right, 0)
            joinBar.setPadding(joinBar.paddingLeft, joinBar.paddingTop, joinBar.paddingRight, joinBarBottom + bottom)
            header.setPadding(bars.left, bars.top, bars.right, headerBottom)
            dock.setPadding(bars.left, dockTop, bars.right, bottom)
            insets
        }
    }

    // ---------------------------------------------------------------- Join screen

    private fun setupJoin() {
        etName.setText(prefs.getString("name", ""))
        etChannel.setText(prefs.getString("channel", ""))
        etName.doAfterTextChanged { tilName.error = null }
        etChannel.doAfterTextChanged { tilChannel.error = null }
        buildChannelChips()
        langGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) setLanguage(if (id == R.id.btnLangHi) Language.HI else Language.EN)
        }
        findViewById<MaterialButton>(R.id.btnJoin).setOnClickListener { join() }
        findViewById<View>(R.id.btnAccuracy).setOnClickListener { openAccuracyTest() }
    }

    /** Recent channels first, then a few ready-made names, so a new user can tap instead of type. */
    private fun buildChannelChips() {
        channelChips.removeAllViews()
        val recent = prefs.getString("recent", "").orEmpty().split(",").filter { it.isNotBlank() }
        for (name in (recent + listOf("RESCUE-1", "BASE-CAMP", "TEAM-A")).distinct().take(4)) {
            channelChips.addView(Chip(this).apply {
                text = name
                setOnClickListener {
                    etChannel.setText(name)
                    etChannel.setSelection(name.length)
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            })
        }
    }

    private fun updateNetRow() {
        val ok = WifiTransport.hasLocalNetwork()
        val tint = ContextCompat.getColor(this, if (ok) R.color.live_tint else R.color.warn_tint)
        val ink = ContextCompat.getColor(this, if (ok) R.color.live else R.color.warn_ink)
        netRow.backgroundTintList = ColorStateList.valueOf(tint)
        netIcon.imageTintList = ColorStateList.valueOf(ink)
        netTitle.setText(if (ok) R.string.net_ok_title else R.string.net_none_title)
        netBody.setText(if (ok) R.string.net_ok_body else R.string.net_none_body)
    }

    /** Switch the language you speak: load its recognizer and its voice in the background. */
    private fun setLanguage(lang: Language, force: Boolean = false) {
        if (lang == language && !force) return
        language = lang
        prefs.edit().putString("lang", lang.code).apply()
        langGroup.check(if (lang == Language.HI) R.id.btnLangHi else R.id.btnLangEn)
        btnLangChip.text = lang.native

        val gen = ++loadGeneration
        sttReady = false
        sttFailed = false
        showLangStatus()
        talkButton.state = TalkButton.State.LOADING
        updateHint()
        thread {
            val ok = runCatching { Engines.withStt(assets, lang) { } }
                .onFailure { android.util.Log.e("iTantraStt", "failed to load ${lang.display}", it) }
                .isSuccess
            voiceFor(lang)  // warm up this language's voice too
            runOnUiThread {
                if (gen != loadGeneration) return@runOnUiThread
                sttReady = ok
                sttFailed = !ok
                showLangStatus()
                if (!recording && !transcribing) talkButton.state = idleState()
                updateHint()
            }
        }
    }

    private fun showLangStatus() {
        langProgress.visibility = if (!sttReady && !sttFailed) View.VISIBLE else View.GONE
        langReady.visibility = if (sttReady) View.VISIBLE else View.GONE
        tvLangStatus.text = getString(
            when {
                sttReady -> R.string.lang_ready
                sttFailed -> R.string.lang_failed
                else -> R.string.lang_loading
            },
            language.display,
        )
        tvLangStatus.setTextColor(ContextCompat.getColor(this, if (sttFailed) R.color.alert_ink else R.color.muted))
    }

    private fun voiceFor(lang: Language): TtsEngine? = synchronized(voices) {
        voices[lang] ?: runCatching { TtsEngine(this, lang) }
            .onFailure { android.util.Log.e("iTantraTTS", "failed to load ${lang.display} voice", it) }
            .getOrNull()?.also { voices[lang] = it }
    }

    private fun join() {
        val name = etName.text.toString().trim()
        val ch = etChannel.text.toString().trim().uppercase(Locale.ROOT)
        if (name.isEmpty()) tilName.error = getString(R.string.name_error)
        if (ch.isEmpty()) tilChannel.error = getString(R.string.channel_error)
        if (name.isEmpty() || ch.isEmpty()) {
            (if (name.isEmpty()) etName else etChannel).requestFocus()
            return
        }
        hideKeyboard()

        myName = name
        channel = ch
        val recent = prefs.getString("recent", "").orEmpty().split(",").filter { it.isNotBlank() }
        prefs.edit()
            .putString("name", name)
            .putString("channel", ch)
            .putString("recent", (listOf(ch) + recent).distinct().take(3).joinToString(","))
            .apply()

        transport.setDisplayName(name)
        transport.start(ch)
        joined = true
        joinedAt = SystemClock.elapsedRealtime()
        peers = emptyList()

        chat.clear()
        tvChannel.text = ch
        tvEmptyBody.text = getString(R.string.empty_body, ch)
        addSystem(getString(R.string.system_joined_you, ch))
        updatePeople(emptyList())
        tabGroup.check(R.id.tabMessages)
        showTab(messages = true)
        setTextMode(false)
        setAlertArmed(false)
        netBanner.visibility = View.GONE
        updateHint()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ui.removeCallbacks(netPoll)
        ui.post(clock)
        switchScreens(toTalk = true)
        if (!hasMic()) requestMic()
    }

    private fun confirmLeave() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.leave_title, channel))
            .setMessage(R.string.leave_body)
            .setNegativeButton(R.string.stay, null)
            .setPositiveButton(R.string.leave_confirm) { _, _ -> leave() }
            .show()
    }

    private fun leave() {
        if (recording) finishRecording(send = false)
        transport.stop()
        speaker.clear()
        joined = false
        ui.removeCallbacks(clock)
        hideAlertBanner()
        hideKeyboard()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildChannelChips()
        switchScreens(toTalk = false)
        ui.post(netPoll)
    }

    /** Join → Talk: the header drops in and the dock rises. Talk → Join: a quick crossfade. */
    private fun switchScreens(toTalk: Boolean) {
        val show = if (toTalk) talkScreen else joinScreen
        val hide = if (toTalk) joinScreen else talkScreen
        hide.animate().cancel()
        hide.animate().alpha(0f).setDuration(160).withEndAction {
            hide.visibility = View.GONE
            hide.alpha = 1f
        }.start()
        show.visibility = View.VISIBLE
        show.alpha = 0f
        show.animate().alpha(1f).setStartDelay(80).setDuration(260).start()
        if (toTalk) {
            header.translationY = -dp(24f)
            header.animate().translationY(0f).setStartDelay(80).setDuration(340)
                .setInterpolator(DecelerateInterpolator(2f)).start()
            dock.translationY = dp(120f)
            dock.animate().translationY(0f).setStartDelay(120).setDuration(420)
                .setInterpolator(DecelerateInterpolator(2.2f)).start()
        }
    }

    // ---------------------------------------------------------------- Talk screen

    private fun setupTalk() {
        chatList.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatList.adapter = chat
        chatList.itemAnimator = null  // rows animate themselves; status updates must not flicker
        chat.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = updateEmpty()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = updateEmpty()
        })

        talkButton.listener = this
        btnAlert.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            setAlertArmed(!alertArmed)
        }
        findViewById<View>(R.id.btnKeyboard).setOnClickListener { setTextMode(true) }
        findViewById<View>(R.id.btnBackToMic).setOnClickListener { setTextMode(false) }
        btnSend.setOnClickListener { sendTyped() }
        etMessage.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
        }
        findViewById<View>(R.id.btnLeave).setOnClickListener { confirmLeave() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { openSettings() }
        btnLangChip.setOnClickListener { openSettings() }
        findViewById<View>(R.id.btnAlertClose).setOnClickListener { hideAlertBanner() }
        tabGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) showTab(messages = id == R.id.tabMessages)
        }
    }

    private fun showTab(messages: Boolean) {
        val show = if (messages) messagesPane else peoplePane
        val hide = if (messages) peoplePane else messagesPane
        if (show.visibility == View.VISIBLE) return
        hide.visibility = View.GONE
        show.alpha = 0f
        show.visibility = View.VISIBLE
        show.animate().alpha(1f).setDuration(180).start()
    }

    private fun updateEmpty() {
        val empty = chat.items.none { it.kind != ChatItem.Kind.SYSTEM }
        if (empty == (emptyState.visibility == View.VISIBLE)) return
        if (empty) {
            emptyState.alpha = 1f
            emptyState.visibility = View.VISIBLE
        } else {
            emptyState.animate().alpha(0f).setDuration(150).withEndAction { emptyState.visibility = View.GONE }.start()
        }
    }

    private fun addSystem(text: String) {
        chat.add(ChatItem(UUID.randomUUID().toString(), ChatItem.Kind.SYSTEM, text))
        scrollToEnd(force = false)
    }

    /** Follow new messages, unless the user has scrolled up to read older ones. */
    private fun scrollToEnd(force: Boolean) {
        val lm = chatList.layoutManager as LinearLayoutManager
        val last = chat.itemCount - 1
        if (last < 0) return
        if (force || lm.findLastVisibleItemPosition() >= last - 3) chatList.smoothScrollToPosition(last)
    }

    private fun updatePeople(list: List<Peer>) {
        val before = peers.associateBy { it.id }
        val now = list.associateBy { it.id }
        if (joined) {
            val early = SystemClock.elapsedRealtime() - joinedAt < 4000
            for (p in list) if (p.id !in before) {
                addSystem(getString(if (early) R.string.system_here else R.string.system_joined, p.name))
            }
            for (p in peers) if (p.id !in now) addSystem(getString(R.string.system_left, p.name))
        }
        peers = list

        tvPeople.text = if (list.isEmpty()) getString(R.string.only_you)
        else resources.getQuantityString(R.plurals.you_and_others, list.size, list.size)
        liveDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (list.isEmpty()) R.color.disabled else R.color.live)
        )
        tabPeople.text = if (list.isEmpty()) getString(R.string.tab_people)
        else getString(R.string.tab_people_count, list.size + 1)
        radar.setPeople(list)

        peopleList.removeAllViews()
        val inf = LayoutInflater.from(this)
        val rows = listOf(Peer("self", myName) to getString(R.string.this_phone)) +
            list.map { it to getString(R.string.online) }
        for ((p, sub) in rows) {
            val row = inf.inflate(R.layout.item_person, peopleList, false)
            val color = if (p.id == "self") ContextCompat.getColor(this, R.color.brand) else Avatar.color(p.id)
            row.findViewById<TextView>(R.id.avatar).apply {
                text = Avatar.initials(p.name)
                backgroundTintList = ColorStateList.valueOf(color)
            }
            row.findViewById<TextView>(R.id.name).text =
                if (p.id == "self") getString(R.string.you_named, p.name) else p.name
            row.findViewById<TextView>(R.id.sub).text = sub
            peopleList.addView(row)
        }
    }

    private fun showNetBanner(show: Boolean) {
        if (!joined || show == (netBanner.visibility == View.VISIBLE)) return
        if (show) {
            netBanner.alpha = 0f
            netBanner.visibility = View.VISIBLE
            netBanner.animate().alpha(1f).setDuration(200).start()
        } else {
            netBanner.animate().alpha(0f).setDuration(200).withEndAction { netBanner.visibility = View.GONE }.start()
        }
    }

    // ---------------------------------------------------------------- Talking

    override fun onTalkStart() {
        if (!sttReady) {
            flashHint(getString(if (sttFailed) R.string.lang_failed else R.string.lang_loading, language.display))
            return
        }
        if (!hasMic()) {
            requestMic()
            return
        }
        val started = recorder.start(
            onLevel = { level -> ui.post { talkButton.setLevel(level) } },
            onLimit = { ui.post { finishRecording(send = true, limited = true) } },
        )
        if (!started) {
            flashHint(getString(R.string.mic_permission_needed))
            return
        }
        recording = true
        cancelArmed = false
        recordStart = SystemClock.elapsedRealtime()
        talkButton.state = TalkButton.State.RECORDING
        speaker.setPaused(true)  // don't record the speaker reading someone else's message
        updateHint()
    }

    override fun onCancelArmed(armed: Boolean) {
        cancelArmed = armed
        updateHint()
    }

    override fun onTalkEnd(cancelled: Boolean) {
        if (recording) finishRecording(send = !cancelled)
    }

    private fun finishRecording(send: Boolean, limited: Boolean = false) {
        if (!recording) return
        recording = false
        cancelArmed = false
        speaker.setPaused(false)
        if (!send) {
            // Stay busy until the mic is fully closed, so a quick re-press can't collide with it.
            talkButton.state = TalkButton.State.BUSY
            flashHint(getString(R.string.hint_cancelled))
            thread {
                recorder.stop()
                runOnUiThread { talkButton.state = idleState() }
            }
            return
        }
        transcribing = true
        talkButton.state = TalkButton.State.BUSY
        updateHint()
        val lang = language
        val alert = alertArmed
        thread {
            val samples = recorder.stop()
            val tooShort = samples.size < recorder.sampleRate * 2 / 5
            val text = if (tooShort) "" else runCatching {
                Engines.withStt(assets, lang) { it.transcribe(samples, recorder.sampleRate) }.trim()
            }.getOrDefault("")
            runOnUiThread {
                transcribing = false
                talkButton.state = idleState()
                when {
                    tooShort -> flashHint(getString(R.string.hint_too_short))
                    text.isEmpty() -> flashHint(getString(R.string.hint_not_heard))
                    else -> {
                        sendMessage(text, lang, alert)
                        if (limited) flashHint(getString(R.string.hint_limit)) else updateHint()
                    }
                }
            }
        }
    }

    private fun idleState() = when {
        sttReady -> TalkButton.State.IDLE
        sttFailed -> TalkButton.State.DISABLED
        else -> TalkButton.State.LOADING
    }

    private fun sendMessage(text: String, lang: Language, alert: Boolean) {
        val id = if (joined) transport.send(text, alert, lang.code) else UUID.randomUUID().toString()
        chat.add(ChatItem(id, ChatItem.Kind.MINE, text, alert = alert, lang = lang))
        scrollToEnd(force = true)
        if (alert) setAlertArmed(false)  // one alert at a time, so the next message isn't one by accident
    }

    private fun sendTyped() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return
        sendMessage(text, Language.detect(text), alertArmed)
        etMessage.text.clear()
    }

    private fun updateHint() {
        val alertColor = ContextCompat.getColor(this, R.color.alert_ink)
        val inkColor = ContextCompat.getColor(this, R.color.ink)
        val (main, sub, red) = when {
            recording && cancelArmed -> Triple(getString(R.string.hint_release_cancel), "", true)
            recording -> Triple(
                getString(R.string.hint_listening, elapsed(SystemClock.elapsedRealtime() - recordStart)),
                getString(R.string.hint_slide_cancel), alertArmed,
            )
            transcribing -> Triple(getString(R.string.hint_transcribing), "", false)
            textMode -> Triple("", if (alertArmed) getString(R.string.hint_alert_sub) else "", true)
            sttFailed -> Triple(getString(R.string.lang_failed, language.display), "", true)
            !sttReady -> Triple(getString(R.string.hint_loading), "", false)
            alertArmed -> Triple(getString(R.string.hint_hold_alert), getString(R.string.hint_alert_sub), true)
            else -> Triple(getString(R.string.hint_hold), "", false)
        }
        tvHint.text = main
        tvHint.visibility = if (main.isEmpty()) View.GONE else View.VISIBLE
        tvHint.setTextColor(if (red) alertColor else inkColor)
        tvHintSub.text = sub
        tvHintSub.visibility = if (textMode && sub.isEmpty()) View.GONE else View.VISIBLE
        tvHintSub.setTextColor(if (red && sub.isNotEmpty()) alertColor else ContextCompat.getColor(this, R.color.muted))
    }

    /** Show a short message in the hint line, then go back to the normal hint. */
    private fun flashHint(text: String) {
        tvHint.visibility = View.VISIBLE
        tvHint.text = text
        tvHint.setTextColor(ContextCompat.getColor(this, R.color.ink))
        tvHintSub.text = ""
        tvHint.alpha = 0f
        tvHint.translationY = dp(6f)
        tvHint.animate().alpha(1f).translationY(0f).setDuration(180).start()
        ui.removeCallbacks(restoreHint)
        ui.postDelayed(restoreHint, 2600)
    }

    private fun setAlertArmed(on: Boolean) {
        alertArmed = on
        talkButton.alertMode = on
        val bg = ContextCompat.getColor(this, if (on) R.color.alert else R.color.surface_alt)
        val fg = ContextCompat.getColor(this, if (on) R.color.on_brand else R.color.ink_soft)
        btnAlert.backgroundTintList = ColorStateList.valueOf(bg)
        btnAlert.iconTint = ColorStateList.valueOf(fg)
        if (Build.VERSION.SDK_INT >= 30) btnAlert.stateDescription = getString(if (on) R.string.tag_alert else R.string.hint_hold)
        btnSend.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (on) R.color.alert else R.color.brand)
        )
        if (on) {
            btnAlert.animate().scaleX(1.12f).scaleY(1.12f).setDuration(110).withEndAction {
                btnAlert.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
        }
        updateHint()
    }

    private fun setTextMode(on: Boolean) {
        if (textMode == on && (micRow.visibility == View.VISIBLE) != on) return
        textMode = on
        val show = if (on) typeRow else micRow
        val hide = if (on) micRow else typeRow
        hide.visibility = View.GONE
        show.visibility = View.VISIBLE
        show.alpha = 0f
        show.animate().alpha(1f).setDuration(180).start()
        if (on) {
            etMessage.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT)
        } else {
            hideKeyboard()
        }
        updateHint()
    }

    // ---------------------------------------------------------------- Incoming

    private fun onIncoming(msg: Message) {
        val lang = Language.fromCode(msg.lang)
        val id = msg.id.ifEmpty { UUID.randomUUID().toString() }
        chat.add(ChatItem(id, ChatItem.Kind.OTHER, msg.text, msg.from, msg.alert, lang))
        scrollToEnd(force = msg.alert)
        if (msg.alert) {
            showAlertBanner(msg)
            vibrateAlert()
        }
        speaker.say(Speaker.Item(id, msg.text, lang, msg.alert))
    }

    override fun onReplay(item: ChatItem) {
        speaker.say(Speaker.Item(item.id, item.text, item.lang, alert = false), replay = true)
    }

    override fun onCopy(item: ChatItem) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", item.text))
        // Android 13+ shows its own confirmation.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun showAlertBanner(msg: Message) {
        tvAlertFrom.text = getString(R.string.alert_from, msg.from.name)
        tvAlertText.text = msg.text
        alertBanner.animate().cancel()
        if (alertBanner.visibility != View.VISIBLE) {
            alertBanner.visibility = View.VISIBLE
            alertBanner.alpha = 0f
            alertBanner.translationY = -dp(40f)
            alertBanner.animate().alpha(1f).translationY(0f).setDuration(320)
                .setInterpolator(OvershootInterpolator(1.4f)).start()
        }
        ui.removeCallbacks(hideAlert)
        ui.postDelayed(hideAlert, 10_000)
    }

    private fun hideAlertBanner() {
        ui.removeCallbacks(hideAlert)
        if (alertBanner.visibility != View.VISIBLE) return
        alertBanner.animate().alpha(0f).translationY(-dp(24f)).setDuration(200)
            .withEndAction { alertBanner.visibility = View.GONE }.start()
    }

    private fun vibrateAlert() {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 400, 150, 400, 150, 700)
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        else @Suppress("DEPRECATION") v.vibrate(pattern, -1)
    }

    // ---------------------------------------------------------------- Settings & accuracy test

    private fun openSettings() {
        val sheet = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.sheet_settings, null)
        sheet.setContentView(v)
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, dp(24f).toInt() + nav.bottom)
            insets
        }

        v.findViewById<MaterialSwitch>(R.id.swReadAloud).apply {
            isChecked = speaker.readAloud
            setOnCheckedChangeListener { _, on ->
                speaker.readAloud = on
                if (!on) speaker.clear()
                prefs.edit().putBoolean("readAloud", on).apply()
            }
        }
        val tvSpeed = v.findViewById<TextView>(R.id.tvSpeed)
        v.findViewById<Slider>(R.id.sliderSpeed).apply {
            value = (speaker.speed * 10).roundToInt().coerceIn(7, 15).toFloat()
            tvSpeed.text = getString(R.string.speed_value, value / 10f)
            addOnChangeListener { _, value, _ ->
                speaker.speed = value / 10f
                tvSpeed.text = getString(R.string.speed_value, speaker.speed)
                prefs.edit().putFloat("speed", speaker.speed).apply()
            }
        }
        v.findViewById<MaterialButtonToggleGroup>(R.id.sheetLangGroup).apply {
            check(if (language == Language.HI) R.id.sheetLangHi else R.id.sheetLangEn)
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) setLanguage(if (id == R.id.sheetLangHi) Language.HI else Language.EN)
            }
        }
        v.findViewById<View>(R.id.btnSheetAccuracy).setOnClickListener {
            sheet.dismiss()
            openAccuracyTest()
        }
        sheet.show()
    }

    private fun openAccuracyTest() {
        if (recording) return
        startActivity(Intent(this, BenchmarkActivity::class.java).putExtra("lang", language.code))
    }

    // ---------------------------------------------------------------- Helpers

    private fun hasMic() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMic() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            flashHint(getString(R.string.mic_permission_needed))
        }
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: root
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun elapsed(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    // Timers only run while the screen is visible.
    override fun onStart() {
        super.onStart()
        ui.post(if (joined) clock else netPoll)
    }

    override fun onStop() {
        ui.removeCallbacks(clock)
        ui.removeCallbacks(netPoll)
        super.onStop()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        if (recording) thread { recorder.stop() }
        transport.stop()
        speaker.release()
        val toRelease = synchronized(voices) { voices.values.toList().also { voices.clear() } }
        val finishing = isFinishing
        thread {
            speaker.awaitStopped(3000)  // it may be mid-sentence with one of these voices
            toRelease.forEach { it.release() }
            if (finishing) Engines.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val REQ_MIC = 1
    }
}
