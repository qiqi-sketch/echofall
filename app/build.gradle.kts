plugins {
    id("com.android.application")
}

android {
    namespace = "com.echofall.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.echofall.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
