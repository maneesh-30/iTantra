package com.itantra.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.concurrent.thread

/**
 * Speaker — reads incoming messages aloud, one at a time, on its own thread.
 *
 * Rules:
 *  - Messages play in the order they arrive; a new one never cuts off the one playing.
 *  - Alerts jump the queue. An alert interrupts a normal message (which is replayed after it)
 *    and is itself never interrupted, as the problem statement requires.
 *  - While [paused] (the user is talking), normal messages wait; alerts still play.
 *  - Each message is read with the voice for its own language.
 */
class Speaker(context: Context, private val voiceFor: (Language) -> TtsEngine?) {

    class Item(val key: String, val text: String, val lang: Language, val alert: Boolean)

    /** Read normal messages aloud. Alerts are always read. */
    @Volatile var readAloud = true
    @Volatile var speed = 1f

    /** Called on the main thread with the key of the message being read, or null when quiet. */
    var onSpeaking: ((String?) -> Unit)? = null

    private val playback = AudioPlayback(context)
    private val main = Handler(Looper.getMainLooper())
    private val lock = Object()
    private val alerts = ArrayDeque<Item>()
    private val normal = ArrayDeque<Item>()
    private var current: Item? = null
    private var paused = false
    private var alive = true

    private val worker = thread(name = "itantra-speaker", isDaemon = true) { loop() }

    /** After [release], wait for the speaking thread to finish (call off the main thread). */
    fun awaitStopped(ms: Long) = worker.join(ms)

    /** Queue a message. `replay` = the user asked to hear it again, so ignore [readAloud]. */
    fun say(item: Item, replay: Boolean = false) {
        if (!item.alert && !readAloud && !replay) return
        synchronized(lock) {
            if (item.alert) {
                alerts.addLast(item)
                if (current?.alert == false) playback.stop()
            } else {
                normal.addLast(item)
            }
            lock.notifyAll()
        }
    }

    /** Hold normal messages (e.g. while the user records). Alerts still play. */
    fun setPaused(p: Boolean) {
        synchronized(lock) {
            paused = p
            if (p && current?.alert == false) playback.stop()
            lock.notifyAll()
        }
    }

    /** Drop queued normal messages and stop the one playing. A playing alert still finishes. */
    fun clear() {
        synchronized(lock) {
            normal.clear()
            if (current?.alert == false) {
                current = null
                playback.stop()
            }
        }
    }

    fun release() {
        synchronized(lock) {
            alive = false
            alerts.clear()
            normal.clear()
            lock.notifyAll()
        }
        playback.stop()
    }

    private fun loop() {
        while (true) {
            val item = synchronized(lock) {
                while (alive && alerts.isEmpty() && (paused || normal.isEmpty())) lock.wait()
                if (!alive) return
                (alerts.removeFirstOrNull() ?: normal.removeFirst()).also { current = it }
            }
            main.post { onSpeaking?.invoke(item.key) }
            var finished = true
            try {
                val audio = voiceFor(item.lang)?.synthesize(item.text, speed)
                // An alert may have arrived while we were synthesising.
                val preempted = !item.alert && synchronized(lock) { alerts.isNotEmpty() || paused }
                if (audio != null) {
                    finished = !preempted && playback.play(audio.samples, audio.sampleRate, item.alert)
                }
            } catch (e: Throwable) {
                Log.e("iTantraTTS", "failed to speak message", e)
            }
            synchronized(lock) {
                // Cut short by an alert or by the user talking: read it again afterwards,
                // unless it was cleared in the meantime.
                if (!finished && !item.alert && current === item) normal.addFirst(item)
                current = null
            }
            main.post { onSpeaking?.invoke(null) }
        }
    }
}
