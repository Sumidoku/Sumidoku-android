plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "org.sumidoku.android"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.sumidoku.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"
    }
    // Signing is opt-in and secret-driven, so a plain checkout builds without it:
    // set KEYSTORE_FILE (a .jks path), KEYSTORE_PASSWORD, KEY_ALIAS and
    // KEY_PASSWORD and `assembleRelease` produces a signed APK; otherwise the
    // release build stays unsigned.
    val keystorePath = providers.environmentVariable("KEYSTORE_FILE").orNull
    val keystorePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
    val keyAliasName = providers.environmentVariable("KEY_ALIAS").orNull
    val keyAliasPassword = providers.environmentVariable("KEY_PASSWORD").orNull
    val signingKeystore = keystorePath?.takeIf { path ->
        file(path).exists() &&
            keystorePassword != null &&
            keyAliasName != null &&
            keyAliasPassword != null
    }
    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyAliasPassword
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // The PropertyEscape check flags every valid sdk.dir form in
        // local.properties (escaped backslashes, forward slashes, colon-escaped),
        // so it cannot be satisfied on Windows; disable it.
        disable += "PropertyEscape"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":feature:servers"))
    implementation(project(":feature:library"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:history"))
    implementation(project(":feature:updates"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:manga"))
    implementation(project(":feature:source"))
    implementation(project(":feature:repos"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:trackers"))
    implementation(project(":feature:migrate"))
    implementation(project(":feature:search"))
    implementation(project(":feature:backups"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:image"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.retrofit)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
