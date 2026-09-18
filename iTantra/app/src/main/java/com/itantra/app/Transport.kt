package com.itantra.app

/**
 * A received message.
 * @param text the message text
 * @param alert true if it should play as an emergency alert (max volume)
 * @param from a short name of the sender's device
 */
data class Message(
    val text: String,
    val alert: Boolean,
    val from: String,
    val lang: String,
)

/**
 * Transport — how a message travels between phones.
 *
 * This is deliberately a small, swappable interface. Today we implement it with WiFi
 * (WifiTransport). Later, a LoRaTransport can implement the same interface for long range,
 * and nothing else in the app has to change.
 *
 * The model is channel-based, like a walkie-talkie: everyone who joins the same channel
 * name is in the same group and hears each other. Only text crosses the link.
 */
interface Transport {
    /** Join a channel and start sending/receiving. */
    fun start(channel: String)

    /** Send a text message to everyone on the channel. `lang` is the language code of the text. */
    fun send(text: String, alert: Boolean, lang: String)

    /** Set the name shown to others as the sender of your messages. */
    fun setDisplayName(name: String)

    /** Called on the app's main thread whenever a message arrives from someone else. */
    fun setOnMessage(handler: (Message) -> Unit)

    /**
     * Called on the app's main thread with the current list of other people on the channel,
     * whenever it changes. Presence is kept live by a periodic "I'm here" heartbeat: someone
     * appears within ~2s of joining and drops off ~6s after they leave or go out of range.
     */
    fun setOnRoster(handler: (List<String>) -> Unit)

    /** Leave the channel and free resources. */
    fun stop()
}
