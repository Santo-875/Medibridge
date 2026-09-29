plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.medibridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medibridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-hackathon-shell"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val envFile = rootProject.file(".env")
        var envBackendUrl = "http://10.0.2.2:8000/"
        var envGeminiKey = ""
        if (envFile.exists()) {
            envFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("BACKEND_URL=")) {
                    val url = trimmed.substringAfter("=").trim().trim('"', '\'')
                    if (url.isNotEmpty()) {
                        envBackendUrl = if (url.endsWith("/")) url else "$url/"
                    }
                }
                if (trimmed.startsWith("GEMINI_API_KEY=") && !trimmed.startsWith("#")) {
                    val key = trimmed.substringAfter("GEMINI_API_KEY=").trim().trim('"', '\'')
                    if (key.isNotBlank() && key != "your_gemini_api_key_here") {
                        envGeminiKey = key
                    }
                }
            }
        }
        buildConfigField("String", "BACKEND_BASE_URL", "\"$envBackendUrl\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$envGeminiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM — controls all compose lib versions in sync
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Room DB (KSP annotation processor)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coil — async image loading
    implementation(libs.coil.compose)

    // Retrofit + OkHttp (wired for future AI API calls — Modules A/B/C/D will implement)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Gson for JSON serialization (used in MedicationEntity JSON fields)
    implementation(libs.gson)

    // ViewModel + StateFlow
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // WorkManager — periodic missed-dose detection (Module C)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // CameraX for Module A OCR/Bill Scanner
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
