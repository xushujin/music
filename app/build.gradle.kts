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
        versionName = "1.0.0"
    }

    // 签名来源（按优先级）：
    //   1) CI：环境变量 KEYSTORE_PATH（由 GitHub Secrets 注入，或工作流生成的 debug keystore）
    //   2) 本地：仓库内的个人 keystore（已 gitignore）
    //   3) 都没有（如 F-Droid 从干净检出构建）→ **不签名**，产出未签名 release 包，
    //      而不是让构建失败。F-Droid 会用它自己的密钥重新签名。
    val ciKeystorePath = System.getenv("KEYSTORE_PATH")
    val localKeystore = rootProject.file("keystore/lightmusic-release.jks")
    val canSign = ciKeystorePath != null || localKeystore.exists()

    signingConfigs {
        create("release") {
            when {
                ciKeystorePath != null -> {
                    storeFile = file(ciKeystorePath)
                    storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "lightmusic"
                    keyAlias = System.getenv("KEY_ALIAS") ?: "lightmusic"
                    keyPassword = System.getenv("KEY_PASSWORD") ?: "lightmusic"
                }

                localKeystore.exists() -> {
                    storeFile = localKeystore
                    storePassword = "lightmusic"
                    keyAlias = "lightmusic"
                    keyPassword = "lightmusic"
                }
                // else：没有可用密钥，不设置 storeFile；buildTypes 里也不会挂这个配置
            }
        }
    }

    buildTypes {
        release {
            if (canSign) signingConfig = signingConfigs.getByName("release")
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
