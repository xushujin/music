plugins {
    // AGP 9.0 起 Kotlin 支持已内建，不再需要 org.jetbrains.kotlin.android
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
