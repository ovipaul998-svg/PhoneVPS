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
        versionCode = 10
        versionName = "2.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        val ksPath = System.getenv("PHONEVPS_KEYSTORE_PATH")
        if (!ksPath.isNullOrBlank()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("PHONEVPS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PHONEVPS_KEY_ALIAS")
                keyPassword = System.getenv("PHONEVPS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
            // Bundle Discord.py and common pure-Python web/API packages.
            install("discord.py==2.5.2")
            install("aiohttp==3.10.10")
            install("flask==3.0.3")
            install("requests==2.32.3")
            install("python-dotenv==1.0.1")
            install("beautifulsoup4==4.12.3")
        }
    }
}
