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

    /** Send a text message to everyone on the channel. */
    fun send(text: String, alert: Boolean)

    /** Called on the app's main thread whenever a message arrives from someone else. */
    fun setOnMessage(handler: (Message) -> Unit)

    /** Leave the channel and free resources. */
    fun stop()
}
