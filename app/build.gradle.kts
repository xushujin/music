import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.azhan.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.azhan.music"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // 本地：用仓库内（已 gitignore）的个人 keystore。
            // CI：从环境变量读取（由 GitHub Secrets 注入），密钥不进仓库。
            val ciKeystore = System.getenv("KEYSTORE_PATH")
            if (ciKeystore != null) {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "lightmusic"
                keyAlias = System.getenv("KEY_ALIAS") ?: "lightmusic"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "lightmusic"
            } else {
                storeFile = rootProject.file("keystore/lightmusic-release.jks")
                storePassword = "lightmusic"
                keyAlias = "lightmusic"
                keyPassword = "lightmusic"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
