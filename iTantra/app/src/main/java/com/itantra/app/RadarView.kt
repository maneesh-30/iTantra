package com.itantra.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * RadarView — a view-only "who's in the room" radar.
 *
 * Draws concentric rings with "You" at the centre and everyone we've heard from placed as
 * blips around the ring. It does not handle any input; the Talk screen is where you speak.
 * Call [setPeople] with the list of other people's names to update it.
 */
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val amber = Color.parseColor("#F5A623")
    private val onAmber = Color.parseColor("#241A05")
    private val blip = Color.parseColor("#FFD68A")
    private val ring = Color.parseColor("#2A281E")
    private val ring2 = Color.parseColor("#1E1B12")
    private val ink = Color.parseColor("#F0EDE3")
    private val muted = Color.parseColor("#8A8474")

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private var people: List<String> = emptyList()

    fun setPeople(names: List<String>) {
        people = names
        invalidate()
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f - dp(28f)

        // rings
        ringPaint.color = ring2
        canvas.drawCircle(cx, cy, maxR, ringPaint)
        ringPaint.color = ring
        canvas.drawCircle(cx, cy, maxR * 0.62f, ringPaint)
        canvas.drawCircle(cx, cy, maxR * 0.32f, ringPaint)

        // centre = You
        fillPaint.color = amber
        canvas.drawCircle(cx, cy, dp(26f), fillPaint)
        textPaint.color = onAmber
        textPaint.textSize = dp(12f)
        textPaint.isFakeBoldText = true
        canvas.drawText("You", cx, cy + dp(4f), textPaint)
        textPaint.isFakeBoldText = false

        if (people.isEmpty()) {
            textPaint.color = muted
            textPaint.textSize = dp(13f)
            canvas.drawText("Waiting for others to join…", cx, cy + maxR + dp(20f), textPaint)
            return
        }

        // blips around the ring
        val r = maxR * 0.72f
        val n = people.size
        for (i in 0 until n) {
            val ang = (-90.0 + i * (360.0 / n)) * PI / 180.0
            val x = cx + (r * cos(ang)).toFloat()
            val y = cy + (r * sin(ang)).toFloat()
            fillPaint.color = blip
            canvas.drawCircle(x, y, dp(10f), fillPaint)
            textPaint.color = ink
            textPaint.textSize = dp(12f)
            canvas.drawText(people[i], x, y + dp(24f), textPaint)
        }
    }
}
