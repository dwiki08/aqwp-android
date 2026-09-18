package froztt13.python.aqw.data.model

import kotlinx.serialization.json.Json

/**
 * Global Json serializer instance configured for resilience and readable formatting.
 */
val appJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
}
