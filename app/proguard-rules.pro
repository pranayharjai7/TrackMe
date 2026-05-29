# Custom Proguard rules for the app module

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

# Ktor Keep Rules
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Supabase Kotlin SDK Keep Rules
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# Hilt Keep Rules
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * implements dagger.hilt.internal.UnsafeCasts { *; }
-keep class * extends javax.inject.Provider { *; }
-keep class * implements javax.inject.Provider { *; }

# Room Keep Rules
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
