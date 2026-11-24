package org.sumidoku.android.core.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import org.sumidoku.android.core.api.model.SettingValue

/**
 * The wire `SettingValue` is externally tagged: known kinds are one-key
 * objects, but `Button` and any kind unknown to the server's normalizer
 * (e.g. the local source `list` rows for paths/exclusions) are bare JSON
 * strings. The generated data class has no string branch, so a bare string
 * aborts decoding of the whole settings document with
 * "Expected start of the object '{'". Bare strings are carried in
 * [SettingValue.rawString] (a patch-added field); object payloads delegate
 * to the generated serializer.
 */
object SettingValueSerializer : KSerializer<SettingValue> {
    private val delegate = SettingValue.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): SettingValue {
        val json = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = json.decodeJsonElement()) {
            is JsonPrimitive -> SettingValue(rawString = element.content)
            else -> json.json.decodeFromJsonElement(delegate, element)
        }
    }

    override fun serialize(encoder: Encoder, value: SettingValue) {
        val json = encoder as? JsonEncoder
        if (json == null) {
            delegate.serialize(encoder, value)
            return
        }
        val raw = value.rawString
        if (raw != null) {
            json.encodeJsonElement(JsonPrimitive(raw))
        } else {
            json.encodeJsonElement(json.json.encodeToJsonElement(delegate, value))
        }
    }
}
