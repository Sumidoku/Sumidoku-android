import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * ncnn android-vulkan prebuilt SDK (for waifu2x upscaling).
 * The SDK is NOT committed to git; `fetchNcnnSdk` downloads + sha256-verifies + extracts it
 * into third_party/ncnn/ before any native build runs. Bump the version and hash together.
 */
val ncnnVersion = "20260113"
val ncnnSha256 = "41944630a7b305b190e7caea4b405bc6d103c6383dc4988e9e85616058e3cccd"
val ncnnSdkDir = rootProject.file("third_party/ncnn/ncnn-$ncnnVersion-android-vulkan")

val fetchNcnnSdk = tasks.register("fetchNcnnSdk") {
    val zipFile = rootProject.file("third_party/ncnn/ncnn-$ncnnVersion-android-vulkan.zip")
    val marker = File(ncnnSdkDir, ".extracted-v$ncnnVersion")
    outputs.upToDateWhen { marker.exists() }
    doLast {
        if (marker.exists()) return@doLast
        // A manually provided SDK (copied from another checkout, or a cached CI artifact) skips the
        // download: the hash is only verifiable for what this task fetched itself.
        if (File(ncnnSdkDir, "arm64-v8a/lib/libncnn.a").exists()) {
            marker.parentFile.mkdirs()
            marker.writeText("provided")
            logger.lifecycle("ncnn SDK present at $ncnnSdkDir (not downloaded by this build)")
            return@doLast
        }
        if (!zipFile.exists()) {
            zipFile.parentFile.mkdirs()
            logger.lifecycle("Downloading ncnn $ncnnVersion android-vulkan SDK...")
            URI("https://github.com/Tencent/ncnn/releases/download/$ncnnVersion/ncnn-$ncnnVersion-android-vulkan.zip")
                .toURL().openStream().use { input ->
                    zipFile.outputStream().use { input.copyTo(it) }
                }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        zipFile.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == ncnnSha256) {
            zipFile.delete()
            "ncnn SDK sha256 mismatch: expected $ncnnSha256, got $actual (zip deleted, retry to re-download)"
        }
        copy {
            from(zipTree(zipFile))
            into(zipFile.parentFile)
        }
        marker.writeText("ok")
        zipFile.delete()
        logger.lifecycle("ncnn SDK ready at $ncnnSdkDir")
    }
}

android {
    namespace = "org.sumidoku.android.core.image"
    compileSdk = 37
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 26
        ndk {
            // Real devices only; x86 emulators have no reliable Vulkan. A build without these
            // ABIs simply cannot load the engine and the reader falls back to the source page.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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

tasks.named("preBuild") { dependsOn(fetchNcnnSdk) }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
