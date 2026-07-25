package com.arflix.tv.shared.repository

interface CloudSyncRepository {
    suspend fun pushToCloud(): Result<Unit>
}
