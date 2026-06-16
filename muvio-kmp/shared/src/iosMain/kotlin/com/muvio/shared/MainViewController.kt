package com.muvio.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.muvio.shared.di.AppConfig
import com.muvio.shared.di.sharedModules
import com.muvio.shared.ui.App
import org.koin.core.context.startKoin
import org.koin.dsl.module

private var koinStarted = false

fun MainViewController() = run {
    if (!koinStarted) {
        koinStarted = true
        startKoin {
            modules(
                module {
                    single {
                        AppConfig(
                            tmdbApiKey = "98ab075e4d53e22396a51b6e35359bba",
                            traktClientId = "234d1a473e25d15ad05127370529a567547b7b86890bdc00f735ea1757d8d157",
                        )
                    }
                },
                *sharedModules.toTypedArray(),
            )
        }
    }
    ComposeUIViewController { App() }
}
