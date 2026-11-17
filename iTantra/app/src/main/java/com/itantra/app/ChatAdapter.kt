package com.itantra.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

/** Stable colour and initials for a person, shared by the chat, people list and radar. */
object Avatar {
    private val palette = intArrayOf(
        Color.parseColor("#3559C7"), Color.parseColor("#0F7B6C"), Color.parseColor("#7048E8"),
        Color.parseColor("#C2410C"), Color.parseColor("#0B7285"), Color.parseColor("#A61E4D"),
        Color.parseColor("#5F7A1F"),
    )

    fun color(key: String): Int = palette[(key.hashCode() and 0x7fffffff) % palette.size]

    fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

/** One row in the conversation. */
class ChatItem(
    val id: String,
    val kind: Kind,
    val text: String,
    val from: Peer? = null,
    val alert: Boolean = false,
    val lang: Language = Language.EN,
    val time: Long = System.currentTimeMillis(),
) {
    enum class Kind { MINE, OTHER, SYSTEM }

    /** Names of people who confirmed they received this (our own messages only). */
    val heardBy = LinkedHashSet<String>()
    var speaking = false
    internal var animateIn = true
}

class ChatAdapter(private val listener: Listener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onReplay(item: ChatItem)
        fun onCopy(item: ChatItem)
    }

    val items = ArrayList<ChatItem>()

    fun add(item: ChatItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        items.clear()
        @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
    }

