package com.jcv.mocktests.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsHelper {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        }
    }

    fun logEvent(eventName: String, block: (Bundle.() -> Unit)? = null) {
        val bundle = Bundle()
        block?.invoke(bundle)
        firebaseAnalytics?.logEvent(eventName, bundle)
    }
}
