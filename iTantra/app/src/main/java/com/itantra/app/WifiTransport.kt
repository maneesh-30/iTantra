package com.itantra.app

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.concurrent.thread

/**
 * WifiTransport — sends messages over the local WiFi network using UDP broadcast.
 *
 * Why broadcast (not multicast): many phone hotspots and home routers silently drop
 * multicast packets. Plain UDP broadcast to 255.255.255.255 stays on the local link and
 * is delivered far more reliably. It is still one-to-many (a walkie-talkie), so it mirrors
 * how a LoRa channel works — swapping in LoRa later keeps the same model.
 *
 * Channel model: every phone sends to the same port. Each packet carries the channel name;
 * a phone only accepts packets whose channel matches its own. "Same channel name" = same group.
 *
 * The phones must be on the same WiFi network or the same phone's hotspot. No internet is used.
 */
class WifiTransport(private val context: Context) : Transport {

    private val broadcastAddr = InetAddress.getByName("255.255.255.255")
    private val port = 45454

    private val selfId = UUID.randomUUID().toString()
    private val deviceName = Build.MODEL ?: "phone"

    // Name others see as the sender. Defaults to the device model until the user sets one.
    @Volatile private var displayName = deviceName

    private var channel: String = ""
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    @Volatile private var running = false

    // Each message carries a unique id; we drop ids we've already seen so the same message
    // (sent twice and to several broadcast addresses for reliability) is shown/spoken only once.
    private val seenIds = object : LinkedHashMap<String, Boolean>(300, 0.75f, false) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>) = size > 300
    }

    // Live roster of others on the channel, kept fresh by presence heartbeats.
    private val roster = HashMap<String, Long>()          // name -> last-heard (elapsedRealtime)
    private val presenceIntervalMs = 2000L                // how often we announce ourselves
    private val presenceTimeoutMs = 6000L                 // drop someone after 3 missed beats

    private var onMessage: ((Message) -> Unit)? = null
    private var onRoster: ((List<String>) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun setOnMessage(handler: (Message) -> Unit) {
        onMessage = handler
    }

    override fun setOnRoster(handler: (List<String>) -> Unit) {
        onRoster = handler
    }

    override fun setDisplayName(name: String) {
        val trimmed = name.trim()
        displayName = if (trimmed.isEmpty()) deviceName else trimmed
    }

    override fun start(channel: String) {
        stop()
        this.channel = channel.trim()

        // Android drops broadcast/multicast packets on WiFi unless we hold this lock.
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("itantra").apply {
            setReferenceCounted(true)
            acquire()
        }

        // Keep WiFi fully awake so we keep receiving broadcasts while the phone is idle.
        val wifiMode = if (Build.VERSION.SDK_INT >= 29) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifi.createWifiLock(wifiMode, "itantra-wifi").apply {
            setReferenceCounted(false)
            acquire()
        }

        val s = DatagramSocket(null)
        s.reuseAddress = true
        s.broadcast = true
        s.bind(InetSocketAddress(port))
        socket = s
        running = true
        Log.i("iTantraNet", "started: channel='${this.channel}' port=$port self=$selfId")

        thread {
            val buf = ByteArray(8192)
            while (running) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    s.receive(packet)
                    val json = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    Log.i("iTantraNet", "recv raw from ${packet.address}: $json")
                    val obj = JSONObject(json)
                    if (obj.optString("id") == selfId) continue          // ignore our own
                    if (obj.optString("ch") != this.channel) continue    // wrong channel
                    val mid = obj.optString("mid")
                    if (mid.isNotEmpty()) {
                        val dup = synchronized(seenIds) { seenIds.put(mid, true) != null }
                        if (dup) continue                                 // already shown this message
                    }
                    if (obj.optString("type", "voice") == "presence") {   // heartbeat, not a message
                        val who = obj.optString("from", "phone")
                        synchronized(roster) { roster[who] = SystemClock.elapsedRealtime() }
                        notifyRoster()
                        continue
                    }
                    val msg = Message(
                        text = obj.optString("text"),
                        alert = obj.optBoolean("alert", false),
                        from = obj.optString("from", "phone"),
                        lang = obj.optString("lang", "en"),
                    )
                    Log.i("iTantraNet", "deliver: ${msg.from}: ${msg.text}")
                    mainHandler.post { onMessage?.invoke(msg) }
                } catch (e: Exception) {
                    if (running) Log.w("iTantraNet", "receive error", e)
                }
            }
        }

        // Heartbeat: announce ourselves every ~2s and drop people we stop hearing from.
        thread {
            while (running) {
                sendPresence()
                pruneRoster()
                try {
                    Thread.sleep(presenceIntervalMs)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    override fun send(text: String, alert: Boolean, lang: String) {
        if (socket == null) return
        val ch = channel
        val json = JSONObject()
            .put("id", selfId)
            .put("mid", UUID.randomUUID().toString())
            .put("ch", ch)
            .put("type", "voice")
            .put("alert", alert)
            .put("text", text)
            .put("from", displayName)
            .put("lang", lang)
            .toString()
        thread { sendBytes(json) }
    }

    /** Broadcast a tiny "I'm here" heartbeat so others keep us on their roster. */
    private fun sendPresence() {
        if (socket == null) return
        val json = JSONObject()
            .put("id", selfId)
            .put("mid", UUID.randomUUID().toString())
            .put("ch", channel)
            .put("type", "presence")
            .put("from", displayName)
            .toString()
        sendBytes(json)
    }

    /** Send one UDP payload to every broadcast address (twice; UDP can drop, payload is tiny). */
    private fun sendBytes(json: String) {
        val s = socket ?: return
        try {
            val bytes = json.toByteArray(Charsets.UTF_8)
            // Every interface's directed broadcast (e.g. 192.168.x.255) plus the limited broadcast.
            // On a hotspot HOST, 255.255.255.255 does not reach clients, but the interface's does.
            for (addr in broadcastAddresses()) {
                val packet = DatagramPacket(bytes, bytes.size, addr, port)
                s.send(packet)
                s.send(packet)
            }
        } catch (e: Exception) {
            Log.w("iTantraNet", "send error", e)
        }
    }

    /** Remove people we haven't heard a heartbeat from recently, then notify if it changed. */
    private fun pruneRoster() {
        val now = SystemClock.elapsedRealtime()
        var changed = false
        synchronized(roster) {
            val it = roster.entries.iterator()
            while (it.hasNext()) {
                if (now - it.next().value > presenceTimeoutMs) {
                    it.remove(); changed = true
                }
            }
        }
        if (changed) notifyRoster()
    }

    /** Push the current roster (sorted names) to the UI on the main thread. */
    private fun notifyRoster() {
        val names = synchronized(roster) { roster.keys.sorted() }
        mainHandler.post { onRoster?.invoke(names) }
    }

    /** All usable broadcast addresses (per-interface directed broadcast + limited broadcast). */
    private fun broadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (ni in ifaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { if (!list.contains(it)) list.add(it) }
                }
            }
        } catch (_: Exception) {
        }
        if (!list.contains(broadcastAddr)) list.add(broadcastAddr)
        return list
    }

    override fun stop() {
        running = false
        synchronized(roster) { roster.clear() }
        notifyRoster()
        socket?.close()
        socket = null
        try {
            multicastLock?.release()
        } catch (_: Exception) {
        }
        multicastLock = null
        try {
            wifiLock?.release()
        } catch (_: Exception) {
        }
        wifiLock = null
    }
}
