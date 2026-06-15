package com.muvio.shared.util

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun randomUUID(): String = java.util.UUID.randomUUID().toString()
