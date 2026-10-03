# Elden Earth — ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepclassmembers class com.eldenearth.game.MainActivity {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class android.webkit.** { *; }
