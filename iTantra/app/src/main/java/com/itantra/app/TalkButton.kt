package com.itantra.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * TalkButton — the big hold-to-talk button.
 *
 * Press and hold to record; release to send; slide left to cancel. While recording, a halo
 * follows the speaker's loudness and rings ripple outward. While speech is being turned into
 * text, an arc spins around it. Everything is drawn here, so it costs no images.
 *
 * With TalkBack on, holding is hard, so a double-tap starts recording and another one sends.
 */
class TalkButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    /** LOADING = speech is being prepared (spinner); DISABLED = speech unavailable (grey, still). */
    enum class State { LOADING, DISABLED, IDLE, RECORDING, BUSY }

    interface Listener {
        fun onTalkStart()
        fun onTalkEnd(cancelled: Boolean)
        fun onCancelArmed(armed: Boolean) {}
    }

    var listener: Listener? = null

    var state = State.LOADING
        set(v) {
            if (field == v) return
            field = v
            onStateChanged()
        }

    /** Red instead of blue: the next message goes out as an alert. */
    var alertMode = false
        set(v) {
            if (field == v) return
            field = v
            animateColor()
        }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val brand = ContextCompat.getColor(context, R.color.brand)
    private val brandDeep = ContextCompat.getColor(context, R.color.brand_deep)
    private val alert = ContextCompat.getColor(context, R.color.alert)
    private val alertDeep = ContextCompat.getColor(context, R.color.alert_deep)
    private val disabled = ContextCompat.getColor(context, R.color.disabled)

    private val micIcon = ContextCompat.getDrawable(context, R.drawable.ic_mic)!!.mutate()
    private val closeIcon = ContextCompat.getDrawable(context, R.drawable.ic_close)!!.mutate()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcRect = RectF()

    private var coreColor = disabled
    private var scale = 1f
    private var level = 0f
    private var shownLevel = 0f
    private var ringPhase = 0f
    private var spin = 0f
    private var dragX = 0f
    private var downX = 0f
    private var pressing = false
    private var armed = false
    private val cancelDistance = dp(90f)

    private var colorAnim: ValueAnimator? = null
    private var scaleAnim: ValueAnimator? = null
    private var loopAnim: ValueAnimator? = null

    init {
        isClickable = true
        isFocusable = true
        onStateChanged()
    }

    /** Microphone loudness, 0..1. */
    fun setLevel(v: Float) {
        level = v
        invalidate()
    }

    private fun onStateChanged() {
        loopAnim?.cancel()
        loopAnim = null
        when (state) {
            State.RECORDING -> loopAnim = loop(1400) { ringPhase = it }
            State.BUSY, State.LOADING -> loopAnim = loop(900) { spin = it * 360f }
            State.IDLE, State.DISABLED -> Unit
        }
        if (state != State.RECORDING) {
            level = 0f
            shownLevel = 0f
        }
        animateColor()
        invalidate()
    }

    private fun loop(ms: Long, update: (Float) -> Unit) = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ms
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { update(it.animatedValue as Float); invalidate() }
        start()
    }

    private fun targetColor() = when {
        state == State.LOADING || state == State.DISABLED -> disabled
        alertMode -> if (pressing) alertDeep else alert
        else -> if (pressing) brandDeep else brand
    }

    private fun animateColor() {
        colorAnim?.cancel()
        colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), coreColor, targetColor()).apply {
            duration = 180
            addUpdateListener { coreColor = it.animatedValue as Int; invalidate() }
            start()
        }
    }

    private fun animateScale(to: Float, overshoot: Boolean) {
        scaleAnim?.cancel()
        scaleAnim = ValueAnimator.ofFloat(scale, to).apply {
            duration = if (overshoot) 260 else 200
            interpolator = if (overshoot) OvershootInterpolator(2.2f) else DecelerateInterpolator()
            addUpdateListener { scale = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f + dragX
        val cy = height / 2f
        val maxR = min(width, height) / 2f - dp(2f)
        val coreR = dp(40f) * scale

        if (state == State.RECORDING) {
            // Loudness halo
            shownLevel += (level - shownLevel) * 0.35f
            val haloR = coreR + dp(6f) + shownLevel * (maxR - coreR - dp(6f))
            fill.color = withAlpha(coreColor, 0.18f)
            canvas.drawCircle(cx, cy, haloR, fill)
            // Two ripples, half a cycle apart
            stroke.strokeWidth = dp(2f)
            for (k in 0..1) {
                val p = (ringPhase + k * 0.5f) % 1f
                stroke.color = withAlpha(coreColor, (1f - p) * 0.5f)
                canvas.drawCircle(cx, cy, coreR + p * (maxR - coreR), stroke)
            }
        }

        fill.color = coreColor
        canvas.drawCircle(cx, cy, coreR, fill)

        if (state == State.BUSY || state == State.LOADING) {
            val r = coreR + dp(7f)
            arcRect.set(cx - r, cy - r, cx + r, cy + r)
            stroke.strokeWidth = dp(3f)
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.color = if (state == State.LOADING) disabled else coreColor
            canvas.drawArc(arcRect, spin, 100f, false, stroke)
        }

        val icon = if (armed) closeIcon else micIcon
        val half = dp(17f) * scale
        icon.setTint(Color.WHITE)
        icon.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        icon.draw(canvas)
    }

    private fun withAlpha(color: Int, a: Float) =
        Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (state == State.LOADING || state == State.DISABLED) {
                    shake()
                    listener?.onTalkStart()  // lets the screen explain why nothing happens
                    return true
                }
                if (state != State.IDLE) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                pressing = true
                armed = false
                downX = event.x
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                animateScale(1.14f, overshoot = true)
                animateColor()
                listener?.onTalkStart()
            }
            MotionEvent.ACTION_MOVE -> if (pressing && state == State.RECORDING) {
                dragX = (event.x - downX).coerceIn(-cancelDistance * 1.25f, 0f) * 0.6f
                val nowArmed = event.x - downX <= -cancelDistance
                if (nowArmed != armed) {
                    armed = nowArmed
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    listener?.onCancelArmed(armed)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (pressing) endPress(cancelled = armed)
            MotionEvent.ACTION_CANCEL -> if (pressing) endPress(cancelled = true)
        }
        return true
    }

    private fun endPress(cancelled: Boolean) {
        pressing = false
        armed = false
        animateScale(1f, overshoot = false)
        animateColor()
        if (dragX != 0f) {
            ValueAnimator.ofFloat(dragX, 0f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { dragX = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        val feedback = if (cancelled && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
        else HapticFeedbackConstants.VIRTUAL_KEY
        performHapticFeedback(feedback)
        listener?.onTalkEnd(cancelled)
    }

    /** A small "not yet" shake. */
    fun shake() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360
            addUpdateListener {
                val t = it.animatedValue as Float
                dragX = (kotlin.math.sin(t * Math.PI * 6) * dp(6f) * (1 - t)).toFloat()
                invalidate()
            }
            start()
        }
    }

    // TalkBack users double-tap instead of holding: first tap starts, second sends.
    override fun performClick(): Boolean {
        super.performClick()
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (am.isTouchExplorationEnabled) {
            when (state) {
                State.IDLE -> listener?.onTalkStart()
                State.RECORDING -> listener?.onTalkEnd(false)
                else -> Unit
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        loopAnim?.cancel()
        colorAnim?.cancel()
        scaleAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onStateChanged()
    }
}
