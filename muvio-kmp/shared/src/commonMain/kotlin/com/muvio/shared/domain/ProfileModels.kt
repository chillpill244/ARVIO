package com.muvio.shared.domain

import com.muvio.shared.util.currentTimeMillis
import com.muvio.shared.util.randomUUID

data class Profile(
    val id: String = randomUUID(),
    val name: String,
    val avatarColor: Long = ProfileColors.random(),
    val avatarId: Int = 0,
    val avatarImageVersion: Long = 0L,
    val avatarImageStoragePath: String? = null,
    val isKidsProfile: Boolean = false,
    val pin: String? = null,
    val isLocked: Boolean = false,
    val createdAt: Long = currentTimeMillis(),
    val lastUsedAt: Long = currentTimeMillis(),
)

object ProfileColors {
    val colors = listOf(
        0xFFE50914L,
        0xFF1DB954L,
        0xFF3B82F6L,
        0xFFF59E0BL,
        0xFF8B5CF6L,
        0xFFEC4899L,
        0xFF14B8A6L,
        0xFF6366F1L,
    )

    fun random(): Long = colors.random()
    fun getByIndex(index: Int): Long = colors[index % colors.size]
}
