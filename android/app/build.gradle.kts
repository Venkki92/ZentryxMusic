import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val currentVersionCode = (versionProps.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1).coerceAtLeast(1)
val appVersionName = versionProps.getProperty("VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() } ?: "0.0.1"

val isApkBuildRequested = gradle.startParameter.taskNames.any { task ->
    task.contains("assemble", ignoreCase = true) ||
        task.contains("bundle", ignoreCase = true) ||
        task.contains("package", ignoreCase = true) ||
        task.contains("install", ignoreCase = true)
}

val appVersionCode = if (isApkBuildRequested) {
    val nextCode = currentVersionCode + 1
    versionPropsFile.writeText("VERSION_CODE=$nextCode\nVERSION_NAME=$appVersionName\n")
    nextCode
} else {
    currentVersionCode
}

android {
    namespace = "com.zentryx.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zentryx.music"
        minSdk = 26       // Android 8.0 Oreo
        targetSdk = 34    // Compatible with modern Android standards while tested against API 28 (Pie)

        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AutoChips AC8259P (ARM Cortex-A53) headunit ABI targets:
        // Excludes x86/x86_64 binaries, shrinking APK size well under the 20MB budget
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }

        // Vector drawables support for low-DPI headunit screen (1024x600 mdpi)
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Allows direct installation onto userdebug headunits
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        // Enable core library desugaring so modern java.time and streams work on Android 9 (API 28)
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/**"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // Core library desugaring for API 28 backward compatibility
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose BOM & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Media3 (ExoPlayer & MediaSessionService)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media)

    // Jetpack Car App Library (Android Auto)
    implementation(libs.androidx.car.app)

    // Room Database & FTS
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Networking (OkHttp for WebDAV and direct streaming)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coil Image Loading (Disk-cached album artwork, lightweight)
    implementation(libs.coil.compose)

    // WorkManager (Background library scanning)
    implementation(libs.androidx.work.runtime.ktx)

    // Security & DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.documentfile)

    // Unit Tests
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
