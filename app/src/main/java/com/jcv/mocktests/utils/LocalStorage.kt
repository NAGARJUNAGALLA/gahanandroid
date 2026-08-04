package com.jcv.mocktests.utils

import android.content.Context

class LocalStorage(context: Context) {
    // This creates a private preferences file named "JcvCbtPrefs"
    private val prefs = context.getSharedPreferences("JcvCbtPrefs", Context.MODE_PRIVATE)

    // Save that a specific test was completed
    fun markTestAsAttempted(courseId: String, testName: String) {
        val key = "${courseId}_${testName}_completed"
        prefs.edit().putBoolean(key, true).apply()
    }

    // Check if a specific test was already completed
    fun isTestAttempted(courseId: String, testName: String): Boolean {
        val key = "${courseId}_${testName}_completed"
        return prefs.getBoolean(key, false)
    }
}
