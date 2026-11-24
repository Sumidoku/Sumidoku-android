package org.sumidoku.android.core.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.net.URI

/**
 * The generated models type URL fields as `@Contextual java.net.URI`
 * (SourceRepo.url, AddRepoRequest.url, UrlResponse.url, …). kotlinx has no
 * built-in URI serializer, so without this registration every decode of
 * those payloads throws `Serializer for class 'URI' is not found` — which is
 * why the repositories screen only ever showed an error. The wire format is
 * a plain string both ways.
 */
object UriStringSerializer : KSerializer<URI> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.net.URI", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: URI) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URI = URI.create(decoder.decodeString())
}

/** Contextual serializers every generated-model decode needs. */
val apiSerializersModule: SerializersModule = SerializersModule {
    contextual(URI::class, UriStringSerializer)
}

/**
 * The one Json configuration used for every Sumidoku API call. Kept here —
 * next to the generated models — so the network stack and the decode tests
 * share a single source of truth instead of drifting apart.
 */
fun sumidokuJson(): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    serializersModule = apiSerializersModule
}
