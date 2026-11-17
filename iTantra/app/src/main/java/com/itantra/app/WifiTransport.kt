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
import java.net.NetworkInterface
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

/**
 * WifiTransport — sends messages over the local WiFi network using UDP broadcast.
 *
 * Why broadcast (not multicast): many phone hotspots and home routers silently drop
 * multicast packets. Plain UDP broadcast stays on the local link and is delivered far more
 * reliably. It is one-to-many (a walkie-talkie), so it mirrors how a LoRa channel works.
 *
 * Packets are small JSON objects. Every packet carries the channel name; a phone only accepts
 * packets for its own channel (compared case-insensitively). Packet types:
 *   voice     a message (text, language, alert flag)
 *   presence  "I'm here" heartbeat every 2 s; with "bye": true it means "I'm leaving"
 *   heard     receipt sent straight back to the sender of a voice message
 * Packets carry "v": 2. Older builds treat unknown types as messages, so receipts are only
 * sent to phones that announce v2, and leaving piggybacks on the presence type.
 */
class WifiTransport(private val context: Context) : Transport {

    private val port = 45454
    private val selfId = UUID.randomUUID().toString()
    private val deviceName = Build.MODEL ?: "phone"

    @Volatile private var displayName = deviceName
    @Volatile private var channel = ""
    @Volatile private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Each packet has a unique id; packets are sent twice to several addresses for reliability,
    // so drop ids we have already handled.
    private val seenIds = boundedMap<String, Boolean>(400)
    // Ids of our own messages, so receipts for them can be matched.
    private val sentIds = boundedMap<String, Boolean>(200)

    private val roster = HashMap<String, Pair<String, Long>>()  // peer id -> (name, last heard)
    private val presenceIntervalMs = 2000L
    private val presenceTimeoutMs = 6000L                      // ~3 missed heartbeats

