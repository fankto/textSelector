package com.example.textselector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
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

    var selectionChangeListener: ((Int, Int) -> Unit)? = null
    var onSearchCleared: (() -> Unit)? = null
    fun isPinActive(): Boolean = pinnedStart != null && pinnedEnd != null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        selectionChangeListener?.invoke(selStart, selEnd)
    }

    private fun handleDoubleTap(offset: Int) {
        // Always clear search UI when double-tapping.
        if (searchResults.isNotEmpty()) {
            clearSearchHighlights()
        } else {
            onSearchCleared?.invoke()
        }

        val (wordStart, wordEnd) = selectWordAt(offset)
        if (pinnedStart == null || pinnedEnd == null) {
            pinnedStart = wordStart
            pinnedEnd = wordEnd
            setSelection(pinnedStart!!, pinnedEnd!!)
        } else {
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
        invalidate()
    }

    fun clearSelectionPins() {
        pinnedStart = null
        pinnedEnd = null
        val pos = selectionStart
        setSelection(pos, pos)
        invalidate()
        onSearchCleared?.invoke()
    }

    fun clearSearchHighlights(invokeCallback: Boolean = true) {
        val editable = text ?: return
        val searchHighlightColor = ContextCompat.getColor(context, R.color.searchHighlight)
        val spans = editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)
        spans.forEach { span ->
            if (span.backgroundColor == searchHighlightColor) {
                editable.removeSpan(span)
            }
        }
        searchResults = emptyList()
        if (pinnedStart != null && pinnedEnd != null) {
            setSelection(pinnedStart!!, pinnedEnd!!)
        }
        invalidate()
        if (invokeCallback) {
            onSearchCleared?.invoke()
        }
    }

    init {
        if (text !is Editable) {
            Log.d("PinnedEditText", "Converting text to Editable")
            setText(Editable.Factory.getInstance().newEditable(text))
        }
    }



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

    fun getSearchResultsCount(): Int = searchResults.size

    fun getCurrentSearchIndex(): Int = if (searchResults.isNotEmpty()) currentSearchIndex + 1 else 0

    fun updateSearch(query: String) {
        Log.d("PinnedEditText", "updateSearch called with query: '$query'")

        val editable = editableText
        if (editable == null) {
            Log.e("PinnedEditText", "Text is not editable!")
            return
        }
        Log.d("PinnedEditText", "Current text content: '${editable.toString()}'")

        clearSearchHighlights(invokeCallback = false)
        Log.d("PinnedEditText", "Cleared previous highlights")

        if (query.isEmpty()) {
            Log.d("PinnedEditText", "Empty query, returning")
            return
        }

        val searchHighlightColor = ContextCompat.getColor(context, R.color.searchHighlight)
        Log.d("PinnedEditText", "Search highlight color: $searchHighlightColor")

        try {
            val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
            Log.d("PinnedEditText", "Created regex: ${regex.pattern}")

            val text = editable.toString()
            val matches = regex.findAll(text).toList()
            Log.d("PinnedEditText", "Found ${matches.size} matches")

            matches.forEach { match ->
                Log.d("PinnedEditText", "Match at ${match.range}: '${match.value}'")
                editable.setSpan(
                    BackgroundColorSpan(searchHighlightColor),
                    match.range.first,
                    match.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            searchResults = matches.map { it.range }
            Log.d("PinnedEditText", "Set searchResults to ${searchResults.size} ranges")

            if (searchResults.isNotEmpty()) {
                currentSearchIndex = 0
                val firstRange = searchResults[0]
                Log.d("PinnedEditText", "Selecting first match: $firstRange")
                setSelection(firstRange.first, firstRange.last + 1)
            }

            postInvalidate()
            Log.d("PinnedEditText", "Search update complete")

        } catch (e: Exception) {
            Log.e("PinnedEditText", "Search failed", e)
            e.printStackTrace()
        }
    }

    fun nextSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
            val range = searchResults[currentSearchIndex]
            setSelection(range.first, range.last + 1)
        }
    }

    fun previousSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = if (currentSearchIndex - 1 < 0) searchResults.size - 1 else currentSearchIndex - 1
            val range = searchResults[currentSearchIndex]
            setSelection(range.first, range.last + 1)
        }
    }
}
