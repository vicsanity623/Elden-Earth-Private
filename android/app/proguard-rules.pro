# Elden Earth — ProGuard Rules

-keepattributes Signature
-keepattributes *Annotation*

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep JavaScript interface
-keepclassmembers class com.eldenearth.game.MainActivity {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView
-keep class android.webkit.** { *; }
