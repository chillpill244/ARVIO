package com.muvio.shared.domain

data class IptvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String,
    val logo: String? = null,
    val epgId: String? = null,
    val rawTitle: String = name,
    val xtreamStreamId: Int? = null,
    val catchupDays: Int = 0,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    val tvgName: String? = null,
    val providerChannelNumber: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val language: String? = null,
    val country: String? = null,
    val qualityLabel: String? = null,
    val variantKey: String? = null,
)

data class IptvNowNext(
    val now: IptvProgram? = null,
    val next: IptvProgram? = null,
    val later: IptvProgram? = null,
    val upcoming: List<IptvProgram> = emptyList(),
    val recent: List<IptvProgram> = emptyList(),
)

data class IptvProgram(
    val title: String,
    val description: String? = null,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val catchupAvailable: Boolean? = null,
) {
    fun isLive(atUtcMillis: Long): Boolean = atUtcMillis in startUtcMillis until endUtcMillis
    fun startsInMinutes(atUtcMillis: Long): Long =
        ((startUtcMillis - atUtcMillis) / 60_000L).coerceAtLeast(0L)
}

data class IptvSnapshot(
    val channels: List<IptvChannel> = emptyList(),
    val grouped: Map<String, List<IptvChannel>> = emptyMap(),
    val nowNext: Map<String, IptvNowNext> = emptyMap(),
    val favoriteGroups: List<String> = emptyList(),
    val favoriteChannels: List<String> = emptyList(),
    val hiddenGroups: List<String> = emptyList(),
    val groupOrder: List<String> = emptyList(),
    val epgWarning: String? = null,
    val loadedAt: Long = 0L,
)

data class PlaylistGroupKey(val key: String) {
    val playlistId: String get() = key.substringBefore('|')
    val groupName: String get() = key.substringAfter('|', missingDelimiterValue = key)

    companion object {
        fun build(playlistId: String, groupName: String): String = "$playlistId|$groupName"
    }
}
