// app/src/main/java/com/example/textselector/PinnedEditText.kt
package com.example.textselector

import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.widget.Button
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max
import kotlin.math.min

class PinnedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    // Pinned boundaries (null means not yet set)
    var pinnedStart: Int? = null
    var pinnedEnd: Int? = null

    // Detect double-taps.
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val offset = getOffsetForPosition(e.x, e.y)
            // If both pins are set, reset to start a new selection.
            if (pinnedStart != null && pinnedEnd != null) {
                pinnedStart = null
                pinnedEnd = null
                setSelection(offset)  // clear selection
            }
            showPinPopup(e.x, e.y, offset)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    /**
     * Displays a popup near the tapped position.
     * Depending on the current pin state, it shows:
     *  - Only "Pin Start" (if no start pin exists)
     *  - "Unpin Start" and "Pin End" (if start is set but not end)
     */
    private fun showPinPopup(tapX: Float, tapY: Float, offset: Int) {
        // Inflate the popup layout defined in res/layout/popup_pin_options.xml
        val popupView = LayoutInflater.from(context).inflate(R.layout.popup_pin_options, null)
        val buttonOption1 = popupView.findViewById<Button>(R.id.buttonOption1)
        val buttonOption2 = popupView.findViewById<Button>(R.id.buttonOption2)

        // Create the PopupWindow.
        val popupWindow = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.isOutsideTouchable = true
        // A background drawable is needed for outside taps to dismiss the popup.
        popupWindow.setBackgroundDrawable(context.getDrawable(android.R.drawable.alert_light_frame))

        if (pinnedStart == null) {
            // No start pin: show only the "Pin Start" option.
            buttonOption1.text = "Pin Start"
            buttonOption1.setOnClickListener {
                val (wordStart, wordEnd) = selectWordAt(offset)
                pinnedStart = wordStart
                // Select the tapped word.
                setSelection(wordStart, wordEnd)
                popupWindow.dismiss()
            }
            buttonOption2.visibility = View.GONE
        } else if (pinnedStart != null && pinnedEnd == null) {
            // Start pin exists; now allow unpinning or pinning the end.
            buttonOption1.text = "Unpin Start"
            buttonOption1.setOnClickListener {
                pinnedStart = null
                setSelection(offset)  // clear selection
                popupWindow.dismiss()
            }
            buttonOption2.visibility = View.VISIBLE
            buttonOption2.text = "Pin End"
            buttonOption2.setOnClickListener {
                val (wordStart, wordEnd) = selectWordAt(offset)
                // Use the word's end offset so that the tapped word is fully selected.
                pinnedEnd = wordEnd
                // Select all text between the start and end pins.
                val selStart = min(pinnedStart!!, pinnedEnd!!)
                val selEnd = max(pinnedStart!!, pinnedEnd!!)
                setSelection(selStart, selEnd)
                popupWindow.dismiss()
            }
        }

        // Calculate absolute screen coordinates.
        val location = IntArray(2)
        getLocationOnScreen(location)
        val anchorX = location[0] + tapX.toInt()
        val anchorY = location[1] + tapY.toInt()
        popupWindow.showAtLocation(this, Gravity.NO_GRAVITY, anchorX, anchorY)
    }

    /** Determines the word boundaries (start, end) for the word at the given offset. */
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
}