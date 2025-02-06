package com.example.textselector

import android.util.Log

object Logger {
    private const val DEBUG = false

    fun d(tag: String, message: String) {
        if (DEBUG) {
            Log.d(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (DEBUG) {
            Log.e(tag, message, throwable)
        }
    }

    fun w(tag: String, message: String) {
        if (DEBUG) {
            Log.w(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (DEBUG) {
            Log.i(tag, message)
        }
    }
}