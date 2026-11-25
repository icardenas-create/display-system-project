plugins {
    id("com.android.application")
}

android {
    namespace = "com.displaysystem.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.displaysystem.app"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Fixed signing key so every new build can be installed over the old one.
    signingConfigs {
        create("display") {
            storeFile = file("signing/display-system.keystore")
            storePassword = "displaysystem"
            keyAlias = "displaysystem"
            keyPassword = "displaysystem"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("display")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("display")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
