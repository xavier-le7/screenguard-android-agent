plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI signing is supplied through repository secrets. Never embed credentials.
val stableKeystorePath = System.getenv("SG_ANDROID_KEYSTORE_PATH")
val stableKeystorePassword = System.getenv("SG_ANDROID_KEYSTORE_PASSWORD")
val hasStableKey = !stableKeystorePath.isNullOrBlank() && !stableKeystorePassword.isNullOrBlank()

android {
    namespace = "fr.sevenskies.screenguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.sevenskies.screenguard.agent"
        minSdk = 34
        targetSdk = 35
        versionCode = 18
        versionName = "0.5.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("stable") {
            if (hasStableKey) {
                storeFile = file(stableKeystorePath!!)
                storePassword = stableKeystorePassword
                keyAlias = "screenguard-agent"
                keyPassword = stableKeystorePassword
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasStableKey) signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (hasStableKey) signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
