plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.sumidoku.android.core.source"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.android)
}
