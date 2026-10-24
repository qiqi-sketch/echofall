plugins {
    id("com.android.application")
}

val sdl2SourceDir: String? = System.getenv("SDL2_SOURCE_DIR")

android {
    namespace = "org.libsdl.app"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.frontline.rts"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                arguments.addAll(listOf("-DANDROID_STL=c++_shared", "-DCMAKE_BUILD_TYPE=Release"))
                if (!sdl2SourceDir.isNullOrEmpty()) {
                    arguments.add("-DSDL2_SOURCE_DIR=$sdl2SourceDir")
                }
                cppFlags.add("-O2")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../CMakeLists.txt")
            version = "3.22.1"
        }
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

    lint {
        abortOnError = false
    }
}

dependencies {
}
