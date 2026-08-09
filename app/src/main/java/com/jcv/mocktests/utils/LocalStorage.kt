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
    // ADD THIS to save the score after an exam
    fun saveTestScore(courseId: String, testName: String, score: Int, maxScore: Int) {
        val prefs = context.getSharedPreferences("JcvAppPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("score_${courseId}_${testName}", score)
            .putInt("max_${courseId}_${testName}", maxScore)
            .putBoolean("attempted_${courseId}_${testName}", true)
            .apply()
    }

    // ADD THIS to retrieve the score for the UI
    fun getTestScore(courseId: String, testName: String): Pair<Int, Int>? {
        val prefs = context.getSharedPreferences("JcvAppPrefs", Context.MODE_PRIVATE)
        val score = prefs.getInt("score_${courseId}_${testName}", -1)
        val max = prefs.getInt("max_${courseId}_${testName}", -1)
        return if (score != -1) Pair(score, max) else null
    }
}
