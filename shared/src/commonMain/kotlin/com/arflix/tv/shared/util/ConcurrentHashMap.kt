package com.arflix.tv.shared.util

class ConcurrentHashMap<K, V> : HashMap<K, V>() {
    override fun putIfAbsent(key: K, value: V): V? {
        if (!containsKey(key)) {
            put(key, value)
            return null
        }
        return get(key)
    }
}
