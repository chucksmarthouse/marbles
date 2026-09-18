import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.aboratech.marbles"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aboratech.marbles"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.6.1"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("release.storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (localProperties.getProperty("release.storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
