package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConstantsTest {

    // ===== LanguageMap tests =====

    @Test
    fun `getLanguageName returns English for en code`() {
        val result = LanguageMap.getLanguageName("en")
        assertThat(result).isEqualTo("English")
    }

    @Test
    fun `getLanguageName returns English for eng code`() {
        val result = LanguageMap.getLanguageName("eng")
        assertThat(result).isEqualTo("English")
    }

    @Test
    fun `getLanguageName returns French for fr code`() {
        val result = LanguageMap.getLanguageName("fr")
        assertThat(result).isEqualTo("French")
    }

    @Test
    fun `getLanguageName returns French for fre code`() {
        val result = LanguageMap.getLanguageName("fre")
        assertThat(result).isEqualTo("French")
    }

    @Test
    fun `getLanguageName returns French for fra code`() {
        val result = LanguageMap.getLanguageName("fra")
        assertThat(result).isEqualTo("French")
    }

    @Test
    fun `getLanguageName returns Spanish for es code`() {
        val result = LanguageMap.getLanguageName("es")
        assertThat(result).isEqualTo("Spanish")
    }

    @Test
    fun `getLanguageName returns Japanese for ja code`() {
        val result = LanguageMap.getLanguageName("ja")
        assertThat(result).isEqualTo("Japanese")
    }

    @Test
    fun `getLanguageName returns Japanese for jpn code`() {
        val result = LanguageMap.getLanguageName("jpn")
        assertThat(result).isEqualTo("Japanese")
    }

    @Test
    fun `getLanguageName returns Korean for ko code`() {
        val result = LanguageMap.getLanguageName("ko")
        assertThat(result).isEqualTo("Korean")
    }

    @Test
    fun `getLanguageName returns Chinese for zh code`() {
        val result = LanguageMap.getLanguageName("zh")
        assertThat(result).isEqualTo("Chinese")
    }

    @Test
    fun `getLanguageName is case insensitive`() {
        assertThat(LanguageMap.getLanguageName("EN")).isEqualTo("English")
        assertThat(LanguageMap.getLanguageName("En")).isEqualTo("English")
        assertThat(LanguageMap.getLanguageName("eN")).isEqualTo("English")
    }

    @Test
    fun `getLanguageName returns uppercase code for unknown language`() {
        val result = LanguageMap.getLanguageName("xyz")
        assertThat(result).isEqualTo("XYZ")
    }

    @Test
    fun `getLanguageName handles empty string`() {
        val result = LanguageMap.getLanguageName("")
        assertThat(result).isEqualTo("")
    }

    @Test
    fun `getLanguageName returns German for de code`() {
        val result = LanguageMap.getLanguageName("de")
        assertThat(result).isEqualTo("German")
    }

    @Test
    fun `getLanguageName returns German for ger code`() {
        val result = LanguageMap.getLanguageName("ger")
        assertThat(result).isEqualTo("German")
    }

    @Test
    fun `getLanguageName returns German for deu code`() {
        val result = LanguageMap.getLanguageName("deu")
        assertThat(result).isEqualTo("German")
    }

    @Test
    fun `getLanguageName returns Hindi for hi code`() {
        val result = LanguageMap.getLanguageName("hi")
        assertThat(result).isEqualTo("Hindi")
    }

    @Test
    fun `getLanguageName returns Arabic for ar code`() {
        val result = LanguageMap.getLanguageName("ar")
        assertThat(result).isEqualTo("Arabic")
    }

    @Test
    fun `getLanguageName returns Russian for ru code`() {
        val result = LanguageMap.getLanguageName("ru")
        assertThat(result).isEqualTo("Russian")
    }

    @Test
    fun `getLanguageName returns Portuguese for pt code`() {
        val result = LanguageMap.getLanguageName("pt")
        assertThat(result).isEqualTo("Portuguese")
    }

    @Test
    fun `getLanguageName returns Italian for it code`() {
        val result = LanguageMap.getLanguageName("it")
        assertThat(result).isEqualTo("Italian")
    }

    @Test
    fun `getLanguageName returns Dutch for nl code`() {
        val result = LanguageMap.getLanguageName("nl")
        assertThat(result).isEqualTo("Dutch")
    }
}
