package com.itantra.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * RadarView — a view-only "who's on the channel" radar.
 *
 * "You" sit at the centre; everyone else is a blip with their initials, spread around the
 * ring. The sweep only animates while the view is on screen, to save battery.
 */
class RadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val brand = ContextCompat.getColor(context, R.color.brand)
    private val ring = ContextCompat.getColor(context, R.color.line)
    private val ink = ContextCompat.getColor(context, R.color.ink)
    private val muted = ContextCompat.getColor(context, R.color.muted)

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private var people: List<Peer> = emptyList()

    fun setPeople(list: List<Peer>) {
        people = list
        contentDescription = if (list.isEmpty()) context.getString(R.string.radar_waiting)
        else list.joinToString { it.name }
        invalidate()
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.2f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweepLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(2f); color = brand }
    private val matrix = Matrix()
    private var shader: SweepGradient? = null
    private var sweep = 0f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val trail = Color.argb(60, Color.red(brand), Color.green(brand), Color.blue(brand))
        shader = SweepGradient(w / 2f, h / 2f, intArrayOf(Color.TRANSPARENT, trail), null)
        sweepPaint.shader = shader
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3600
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (!isVisible) {
            animator?.cancel()
            animator = null
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f - dp(30f)

        ringPaint.color = ring
        for (f in floatArrayOf(1f, 0.66f, 0.33f)) canvas.drawCircle(cx, cy, maxR * f, ringPaint)

        shader?.let {
            matrix.setRotate(sweep, cx, cy)
            it.setLocalMatrix(matrix)
            canvas.drawCircle(cx, cy, maxR, sweepPaint)
        }
        val rad = (sweep - 90.0) * PI / 180.0
        canvas.drawLine(cx, cy, cx + (maxR * cos(rad)).toFloat(), cy + (maxR * sin(rad)).toFloat(), sweepLine)

        fill.color = brand
        canvas.drawCircle(cx, cy, dp(24f), fill)
        text.color = Color.WHITE
        text.textSize = dp(12f)
        text.isFakeBoldText = true
        canvas.drawText(context.getString(R.string.you), cx, cy + dp(4f), text)

        if (people.isEmpty()) {
            text.isFakeBoldText = false
            text.color = muted
            text.textSize = dp(13f)
            canvas.drawText(context.getString(R.string.radar_waiting), cx, cy + maxR + dp(22f), text)
            return
        }

        val r = maxR * 0.74f
        for ((i, p) in people.withIndex()) {
            val ang = (-90.0 + 30.0 + i * (360.0 / people.size)) * PI / 180.0
            val x = cx + (r * cos(ang)).toFloat()
            val y = cy + (r * sin(ang)).toFloat()
            fill.color = Avatar.color(p.id)
            canvas.drawCircle(x, y, dp(16f), fill)
            text.color = Color.WHITE
            text.textSize = dp(12f)
            text.isFakeBoldText = true
            canvas.drawText(Avatar.initials(p.name), x, y + dp(4f), text)
            text.isFakeBoldText = false
            text.color = ink
            text.textSize = dp(12f)
            canvas.drawText(p.name.take(14), x, y + dp(32f), text)
        }
    }
}
