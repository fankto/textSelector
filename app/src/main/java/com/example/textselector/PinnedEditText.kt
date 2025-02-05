package com.example.textselector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.util.regex.Pattern

class PinnedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    // Stores the two boundaries of the current selection.
    private var pinnedStart: Int? = null
    private var pinnedEnd: Int? = null

    // For detecting triple taps.
    private var tapCount = 0
    private var lastTapTime = 0L
    private val tripleTapThreshold = 500L // milliseconds

    // --- New search navigation support ---
    private var searchResults: List<IntRange> = emptyList()
    private var currentSearchIndex: Int = 0

    // Gesture detector for double taps.
    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val offset = getOffsetForPosition(e.x, e.y)
                handleDoubleTap(offset)
                return true
            }
        })

    // Paint used to draw the "PIN" indicator.
    private val pinIndicatorPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gold_primary)
        textSize = 36f  // adjust as needed
        isAntiAlias = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Triple tap detection.
        if (event.action == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < tripleTapThreshold) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = now
            if (tapCount == 3) {
                clearSelectionPins()
                tapCount = 0
                return true
            }
        }
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    /**
     * On a double tap:
     * - If no selection exists, select the tapped word.
     * - Otherwise, update the boundary (start or end) that is closer to the tap.
     */
    private fun handleDoubleTap(offset: Int) {
        val (wordStart, wordEnd) = selectWordAt(offset)
        if (pinnedStart == null || pinnedEnd == null) {
            // First double tap: select the tapped word.
            pinnedStart = wordStart
            pinnedEnd = wordEnd
            setSelection(pinnedStart!!, pinnedEnd!!)
        } else {
            // Subsequent double tap: update the boundary closest to the tapped word.
            val tapMid = (wordStart + wordEnd) / 2
            val distanceToStart = abs(tapMid - pinnedStart!!)
            val distanceToEnd = abs(tapMid - pinnedEnd!!)
            if (distanceToStart <= distanceToEnd) {
                pinnedStart = wordStart
            } else {
                pinnedEnd = wordEnd
            }
            val newStart = min(pinnedStart!!, pinnedEnd!!)
            val newEnd = max(pinnedStart!!, pinnedEnd!!)
            setSelection(newStart, newEnd)
        }
        invalidate() // update the view so the "PIN" indicator is drawn
    }

    /**
     * Clears the stored selection boundaries and resets the native selection.
     */
    fun clearSelectionPins() {
        pinnedStart = null
        pinnedEnd = null
        // Reset native selection by moving the cursor.
        val pos = selectionStart
        setSelection(pos, pos)
        invalidate() // remove any drawn indicators
    }

    /**
     * Returns the full boundaries (start, end) of the word at the given text offset.
     */
    private fun selectWordAt(offset: Int): Pair<Int, Int> {
        val textStr = text?.toString() ?: ""
        if (textStr.isEmpty()) return Pair(0, 0)
        var start = offset
        var end = offset
        while (start > 0 && !textStr[start - 1].isWhitespace()) {
            start--
        }
        while (end < textStr.length && !textStr[end].isWhitespace()) {
            end++
        }
        return Pair(start, end)
    }

    // --- Search Functionality (with a small change to preserve pinned state) ---

    /**
     * Highlights all occurrences of [query] in the text by applying a background color span.
     * Ensure that R.color.searchHighlight is defined in your colors.xml.
     */
    fun highlightSearch(query: String) {
        val originalText = text?.toString() ?: return
        val spannable = SpannableString(originalText)
        if (query.isNotEmpty()) {
            // Use a case-insensitive search.
            val regex = Regex(Pattern.quote(query), RegexOption.IGNORE_CASE)
            regex.findAll(originalText).forEach { matchResult ->
                spannable.setSpan(
                    BackgroundColorSpan(ContextCompat.getColor(context, R.color.searchHighlight)),
                    matchResult.range.first,
                    matchResult.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        setText(spannable)
        // Reapply any existing pinned selection (if still valid)
        if (pinnedStart != null && pinnedEnd != null) {
            setSelection(pinnedStart!!, pinnedEnd!!)
        }
    }

    /**
     * Clears any search highlights by resetting the text.
     */
    fun clearHighlights() {
        val plainText = text?.toString() ?: ""
        setText(plainText)
        // Reapply selection if needed
        if (pinnedStart != null && pinnedEnd != null) {
            setSelection(pinnedStart!!, pinnedEnd!!)
        }
    }

    fun nextSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
            val range = searchResults[currentSearchIndex]
            setSelection(range.first, range.last)
        }
    }

    fun previousSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = if (currentSearchIndex - 1 < 0) searchResults.size - 1 else currentSearchIndex - 1
            val range = searchResults[currentSearchIndex]
            setSelection(range.first, range.last)
        }
    }

    fun getSearchResultsCount(): Int = searchResults.size

    fun getCurrentSearchIndex(): Int = if (searchResults.isNotEmpty()) currentSearchIndex + 1 else 0

    /**
     * Draws a small "PIN" indicator above the start of the pinned selection.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pinnedStart != null && pinnedEnd != null) {
            // Calculate a fixed position (e.g., bottom left with some padding)
            val leftPadding = compoundPaddingLeft.toFloat()
            val bottomPadding = (height - compoundPaddingBottom).toFloat()
            // Offset the text a little inside the view (adjust 16 as needed)
            canvas.drawText("PIN", leftPadding + 16, bottomPadding - 16, pinIndicatorPaint)
        }
    }
}
