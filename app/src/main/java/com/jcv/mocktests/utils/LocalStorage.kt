package com.jcv.mocktests.utils

import android.content.Context
import android.content.SharedPreferences

class LocalStorage(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("JcvAppPrefs", Context.MODE_PRIVATE)

    // ---------------------------------------------------------
    // NEW: DEVICE ID (For Single-Device Login)
    // ---------------------------------------------------------
    fun getOrCreateDeviceId(): String {
        var deviceId = prefs.getString("unique_device_id", null)
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("unique_device_id", deviceId).apply()
        }
        return deviceId
    }

    // ---------------------------------------------------------
    // NEW: DARK MODE PREFERENCES 
    // ---------------------------------------------------------
    fun setDarkMode(isDark: Boolean) {
        prefs.edit().putBoolean("is_dark_mode", isDark).apply()
    }

    fun isDarkMode(systemDefault: Boolean): Boolean {
        return prefs.getBoolean("is_dark_mode", systemDefault)
    }

    // ---------------------------------------------------------
    // EXISTING: TEST SCORE LOGIC
    // ---------------------------------------------------------
    fun markTestAsAttempted(courseId: String, testName: String) {
        val key = "attempted_${courseId}_${testName}"
        prefs.edit().putBoolean(key, true).apply()
    }

    fun isTestAttempted(courseId: String, testName: String): Boolean {
        val oldKey = "${courseId}_${testName}_completed"
        val newKey = "attempted_${courseId}_${testName}"
        return prefs.getBoolean(oldKey, false) || prefs.getBoolean(newKey, false)
    }

    // SAVES AS A FLOAT (DECIMAL)
    fun saveTestScore(courseId: String, testName: String, score: Float, maxScore: Float) {
        prefs.edit()
            .putFloat("score_${courseId}_${testName}", score)
            .putFloat("max_${courseId}_${testName}", maxScore)
            .putBoolean("attempted_${courseId}_${testName}", true)
            .apply()
    }

    // SAFELY RETRIEVES FLOAT (Prevents crashes from older integer scores)
    fun getTestScore(courseId: String, testName: String): Pair<Float, Float>? {
        var score = -1f
        var max = -1f
        
        try {
            score = prefs.getFloat("score_${courseId}_${testName}", -1f)
            max = prefs.getFloat("max_${courseId}_${testName}", -1f)
        } catch (e: ClassCastException) {
            // Fallback just in case the old score was saved as an Int
            score = prefs.getInt("score_${courseId}_${testName}", -1).toFloat()
            max = prefs.getInt("max_${courseId}_${testName}", -1).toFloat()
        }
        
        return if (score != -1f) Pair(score, max) else null
    }
}
