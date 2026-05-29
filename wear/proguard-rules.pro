# Custom Proguard rules for the Wear OS module

# Strip debug and verbose logs in release build types
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Kotlin Serialization Keep Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,allowobfuscation class * implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers class * {
    *** Companion;
}

# Health Services & Wearable Client Keep Rules
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.wearable.**
-keep class androidx.health.services.** { *; }
-dontwarn androidx.health.services.**
