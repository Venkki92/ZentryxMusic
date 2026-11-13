# R8 / ProGuard Configuration for Zentryx Music
# Optimized for AutoChips AC8259P / Android 9 (API 28) Headunit

# Keep Domain Models used in Room & Navigation
-keep class com.zentryx.music.models.** { *; }

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keepattributes *Annotation*

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class androidx.room.paging.** { *; }

# Hilt & Dagger
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * extends dagger.hilt.internal.GeneratedComponentBuilder
-keep class * extends dagger.hilt.android.internal.builders.*
-keep class * extends dagger.hilt.android.internal.modules.*
-dontwarn dagger.hilt.**

# OkHttp & WebDAV
-keepattributes Signature
-keepattributes InnerClasses
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Android 9 (API 28) Headunit Hardware & KeyEvents
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
