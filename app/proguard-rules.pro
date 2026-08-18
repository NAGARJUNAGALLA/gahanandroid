# 1. PRESERVE JAVASCRIPT INTERFACES FOR WEBVIEW
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 2. PRESERVE YOUR NATIVE APP CLASSES
-keep class com.jcv.mocktests.** { *; }

# 3. PRESERVE FIREBASE AND GMS MODELS
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# 4. PRESERVE ANDROIDX BUNDLED CODE (Helps prevent rare crashes)
-keep class androidx.** { *; }
