package com.arflix.tv.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AvatarRegistryTest {

    // ===== totalAvatars tests =====

    @Test
    fun `totalAvatars returns 84`() {
        assertThat(AvatarRegistry.totalAvatars).isEqualTo(84)
    }

    // ===== categories tests =====

    @Test
    fun `categories has 4 categories`() {
        assertThat(AvatarRegistry.categories).hasSize(4)
    }

    @Test
    fun `categories includes Animals`() {
        val animalsCategory = AvatarRegistry.categories.find { it.first == "Animals" }
        assertThat(animalsCategory).isNotNull()
    }

    @Test
    fun `categories includes Characters`() {
        val charactersCategory = AvatarRegistry.categories.find { it.first == "Characters" }
        assertThat(charactersCategory).isNotNull()
    }

    @Test
    fun `categories includes Media`() {
        val mediaCategory = AvatarRegistry.categories.find { it.first == "Media" }
        assertThat(mediaCategory).isNotNull()
    }

    @Test
    fun `categories includes Nature`() {
        val natureCategory = AvatarRegistry.categories.find { it.first == "Nature" }
        assertThat(natureCategory).isNotNull()
    }

    @Test
    fun `each category has 21 avatars`() {
        AvatarRegistry.categories.forEach { (name, avatarIds) ->
            assertThat(avatarIds).hasSize(21)
        }
    }

    @Test
    fun `all avatar IDs are unique across categories`() {
        val allIds = AvatarRegistry.categories.flatMap { it.second }
        assertThat(allIds).containsNoDuplicates()
    }

    @Test
    fun `total avatars from categories matches totalAvatars`() {
        val countFromCategories = AvatarRegistry.categories.sumOf { it.second.size }
        assertThat(countFromCategories).isEqualTo(AvatarRegistry.totalAvatars)
    }

    @Test
    fun `all category IDs are in valid range 1-84`() {
        val allIds = AvatarRegistry.categories.flatMap { it.second }
        allIds.forEach { id ->
            assertThat(id).isAtLeast(1)
            assertThat(id).isAtMost(84)
        }
    }

    // ===== getDrawableRes tests =====

    @Test
    fun `getDrawableRes returns valid resource for avatar 1`() {
        val resource = AvatarRegistry.getDrawableRes(1)
        assertThat(resource).isNotEqualTo(0)
    }

    @Test
    fun `getDrawableRes returns fallback for invalid negative ID`() {
        val resource = AvatarRegistry.getDrawableRes(-1)
        // Should return avatar_1 as fallback
        assertThat(resource).isEqualTo(AvatarRegistry.getDrawableRes(1))
    }

    @Test
    fun `getDrawableRes returns fallback for ID above 84`() {
        val resource = AvatarRegistry.getDrawableRes(100)
        // Should return avatar_1 as fallback
        assertThat(resource).isEqualTo(AvatarRegistry.getDrawableRes(1))
    }

    @Test
    fun `getDrawableRes returns fallback for zero`() {
        val resource = AvatarRegistry.getDrawableRes(0)
        assertThat(resource).isEqualTo(AvatarRegistry.getDrawableRes(1))
    }

    // ===== gradientColors tests =====

    @Test
    fun `gradientColors returns pair of colors for valid ID`() {
        val (start, end) = AvatarRegistry.gradientColors(1)
        assertThat(start).isNotNull()
        assertThat(end).isNotNull()
    }

    @Test
    fun `gradientColors returns different colors for start and end`() {
        val (start, end) = AvatarRegistry.gradientColors(1)
        assertThat(start).isNotEqualTo(end)
    }

    @Test
    fun `gradientColors returns default for invalid ID`() {
        val (start, end) = AvatarRegistry.gradientColors(999)
        // Should return default gray gradient
        assertThat(start).isNotNull()
        assertThat(end).isNotNull()
    }

    @Test
    fun `gradientColors returns unique gradients for different avatars`() {
        val gradient1 = AvatarRegistry.gradientColors(1)
        val gradient10 = AvatarRegistry.gradientColors(10)
        val gradient50 = AvatarRegistry.gradientColors(50)

        // At least some gradients should be different
        assertThat(listOf(gradient1, gradient10, gradient50).distinct().size).isGreaterThan(1)
    }

    @Test
    fun `gradientColors works for all category avatar IDs`() {
        val allIds = AvatarRegistry.categories.flatMap { it.second }
        allIds.forEach { id ->
            val (start, end) = AvatarRegistry.gradientColors(id)
            assertThat(start).isNotNull()
            assertThat(end).isNotNull()
        }
    }

    // ===== Original avatar set tests (backward compatibility) =====

    @Test
    fun `original avatar IDs 1-24 are preserved`() {
        val animalsOriginal = listOf(1, 2, 3, 4, 5, 6)
        val charactersOriginal = listOf(7, 8, 9, 10, 11, 12)
        val mediaOriginal = listOf(13, 14, 15, 16, 17, 18)
        val natureOriginal = listOf(19, 20, 21, 22, 23, 24)

        val animals = AvatarRegistry.categories.find { it.first == "Animals" }!!.second
        val characters = AvatarRegistry.categories.find { it.first == "Characters" }!!.second
        val media = AvatarRegistry.categories.find { it.first == "Media" }!!.second
        val nature = AvatarRegistry.categories.find { it.first == "Nature" }!!.second

        animalsOriginal.forEach { assertThat(animals).contains(it) }
        charactersOriginal.forEach { assertThat(characters).contains(it) }
        mediaOriginal.forEach { assertThat(media).contains(it) }
        natureOriginal.forEach { assertThat(nature).contains(it) }
    }
}
