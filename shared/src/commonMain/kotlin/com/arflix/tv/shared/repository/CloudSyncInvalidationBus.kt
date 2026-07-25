package com.arflix.tv.shared.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

enum class CloudSyncScope {
    WATCHLIST,
    HISTORY,
    SETTINGS
}

data class CloudSyncInvalidation(
    val scope: CloudSyncScope,
    val profileId: String?,
    val reason: String
)

interface CloudSyncInvalidationBus {
    val invalidationFlow: SharedFlow<CloudSyncInvalidation>
    fun markDirty(scope: CloudSyncScope, profileId: String? = null, reason: String = "")
    suspend fun <T> suppressDuringRemoteApply(block: suspend () -> T): T
}
