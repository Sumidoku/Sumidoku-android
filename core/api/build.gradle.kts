plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Generated Retrofit client from the pinned server OpenAPI snapshot
// (api/openapi.json, hash pinned in api/openapi.json.sha256).
// See docs/android-client.md §6: the spec is the contract; regenerate
// deliberately, never edit generated output.
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(rootProject.file("api/openapi.json").absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    cleanupOutput.set(true)
    apiPackage.set("org.sumidoku.android.core.api")
    modelPackage.set("org.sumidoku.android.core.api.model")
    invokerPackage.set("org.sumidoku.android.core.api.infrastructure")
    library.set("jvm-retrofit2")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "string",
            "collectionType" to "list",
            "enumPropertyNaming" to "UPPERCASE",
            "omitGradleWrapper" to "true",
            "useOneOfInterfaces" to "false",
        ),
    )
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
}

tasks.register("patchGeneratedSources") {
    // Two stock-output fixes for the kotlin generator:
    // 1. `@Serializable interface` for the oneOf body RegisterInternalRequest
    //    is only legal on *sealed* interfaces in kotlinx-serialization.
    // 2. Properties typed `kotlin.Any` have no serializer and crash the
    //    serialization IR backend. On the wire they are arbitrary JSON, so
    //    JsonElement is the faithful representation.
    // Both patches are deterministic text replacements verified by the
    // compile; regenerate + recompile proves the spec still maps.
    dependsOn("openApiGenerate")
    val modelDir = file("build/generated/openapi/src/main/kotlin/org/sumidoku/android/core/api/model")
    doLast {
        val oneOf = File(modelDir, "RegisterInternalRequest.kt")
        if (oneOf.exists()) {
            val original = oneOf.readText()
            val patched = original.replace(
                Regex("""(?<!sealed )interface RegisterInternalRequest \{"""),
                "sealed interface RegisterInternalRequest {",
            )
            if (patched != original) oneOf.writeText(patched)
        }
        modelDir.listFiles { f -> f.extension == "kt" }?.forEach { source ->
            val lines = source.readLines()
            var changed = false
            val patched = lines.map { line ->
                if (line.trimStart().startsWith("val ") && line.contains("kotlin.Any")) {
                    changed = true
                    line.replace("kotlin.Any", "kotlinx.serialization.json.JsonElement")
                } else {
                    line
                }
            }
            if (changed) source.writeText(patched.joinToString(System.lineSeparator()))
        }
        // PageContent is an externally-tagged union emitted as a data class
        // whose variant fields are all *required*; the wire only carries the
        // active variant, so any decode fails with MissingFieldException.
        // Make every variant field nullable with a null default.
        val pageContent = File(modelDir, "PageContent.kt")
        if (pageContent.exists()) {
            val original = pageContent.readText()
            val patched = original.replace(
                Regex("""^(\s*val \w+: [^=\n]+),$""", RegexOption.MULTILINE),
                "$1? = null,",
            )
            if (patched != original) pageContent.writeText(patched)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("openApiGenerate")
    dependsOn("patchGeneratedSources")
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
