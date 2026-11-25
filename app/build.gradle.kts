plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.phonevps.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phonevps.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "1.9"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            // Bundle Discord.py and its Android-compatible HTTP dependency.
            install("discord.py==2.5.2")
            install("aiohttp==3.10.10")
        }
    }
}
