package com.example.textselector.ui

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.example.textselector.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PinnedEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private var pinnedStart: Int? = null
    private var pinnedEnd: Int? = null

    private var tapCount = 0
    private var lastTapTime = 0L
    private val tripleTapThreshold = 500L

    private var searchResults: List<IntRange> = emptyList()
    private var currentSearchIndex = 0

    private var ignoreBringPointIntoView = false

    var onPinChanged: (() -> Unit)? = null
    var onSearchCleared: (() -> Unit)? = null
    var selectionChangeListener: ((Int, Int) -> Unit)? = null

    init {
        if (text !is Editable) setText(text?.toString() ?: "")
        isLongClickable = false
    }

    override fun bringPointIntoView(offset: Int): Boolean {
        return if (ignoreBringPointIntoView) false else super.bringPointIntoView(offset)
    }

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val offset = getOffsetForPosition(e.x, e.y)
                handleDoubleTap(offset)
                return true
            }
        })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < tripleTapThreshold) {
                tapCount++
            } else {
                tapCount = 1
            }
            lastTapTime = currentTime

            if (tapCount == 3) {
                clearSelectionPins()
                tapCount = 0
                return true
            }
        }
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private fun handleDoubleTap(offset: Int) {
        val (wordStart, wordEnd) = getWordBoundaries(offset)
        if (pinnedStart == null || pinnedEnd == null) {
            pinnedStart = wordStart
            pinnedEnd = wordEnd
        } else {
            if (wordEnd <= pinnedStart!!) {
                pinnedStart = wordStart
            } else if (wordStart >= pinnedEnd!!) {
                pinnedEnd = wordEnd
            } else {
                val diffStart = abs(wordStart - pinnedStart!!)
                val diffEnd = abs(wordEnd - pinnedEnd!!)
                if (diffStart < diffEnd) pinnedStart = wordStart else pinnedEnd = wordEnd
            }
        }
        val start = min(pinnedStart!!, pinnedEnd!!)
        val end = max(pinnedStart!!, pinnedEnd!!)

        // Disable auto-scroll
        ignoreBringPointIntoView = true
        setSelection(start, end)
        postDelayed({ ignoreBringPointIntoView = false }, 50)

        onPinChanged?.invoke()
    }

    private fun getWordBoundaries(offset: Int): Pair<Int, Int> {
        val content = text?.toString() ?: ""
        if (content.isEmpty()) return 0 to 0
        var start = offset
        var end = offset
        while (start > 0 && !content[start - 1].isWhitespace()) start--
        while (end < content.length && !content[end].isWhitespace()) end++
        return start to end
    }

    fun clearSelectionPins() {
        pinnedStart = null
        pinnedEnd = null
        setSelection(selectionStart, selectionStart)
        onPinChanged?.invoke()
    }

    fun updateSearch(query: String) {
        val editable = text ?: return
        clearSearchHighlights(invokeCallback = false)
        if (query.isEmpty()) return

        val highlightColor = ContextCompat.getColor(context, R.color.searchHighlight)
        val regex = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        val matches = regex.findAll(editable.toString()).toList()
        matches.forEach { match ->
            editable.setSpan(
                BackgroundColorSpan(highlightColor),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        searchResults = matches.map { it.range }
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = 0
            val range = searchResults[0]
            setSelection(range.first, range.last + 1)
        }
    }

    fun clearSearchHighlights(invokeCallback: Boolean = true) {
        val editable = text ?: return
        val highlightColor = ContextCompat.getColor(context, R.color.searchHighlight)
        editable.getSpans(0, editable.length, BackgroundColorSpan::class.java)
            .filter { it.backgroundColor == highlightColor }
            .forEach { editable.removeSpan(it) }
        searchResults = emptyList()
        if (invokeCallback) onSearchCleared?.invoke()
    }

    fun nextSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
            val range = searchResults[currentSearchIndex]
            setSelection(range.first, range.last + 1)
            bringPointIntoView(range.first)
        }
    }

    fun previousSearchResult() {
        if (searchResults.isNotEmpty()) {
            currentSearchIndex =
                if (currentSearchIndex - 1 < 0) searchResults.size - 1 else currentSearchIndex - 1
            val range = searchResults[currentSearchIndex]
            setSelection(range.first, range.last + 1)
            bringPointIntoView(range.first)
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        selectionChangeListener?.invoke(selStart, selEnd)
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return SavedState(superState).apply {
            pinnedStart = this@PinnedEditText.pinnedStart ?: -1
            pinnedEnd = this@PinnedEditText.pinnedEnd ?: -1
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        pinnedStart = if (state.pinnedStart != -1) state.pinnedStart else null
        pinnedEnd = if (state.pinnedEnd != -1) state.pinnedEnd else null
        if (pinnedStart != null && pinnedEnd != null) {
            setSelection(min(pinnedStart!!, pinnedEnd!!), max(pinnedStart!!, pinnedEnd!!))
        }
    }

    internal class SavedState : BaseSavedState {
        var pinnedStart: Int = -1
        var pinnedEnd: Int = -1

        constructor(superState: Parcelable?) : super(superState)
        private constructor(parcel: Parcel) : super(parcel) {
            pinnedStart = parcel.readInt()
            pinnedEnd = parcel.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(pinnedStart)
            out.writeInt(pinnedEnd)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState = SavedState(parcel)
            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }

    // Expose search result data for external use.
    val getSearchResultsCount: () -> Int = { searchResults.size }
    val getCurrentSearchIndex: () -> Int = { currentSearchIndex + 1 }
}
