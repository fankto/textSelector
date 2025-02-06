package com.example.textselector

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CustomFloatingActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FloatingActionButton(context, attrs, defStyleAttr) {
    override fun performClick(): Boolean {
        // Optionally add any custom behavior here.
        return super.performClick()
    }
}
