package com.muvio.shared.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SearchRoute

@Serializable
object SettingsRoute

@Serializable
data class DetailsRoute(val tmdbId: Int, val mediaType: String)

@Serializable
data class PlayerRoute(val tmdbId: Int, val mediaType: String, val streamIndex: Int = 0)
