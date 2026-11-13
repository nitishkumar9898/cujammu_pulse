import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val configuredReleaseApiBaseUrl = providers.gradleProperty("cujammuApiBaseUrl").orNull?.trim().orEmpty()
if (configuredReleaseApiBaseUrl.isNotEmpty()) {
    val releaseApiUri = runCatching { URI(configuredReleaseApiBaseUrl) }
        .getOrElse { throw GradleException("cujammuApiBaseUrl must be an absolute HTTPS URL") }
    if (releaseApiUri.scheme != "https" || releaseApiUri.host.isNullOrBlank()
        || releaseApiUri.userInfo != null || releaseApiUri.query != null || releaseApiUri.fragment != null
        || !configuredReleaseApiBaseUrl.endsWith("/")) {
        throw GradleException("cujammuApiBaseUrl must be an absolute HTTPS base URL ending in / with no credentials, query, or fragment")
    }
}
// Kept as a standalone diagnostic target; artifact tasks enforce the guard themselves.
tasks.register("validateProductionApiEndpoint") {
    doLast {
        if (configuredReleaseApiBaseUrl.isBlank()) {
            throw GradleException("Release builds require -PcujammuApiBaseUrl=https://<approved-api-host>/")
        }
    }
}
tasks.configureEach {
    val releaseTask = name.contains("Release", ignoreCase = true)
    if (releaseTask) {
        // Keep validation on release tasks themselves so excluding a dependency cannot bypass it.
        doFirst {
            if (configuredReleaseApiBaseUrl.isBlank()) {
                throw GradleException("Release builds require -PcujammuApiBaseUrl=https://<approved-api-host>/")
            }
        }
        // With no endpoint, force any output-bearing release task to execute its inline guard.
        // With a configured endpoint, normal Gradle up-to-date/cache behavior remains enabled.
        outputs.upToDateWhen { configuredReleaseApiBaseUrl.isNotBlank() }
    }
}
val escapedReleaseApiBaseUrl = configuredReleaseApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.cujammu.pulse"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cujammu.pulse"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_BASE_URL", "\"\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$escapedReleaseApiBaseUrl\"")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose & Material 3
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Networking - Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image Loading - Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // CameraX & Barcode Scanning (Dual-QR 60 FPS)
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Local Storage - Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Coroutines & Lifecycle ViewModel
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
