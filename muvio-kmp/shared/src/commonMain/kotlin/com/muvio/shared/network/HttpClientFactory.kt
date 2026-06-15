package com.muvio.shared.network

import io.ktor.client.HttpClient

/** Platform-specific HttpClient construction (OkHttp on Android, Darwin on iOS). */
expect fun createHttpClient(): HttpClient
