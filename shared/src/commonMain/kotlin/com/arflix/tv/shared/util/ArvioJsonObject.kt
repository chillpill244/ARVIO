package com.arflix.tv.shared.util

import kotlinx.serialization.json.*

class ArvioJsonObject {
    internal val map = mutableMapOf<String, JsonElement>()

    constructor()

    constructor(json: String) {
        try {
            val element = Json.parseToJsonElement(json)
            if (element is JsonObject) {
                map.putAll(element)
            }
        } catch (e: Exception) {
            // Ignore parse errors to match org.json behavior partially
        }
    }

    fun put(key: String, value: Any?): ArvioJsonObject {
        val element = when (value) {
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is ArvioJsonObject -> JsonObject(value.map)
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }
        map[key] = element
        return this
    }

    fun optString(key: String, fallback: String = ""): String {
        val el = map[key] ?: return fallback
        if (el is JsonNull) return fallback
        return el.jsonPrimitive.contentOrNull ?: fallback
    }

    fun optLong(key: String, fallback: Long = 0L): Long {
        val el = map[key] ?: return fallback
        if (el is JsonNull) return fallback
        return el.jsonPrimitive.longOrNull ?: fallback
    }
    
    fun optInt(key: String, fallback: Int = 0): Int {
        val el = map[key] ?: return fallback
        if (el is JsonNull) return fallback
        return el.jsonPrimitive.intOrNull ?: fallback
    }

    fun has(key: String): Boolean {
        return map.containsKey(key) && map[key] !is JsonNull
    }
    
    fun optJSONObject(key: String): ArvioJsonObject? {
        val el = map[key] ?: return null
        if (el !is JsonObject) return null
        val newObj = ArvioJsonObject()
        el.forEach { (k, v) -> newObj.map[k] = v }
        return newObj
    }

    fun getLong(key: String): Long {
        return optLong(key)
    }

    override fun toString(): String {
        return JsonObject(map).toString()
    }
}
