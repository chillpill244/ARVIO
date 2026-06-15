package com.muvio.shared.di

import com.muvio.shared.network.StremioClient
import com.muvio.shared.network.createHttpClient
import org.koin.dsl.module

val networkModule = module {
    single { createHttpClient() }
}

val stremioModule = module {
    single { StremioClient(get()) }
}

/** All modules to pass to startKoin { modules(...) } in each app shell. */
val sharedModules = listOf(networkModule, stremioModule)