    fun markHeard(id: String, name: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0 && items[i].heardBy.add(name)) notifyItemChanged(i, PAYLOAD_STATUS)
    }

    /** Show the speaker icon on the message being read aloud (null = none). */
    fun setSpeaking(id: String?) {
        for ((i, item) in items.withIndex()) {
            val now = item.id == id
            if (item.speaking != now) {
                item.speaking = now
                notifyItemChanged(i, PAYLOAD_SPEAKING)
            }
        }
    }

    override fun getItemCount() = items.size
    override fun getItemViewType(position: Int) = items[position].kind.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return when (ChatItem.Kind.entries[viewType]) {
            ChatItem.Kind.MINE -> MineVH(inf.inflate(R.layout.item_message_mine, parent, false))
            ChatItem.Kind.OTHER -> OtherVH(inf.inflate(R.layout.item_message_other, parent, false))
            ChatItem.Kind.SYSTEM -> SystemVH(inf.inflate(R.layout.item_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
        onBindViewHolder(holder, position, mutableListOf())

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = items[position]
        val partial = payloads.isNotEmpty()
        when (holder) {
            is MineVH -> if (partial) holder.bindStatus(item) else holder.bind(item)
            is OtherVH -> if (partial) holder.bindSpeaking(item) else holder.bind(item, items.getOrNull(position - 1))
            is SystemVH -> holder.bind(item)
        }
        if (!partial) animateIn(holder.itemView, item)
    }

    private fun animateIn(v: View, item: ChatItem) {
        v.animate().cancel()
        v.alpha = 1f
        v.translationX = 0f
        v.translationY = 0f
        if (!item.animateIn) return
        item.animateIn = false
        val d = v.resources.displayMetrics.density
        v.alpha = 0f
        v.translationY = 14 * d
        v.translationX = when (item.kind) {
            ChatItem.Kind.MINE -> 28 * d
            ChatItem.Kind.OTHER -> -28 * d
            ChatItem.Kind.SYSTEM -> 0f
        }
        v.animate().alpha(1f).translationX(0f).translationY(0f)
            .setDuration(280).setInterpolator(DecelerateInterpolator(1.8f)).start()
    }

    private fun time(ctx: Context, item: ChatItem): String =
        android.text.format.DateFormat.getTimeFormat(ctx).format(Date(item.time))

    inner class MineVH(v: View) : RecyclerView.ViewHolder(v) {
        private val bubble: LinearLayout = v.findViewById(R.id.bubble)
        private val tag: TextView = v.findViewById(R.id.tag)
        private val text: TextView = v.findViewById(R.id.text)
        private val time: TextView = v.findViewById(R.id.time)
        private val statusIcon: ImageView = v.findViewById(R.id.statusIcon)
        private val status: TextView = v.findViewById(R.id.status)

        fun bind(item: ChatItem) {
            bubble.setBackgroundResource(if (item.alert) R.drawable.bg_bubble_mine_alert else R.drawable.bg_bubble_mine)
            tag.visibility = if (item.alert) View.VISIBLE else View.GONE
            text.text = item.text
            time.text = time(itemView.context, item)
            bubble.setOnLongClickListener { listener.onCopy(item); true }
            bindStatus(item)
        }

        fun bindStatus(item: ChatItem) {
            val ctx = itemView.context
            val heard = item.heardBy
            statusIcon.setImageResource(if (heard.isEmpty()) R.drawable.ic_check else R.drawable.ic_check_double)
            status.text = when (heard.size) {
                0 -> ctx.getString(R.string.status_sent)
                1 -> ctx.getString(R.string.status_heard_one, heard.first())
                else -> ctx.getString(R.string.status_heard_many, heard.size)
            }
            bubble.contentDescription = "${item.text}. ${status.text}"
        }
    }

    inner class OtherVH(v: View) : RecyclerView.ViewHolder(v) {
        private val avatar: TextView = v.findViewById(R.id.avatar)
        private val name: TextView = v.findViewById(R.id.name)
        private val bubble: LinearLayout = v.findViewById(R.id.bubble)
        private val tag: TextView = v.findViewById(R.id.tag)
        private val text: TextView = v.findViewById(R.id.text)
        private val time: TextView = v.findViewById(R.id.time)
        private val speaking: ImageView = v.findViewById(R.id.speaking)
        private var pulse: ObjectAnimator? = null

        fun bind(item: ChatItem, prev: ChatItem?) {
            val from = item.from ?: Peer("", "?")
            val color = Avatar.color(from.id)
            // Consecutive messages from the same person are grouped under one name.
            val grouped = prev != null && prev.kind == ChatItem.Kind.OTHER && prev.from?.id == from.id &&
                item.time - prev.time < 120_000
            name.visibility = if (grouped) View.GONE else View.VISIBLE
            avatar.visibility = if (grouped) View.INVISIBLE else View.VISIBLE
            (avatar.layoutParams as ViewGroup.MarginLayoutParams).topMargin =
                if (grouped) 0 else (20 * itemView.resources.displayMetrics.density).toInt()
            name.text = from.name
            name.setTextColor(color)
            avatar.text = Avatar.initials(from.name)
            avatar.backgroundTintList = ColorStateList.valueOf(color)

            bubble.setBackgroundResource(if (item.alert) R.drawable.bg_bubble_other_alert else R.drawable.bg_bubble_other)
            tag.visibility = if (item.alert) View.VISIBLE else View.GONE
            text.text = item.text
            time.text = time(itemView.context, item)
            bubble.contentDescription = "${from.name}: ${item.text}. ${itemView.context.getString(R.string.replay_desc)}"
            bubble.setOnClickListener { listener.onReplay(item) }
            bubble.setOnLongClickListener { listener.onCopy(item); true }
            bindSpeaking(item)
        }

        fun bindSpeaking(item: ChatItem) {
            pulse?.cancel()
            pulse = null
            if (item.speaking) {
                speaking.visibility = View.VISIBLE
                pulse = ObjectAnimator.ofFloat(speaking, View.ALPHA, 1f, 0.25f).apply {
                    duration = 520
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            } else {
                speaking.visibility = View.GONE
                speaking.alpha = 1f
            }
        }
    }

    class SystemVH(v: View) : RecyclerView.ViewHolder(v) {
        private val text: TextView = v.findViewById(R.id.text)
        fun bind(item: ChatItem) { text.text = item.text }
    }

    companion object {
        private const val PAYLOAD_STATUS = "status"
        private const val PAYLOAD_SPEAKING = "speaking"
    }
}
