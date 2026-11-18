# ─────────────────────────────────────────────────────────────────────────────
# Project-Specific ProGuard / R8 Obfuscation & APK Shrinking Rules for HomeMonitor
# ─────────────────────────────────────────────────────────────────────────────

# Optimization & Code Shrinking Options
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-allowaccessmodification
-repackageclasses ''

# Stacktrace Retracing & Attribute Retention
-renamesourcefileattribute ""
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod,Signature,*Annotation*

# Preserve org.json library structures used by dynamic API payloads
-keep class org.json.** { *; }

# Anti-Tamper & Security Hardening Support
# Ignore missing optional Xposed framework class checked via reflection in TamperProtectionManager
-dontwarn de.robv.android.xposed.**

# Third-Party Library Warning Suppression
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
