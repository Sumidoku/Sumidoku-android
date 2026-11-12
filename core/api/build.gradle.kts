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
        // Externally-tagged unions are emitted as data classes whose
        // variant fields are all *required*; the wire only carries the
        // active variant, so any decode fails with MissingFieldException.
        // Make every variant field nullable with a null default. The set
        // of union files is derived from the spec's oneOf/discriminator
        // shapes and must be re-checked when the spec changes.
        listOf("PageContent.kt", "SettingValue.kt", "HomeComponentValue.kt", "FilterValue.kt", "LinkValue.kt").forEach { name ->
            val union = File(modelDir, name)
            if (union.exists()) {
                val lines = union.readLines()
                var changed = false
                val patched = lines.map { line ->
                    val trimmed = line.trimStart()
                    val candidate = trimmed.removeSuffix(",")
                    if (
                        trimmed.startsWith("val ") &&
                        !candidate.contains('?') &&
                        !candidate.contains('=') &&
                        candidate.length > 4
                    ) {
                        changed = true
                        "${line.substringBefore("val ")}$candidate? = null${if (trimmed.endsWith(",")) "," else ""}"
                    } else {
                        line
                    }
                }
                if (changed) union.writeText(patched.joinToString(System.lineSeparator()))
            }
        }
        // RegisterInternalRequest's oneOf implementations are dropped by the
        // generator (only the sealed interface survives), so callers can never
        // construct a request. The wire body is a plain externally-tagged JSON
        // object ({"Local": {...}} / {"Opds": {...}}), so accept JsonObject
        // directly — the kotlinx converter serializes it as-is.
        // NOTE: relative File() resolves against the Gradle daemon CWD,
        // not this project — use projectDir so the patch actually lands.
        val sourcesApi = File(projectDir, "build/generated/openapi/src/main/kotlin/org/sumidoku/android/core/api/SourcesApi.kt")
        if (sourcesApi.exists()) {
            val original = sourcesApi.readText()
            val patched = original.replace(
                "registerInternalSource(@Body registerInternalRequest: RegisterInternalRequest)",
                "registerInternalSource(@Body body: kotlinx.serialization.json.JsonObject)",
            )
            if (patched != original) sourcesApi.writeText(patched)
        }
        // MetadataFieldValue is a free-form object in the spec and generates
        // as an empty class, which silently drops the preview diff values.
        // The wire is arbitrary JSON (strings, lists, objects), so expose the
        // raw element for display.
        listOf("MetadataPreviewField.kt", "MetadataFieldAlternative.kt").forEach { name ->
            val target = File(modelDir, name)
            if (target.exists()) {
                val original = target.readText()
                val patched = original.replace(
                    "MetadataFieldValue?",
                    "kotlinx.serialization.json.JsonElement?",
                )
                if (patched != original) target.writeText(patched)
            }
        }
        // Filter rule values are free-form scalars (null for flag rules,
        // integer for valued rules, string for author/tag); the generated
        // empty wrapper classes would drop them, so expose the raw element.
        listOf(
            "FilterRule.kt" to "FilterRuleValue?",
            "FilterRuleInput.kt" to "FilterRuleInputValue?",
        ).forEach { (name, wrapper) ->
            val target = File(modelDir, name)
            if (target.exists()) {
                val original = target.readText()
                val patched = original.replace(
                    wrapper,
                    "kotlinx.serialization.json.JsonElement?",
                )
                if (patched != original) target.writeText(patched)
            }
        }
        // Nullable properties without a default still require the key to be
        // present: a missing key aborts the whole decode with
        // MissingFieldException. The vendored aidoku wire types send
        // explicit nulls, but any hand-written or future payload that omits
        // an optional key would take the whole source home/listing/search
        // response down. Default every nullable property to null in that
        // payload family so decode tolerates omitted keys.
        val tolerantNames = setOf(
            "HomeComponent.kt", "HomeLayout.kt", "Link.kt", "AidokuListing.kt",
            "Listing.kt", "HomeFilterItem.kt", "SourceManga.kt",
            "SourceChapter.kt", "MangaWithChapter.kt",
        )
        modelDir.listFiles { f ->
            f.extension == "kt" && (
                f.name in tolerantNames ||
                    f.name.startsWith("HomeComponentValueOneOf") ||
                    f.name.startsWith("LinkValueOneOf")
                )
        }?.forEach { target ->
            val lines = target.readLines()
            var changed = false
            val patched = lines.map { line ->
                val trimmed = line.trimStart()
                val candidate = trimmed.removeSuffix(",")
                if (
                    trimmed.startsWith("val ") &&
                    candidate.contains('?') &&
                    !candidate.contains('=') &&
                    candidate.length > 4
                ) {
                    changed = true
                    "${line.substringBefore("val ")}$candidate = null${if (trimmed.endsWith(",")) "," else ""}"
                } else {
                    line
                }
            }
            if (changed) target.writeText(patched.joinToString(System.lineSeparator()))
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
    testImplementation(libs.junit)
}