    private var onMessage: ((Message) -> Unit)? = null
    private var onRoster: ((List<Peer>) -> Unit)? = null
    private var onHeard: ((String, Peer) -> Unit)? = null
    private var onLink: ((Boolean) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    override fun setOnMessage(handler: (Message) -> Unit) { onMessage = handler }
    override fun setOnRoster(handler: (List<Peer>) -> Unit) { onRoster = handler }
    override fun setOnHeard(handler: (String, Peer) -> Unit) { onHeard = handler }
    override fun setOnLinkChange(handler: (Boolean) -> Unit) { onLink = handler }

    override fun setDisplayName(name: String) {
        displayName = name.trim().ifEmpty { deviceName }
    }

    override fun start(channel: String) {
        stop()
        this.channel = channel.trim().uppercase(Locale.ROOT)

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Android drops broadcast packets on WiFi unless we hold this lock.
        multicastLock = wifi.createMulticastLock("itantra").apply { setReferenceCounted(false); acquire() }
        // Keep WiFi fully awake so we keep receiving while the phone is idle.
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wifi.createWifiLock(mode, "itantra-wifi").apply { setReferenceCounted(false); acquire() }

        val s = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(port))
        }
        socket = s
        Log.i(TAG, "started: channel='${this.channel}' self=$selfId")
        // Both loops end when `socket` stops being `s`, so leaving and rejoining never leaves
        // an old loop spinning on a closed socket.
        thread(name = "itantra-rx") { receiveLoop(s) }
        thread(name = "itantra-beat") { heartbeatLoop(s) }
    }

    override fun send(text: String, alert: Boolean, lang: String): String {
        val mid = UUID.randomUUID().toString()
        synchronized(sentIds) { sentIds[mid] = true }
        val json = packet("voice", mid).put("alert", alert).put("text", text).put("lang", lang)
        thread { broadcast(json) }
        return mid
    }

    override fun stop() {
        val s = socket
        socket = null
        if (s != null) {
            // Say goodbye so others drop us now rather than after the timeout, then close.
            val bye = packet("presence").put("bye", true)
            thread {
                broadcast(bye, s)
                s.close()
            }
        }
        synchronized(roster) { roster.clear() }
        notifyRoster()
        runCatching { multicastLock?.release() }
        runCatching { wifiLock?.release() }
        multicastLock = null
        wifiLock = null
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(8192)
        while (socket === s) {
            try {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                handle(JSONObject(String(p.data, 0, p.length, Charsets.UTF_8)), p.address)
            } catch (e: Exception) {
                if (socket === s) Log.w(TAG, "receive error", e)
            }
        }
    }

    private fun handle(obj: JSONObject, sender: InetAddress) {
        val fromId = obj.optString("id")
        if (fromId.isEmpty() || fromId == selfId) return
        if (!obj.optString("ch").equals(channel, ignoreCase = true)) return
        val mid = obj.optString("mid")
        if (mid.isNotEmpty() && synchronized(seenIds) { seenIds.put(mid, true) != null }) return

        val from = Peer(fromId, obj.optString("from").ifBlank { "Phone" })
        when (obj.optString("type", "voice")) {
            "presence" -> if (obj.optBoolean("bye")) forget(fromId) else touch(from)
            "heard" -> {
                val ref = obj.optString("ref")
                if (synchronized(sentIds) { sentIds.containsKey(ref) }) main.post { onHeard?.invoke(ref, from) }
            }
            else -> {
                val text = obj.optString("text").trim()
                if (text.isEmpty()) return
                touch(from)
                val msg = Message(mid, text, obj.optBoolean("alert"), from, obj.optString("lang", "en"))
                main.post { onMessage?.invoke(msg) }
                // Receipt, straight back to the sender, only if their app understands it.
                if (mid.isNotEmpty() && obj.optInt("v") >= 2) {
                    send(packet("heard").put("ref", mid), listOf(sender))
                }
            }
        }
    }

    private fun heartbeatLoop(s: DatagramSocket) {
        var lastLink: Boolean? = null
        while (socket === s) {
            broadcast(packet("presence"), s)
            prune()
            val link = hasLocalNetwork()
            if (link != lastLink) {
                lastLink = link
                main.post { onLink?.invoke(link) }
            }
            try { Thread.sleep(presenceIntervalMs) } catch (_: InterruptedException) { }
        }
    }

    private fun packet(type: String, mid: String = UUID.randomUUID().toString()): JSONObject =
        JSONObject()
            .put("v", 2)
            .put("id", selfId)
            .put("mid", mid)
            .put("ch", channel)
            .put("type", type)
            .put("from", displayName)

    private fun broadcast(json: JSONObject, s: DatagramSocket? = socket) = send(json, broadcastAddresses(), s)

    /** Send one payload to each address, twice (UDP can drop packets; the payload is tiny). */
    private fun send(json: JSONObject, to: List<InetAddress>, s: DatagramSocket? = socket) {
        s ?: return
        try {
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            for (addr in to) {
                val p = DatagramPacket(bytes, bytes.size, addr, port)
                s.send(p)
                s.send(p)
            }
        } catch (e: Exception) {
            Log.w(TAG, "send error", e)
        }
    }

    private fun touch(p: Peer) {
        val changed = synchronized(roster) {
            val old = roster.put(p.id, p.name to SystemClock.elapsedRealtime())
            old == null || old.first != p.name
        }
        if (changed) notifyRoster()
    }

    private fun forget(id: String) {
        if (synchronized(roster) { roster.remove(id) != null }) notifyRoster()
    }

    private fun prune() {
        val now = SystemClock.elapsedRealtime()
        val changed = synchronized(roster) { roster.entries.removeIf { now - it.value.second > presenceTimeoutMs } }
        if (changed) notifyRoster()
    }

    private fun notifyRoster() {
        val peers = synchronized(roster) { roster.map { Peer(it.key, it.value.first) } }
            .sortedBy { it.name.lowercase() }
        main.post { onRoster?.invoke(peers) }
    }

    companion object {
        private const val TAG = "iTantraNet"
        private val LIMITED_BROADCAST: InetAddress = InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1))

        private fun <K, V> boundedMap(max: Int) = object : LinkedHashMap<K, V>(max, 0.75f, false) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>) = size > max
        }

        /** Every interface's directed broadcast (e.g. 192.168.x.255), plus 255.255.255.255.
         *  On a hotspot host, 255.255.255.255 does not reach clients but the directed one does. */
        private fun broadcastAddresses(): List<InetAddress> {
            val list = runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { ni -> ni.interfaceAddresses.mapNotNull { it.broadcast } }
                    .distinct()
            }.getOrDefault(emptyList())
            return list + LIMITED_BROADCAST
        }

        /** True when this phone is on a local network others can reach: WiFi, or hosting a hotspot. */
        fun hasLocalNetwork(): Boolean = broadcastAddresses().size > 1
    }
}
