import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Claves secretas: se leen al compilar desde (1) local.properties (Git lo ignora) o (2) una variable
// de entorno (GitHub Secrets). NUNCA van escritas en el código fuente.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim().replace("\"", "")

android {
    namespace = "com.mivehiculo.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mivehiculo.launcher"
        minSdk = 24
        // Radios chinos: targetSdk bajo evita restricciones de Android moderno
        targetSdk = 28
        versionCode = 13
        versionName = "0.13"

        buildConfigField("String", "GROQ_API_KEY", "\"${secret("GROQ_API_KEY")}\"")
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"${secret("OPENWEATHER_API_KEY")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildTypes {
        // Versión "release": más rápida y ligera en el radio (se firma con la clave de debug para poder instalarla).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")

    // Reproductor de música local (ExoPlayer) y su sesión de medios
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
}
