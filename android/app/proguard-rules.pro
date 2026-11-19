# R8 / ProGuard Configuration for Zentryx Music
# Optimized for AutoChips AC8259P / Android 9 (API 28) Headunit

# Keep Domain Models, DSP, Auto, and Visualizer classes used in Room, AudioFx, CarApp & Navigation
-keep class com.zentryx.music.models.** { *; }
-keep class com.zentryx.music.dsp.** { *; }
-keep class com.zentryx.music.visualizer.** { *; }
-keep class com.zentryx.music.auto.** { *; }

# Jetpack Car App Library (Android Auto)
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

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

# WorkManager, Scanner Workers & Assisted Factories
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep class com.zentryx.music.scanner.** { *; }
-keep class com.zentryx.music.ZentryxWorkerFactory { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Android 9 (API 28) Headunit Hardware & KeyEvents
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
