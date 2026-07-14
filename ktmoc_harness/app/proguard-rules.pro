# Add project specific ProGuard rules here.
# F-Droid compatible configuration

# Keep JavaScript interfaces
-keepclassmembers class com.ktmoc.harness.KtmocHarnessActivity$* {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView-related classes
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# USB Serial
-keep class com.hoho.android.usbserial.** { *; }

# Keep JSON classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn org.json.**

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
