package com.itantra.app

/**
 * A received message.
 * @param id unique message id (used for "heard by" receipts)
 * @param text the message text
 * @param alert true if it should play as an emergency alert (max volume, never interrupted)
 * @param from the sender
 * @param lang language code of the text, so it is read with the right voice
 */
data class Message(
    val id: String,
    val text: String,
    val alert: Boolean,
    val from: Peer,
    val lang: String,
)

/** Someone on the channel. `id` is unique per phone; `name` is what they typed. */
data class Peer(val id: String, val name: String)

/**
 * Transport — how a message travels between phones.
 *
 * This is deliberately a small, swappable interface. Today we implement it with WiFi
 * (WifiTransport). Later, a LoRaTransport can implement the same interface for long range,
 * and nothing else in the app has to change.
 *
 * The model is channel-based, like a walkie-talkie: everyone who joins the same channel
 * name is in the same group and hears each other. Only text crosses the link.
 * All callbacks run on the app's main thread.
 */
interface Transport {
    /** Join a channel (case-insensitive) and start sending/receiving. */
    fun start(channel: String)

    /** Send a text message to everyone on the channel. Returns the message id. */
    fun send(text: String, alert: Boolean, lang: String): String

    /** Set the name shown to others as the sender of your messages. */
    fun setDisplayName(name: String)

    fun setOnMessage(handler: (Message) -> Unit)

    /**
     * The other people on the channel, whenever it changes. Presence is kept live by a periodic
     * heartbeat: someone appears within ~2s of joining and drops off ~6s after they go out of
     * range, or immediately when they leave.
     */
    fun setOnRoster(handler: (List<Peer>) -> Unit)

    /** Someone confirmed they received one of our messages. */
    fun setOnHeard(handler: (messageId: String, by: Peer) -> Unit)

    /** Whether this phone is on a network that can reach anyone (WiFi or a hotspot). */
    fun setOnLinkChange(handler: (up: Boolean) -> Unit)

    /** Leave the channel and free resources. */
    fun stop()
}
