# ─────────────────────────────────────────────
# Weather Calendar — ProGuard / R8 Rules
# ─────────────────────────────────────────────

# ── Retrofit ──
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Kotlinx Serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes and their serializers
-keep,includedescriptorclasses class com.weathercalendar.**$$serializer { *; }
-keepclassmembers class com.weathercalendar.** {
    *** Companion;
}
-keepclasseswithmembers class com.weathercalendar.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── API response models (must not be obfuscated) ──
-keep class com.weathercalendar.data.remote.** { *; }
-keep class com.weathercalendar.data.repository.SavedCity { *; }

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Coroutines ──
-dontwarn kotlinx.coroutines.**

# ── Google Play Services ──
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── General Android ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
