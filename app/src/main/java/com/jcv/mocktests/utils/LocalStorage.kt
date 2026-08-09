package com.jcv.mocktests.utils

import android.content.Context
import android.content.SharedPreferences

class LocalStorage(private val context: Context) {
    
    // We create ONE consistent preferences file for the whole app
    private val prefs: SharedPreferences = context.getSharedPreferences("JcvAppPrefs", Context.MODE_PRIVATE)

    // Save that a specific test was completed
    fun markTestAsAttempted(courseId: String, testName: String) {
        val key = "attempted_${courseId}_${testName}"
        prefs.edit().putBoolean(key, true).apply()
    }

    // Check if a specific test was already completed
    fun isTestAttempted(courseId: String, testName: String): Boolean {
        // Checks both the old and new key styles so students don't lose past progress
        val oldKey = "${courseId}_${testName}_completed"
        val newKey = "attempted_${courseId}_${testName}"
        return prefs.getBoolean(oldKey, false) || prefs.getBoolean(newKey, false)
    }

    // Save the score after an exam
    fun saveTestScore(courseId: String, testName: String, score: Int, maxScore: Int) {
        prefs.edit()
            .putInt("score_${courseId}_${testName}", score)
            .putInt("max_${courseId}_${testName}", maxScore)
            .putBoolean("attempted_${courseId}_${testName}", true)
            .apply()
    }

    // Retrieve the score for the UI
    fun getTestScore(courseId: String, testName: String): Pair<Int, Int>? {
        val score = prefs.getInt("score_${courseId}_${testName}", -1)
        val max = prefs.getInt("max_${courseId}_${testName}", -1)
        return if (score != -1) Pair(score, max) else null
    }
}
