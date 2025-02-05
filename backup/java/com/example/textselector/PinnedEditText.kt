package com.example.textselector

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class PinnedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    // Pinned boundaries
    var pinnedStart: Int? = null
    var pinnedEnd: Int? = null

    // Pin markers
    private var startMarkerAlpha = 0f
    private var endMarkerAlpha = 0f
    private val markerPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gold_primary)
    }
    private val markerWidth = 4f // dp
    private val markerWidthPx = context.resources.displayMetrics.density * markerWidth

    // Selection overlay colors
    private val selectionPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gold_primary)
        alpha = 50 // Semi-transparent
    }

    // Custom selection handles
    init {
        textSelectHandle = ContextCompat.getDrawable(context, R.drawable.selection_handle)
        textSelectHandleLeft = textSelectHandle
        textSelectHandleRight = textSelectHandle
    }

    // Detect gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val offset = getOffsetForPosition(e.x, e.y)
            if (pinnedStart != null && pinnedEnd != null) {
                clearPins()
            } else {
                handleDoubleTap(e.x, e.y, offset)
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val offset = getOffsetForPosition(e.x, e.y)
            handleLongPress(e.x, e.y, offset)
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun handleDoubleTap(x: Float, y: Float, offset: Int) {
        val (wordStart, wordEnd) = selectWordAt(offset)
        if (pinnedStart == null) {
            setPinStart(wordStart)
        } else if (pinnedEnd == null) {
            setPinEnd(wordEnd)
        }
    }

    private fun handleLongPress(x: Float, y: Float, offset: Int) {
        if (pinnedStart == null || pinnedEnd == null) {
            val (wordStart, wordEnd) = selectWordAt(offset)
            if (pinnedStart == null) {
                setPinStart(wordStart)
            } else {
                setPinEnd(wordEnd)
            }
        }
    }

    private fun setPinStart(position: Int) {
        pinnedStart = position
        animateMarker(true)
        setSelection(position)
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun setPinEnd(position: Int) {
        pinnedEnd = position
        animateMarker(false)
        setSelectionRange()
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun clearPins() {
        pinnedStart = null
        pinnedEnd = null
        animateMarkersOut()
        clearSelection()
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun animateMarker(isStart: Boolean) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                if (isStart) {
                    startMarkerAlpha = animator.animatedValue as Float
                } else {
                    endMarkerAlpha = animator.animatedValue as Float
                }
                invalidate()
            }
        }.start()
    }

    private fun animateMarkersOut() {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                startMarkerAlpha = animator.animatedValue as Float
                endMarkerAlpha = animator.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw pin markers
        pinnedStart?.let { start ->
            val startX = layout.getPrimaryHorizontal(start)
            markerPaint.alpha = (startMarkerAlpha * 255).toInt()
            canvas.drawRect(
                startX - markerWidthPx / 2,
                0f,
                startX + markerWidthPx / 2,
                height.toFloat(),
                markerPaint
            )
        }

        pinnedEnd?.let { end ->
            val endX = layout.getPrimaryHorizontal(end)
            markerPaint.alpha = (endMarkerAlpha * 255).toInt()
            canvas.drawRect(
                endX - markerWidthPx / 2,
                0f,
                endX + markerWidthPx / 2,
                height.toFloat(),
                markerPaint
            )
        }
    }

    private fun setSelectionRange() {
        if (pinnedStart != null && pinnedEnd != null) {
            val start = min(pinnedStart!!, pinnedEnd!!)
            val end = max(pinnedStart!!, pinnedEnd!!)
            setSelection(start, end)
        }
    }

    private fun selectWordAt(offset: Int): Pair<Int, Int> {
        val text = text?.toString() ?: return Pair(0, 0)
        if (text.isEmpty()) return Pair(0, 0)

        var start = offset
        var end = offset

        // Find word boundaries
        while (start > 0 && !text[start - 1].isWhitespace()) {
            start--
        }
        while (end < text.length && !text[end].isWhitespace()) {
            end++
        }

        return Pair(start, end)
    }
}