package com.arflix.tv.util

import com.arflix.tv.data.model.CatalogSourceType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogUrlParserTest {

    // ===== normalize() tests =====

    @Test
    fun `normalize trims whitespace`() {
        val result = CatalogUrlParser.normalize("  https://trakt.tv/lists/123  ")
        assertThat(result).isEqualTo("https://trakt.tv/lists/123")
    }

    @Test
    fun `normalize adds https scheme when missing`() {
        val result = CatalogUrlParser.normalize("trakt.tv/lists/123")
        assertThat(result).isEqualTo("https://trakt.tv/lists/123")
    }

    @Test
    fun `normalize preserves http scheme`() {
        val result = CatalogUrlParser.normalize("http://trakt.tv/lists/123")
        assertThat(result).isEqualTo("http://trakt.tv/lists/123")
    }

    @Test
    fun `normalize removes trailing slash`() {
        val result = CatalogUrlParser.normalize("https://trakt.tv/lists/123/")
        assertThat(result).isEqualTo("https://trakt.tv/lists/123")
    }

    @Test
    fun `normalize handles empty string`() {
        val result = CatalogUrlParser.normalize("")
        assertThat(result).isEmpty()
    }

    @Test
    fun `normalize handles whitespace-only string`() {
        val result = CatalogUrlParser.normalize("   ")
        assertThat(result).isEmpty()
    }

    // ===== detectSource() tests =====

    @Test
    fun `detectSource identifies Trakt URLs`() {
        assertThat(CatalogUrlParser.detectSource("https://trakt.tv/lists/123"))
            .isEqualTo(CatalogSourceType.TRAKT)
    }

    @Test
    fun `detectSource identifies Trakt subdomain`() {
        assertThat(CatalogUrlParser.detectSource("https://www.trakt.tv/lists/123"))
            .isEqualTo(CatalogSourceType.TRAKT)
    }

    @Test
    fun `detectSource identifies Mdblist URLs`() {
        assertThat(CatalogUrlParser.detectSource("https://mdblist.com/lists/test"))
            .isEqualTo(CatalogSourceType.MDBLIST)
    }

    @Test
    fun `detectSource identifies Mdblist subdomain`() {
        assertThat(CatalogUrlParser.detectSource("https://www.mdblist.com/lists/test"))
            .isEqualTo(CatalogSourceType.MDBLIST)
    }

    @Test
    fun `detectSource returns null for unknown URLs`() {
        assertThat(CatalogUrlParser.detectSource("https://example.com/lists/test"))
            .isNull()
    }

    @Test
    fun `detectSource handles URLs without scheme`() {
        assertThat(CatalogUrlParser.detectSource("trakt.tv/lists/123"))
            .isEqualTo(CatalogSourceType.TRAKT)
    }

    // ===== parseTrakt() tests =====

    @Test
    fun `parseTrakt parses user list URL`() {
        val result = CatalogUrlParser.parseTrakt("https://trakt.tv/users/johndoe/lists/my-list")

        assertThat(result).isInstanceOf(ParsedCatalogUrl.TraktUserList::class.java)
        val userList = result as ParsedCatalogUrl.TraktUserList
        assertThat(userList.username).isEqualTo("johndoe")
        assertThat(userList.listId).isEqualTo("my-list")
    }

    @Test
    fun `parseTrakt parses public list URL`() {
        val result = CatalogUrlParser.parseTrakt("https://trakt.tv/lists/popular-2024")

        assertThat(result).isInstanceOf(ParsedCatalogUrl.TraktList::class.java)
        val list = result as ParsedCatalogUrl.TraktList
        assertThat(list.listId).isEqualTo("popular-2024")
    }

    @Test
    fun `parseTrakt returns null for non-Trakt URL`() {
        val result = CatalogUrlParser.parseTrakt("https://example.com/lists/test")
        assertThat(result).isNull()
    }

    @Test
    fun `parseTrakt returns null for invalid Trakt path`() {
        val result = CatalogUrlParser.parseTrakt("https://trakt.tv/movies")
        assertThat(result).isNull()
    }

    @Test
    fun `parseTrakt handles trailing slashes in user list`() {
        val result = CatalogUrlParser.parseTrakt("https://trakt.tv/users/test/lists/item/")

        assertThat(result).isInstanceOf(ParsedCatalogUrl.TraktUserList::class.java)
    }

    // ===== parse() tests =====

    @Test
    fun `parse handles Trakt user list`() {
        val result = CatalogUrlParser.parse("https://trakt.tv/users/foo/lists/bar")

        assertThat(result).isInstanceOf(ParsedCatalogUrl.TraktUserList::class.java)
    }

    @Test
    fun `parse handles Trakt public list`() {
        val result = CatalogUrlParser.parse("https://trakt.tv/lists/trending")

        assertThat(result).isInstanceOf(ParsedCatalogUrl.TraktList::class.java)
    }

    @Test
    fun `parse handles Mdblist URL`() {
        val result = CatalogUrlParser.parse("https://mdblist.com/lists/favorite")

        assertThat(result).isInstanceOf(ParsedCatalogUrl.Mdblist::class.java)
        val mdblist = result as ParsedCatalogUrl.Mdblist
        assertThat(mdblist.url).isEqualTo("https://mdblist.com/lists/favorite")
    }

    @Test
    fun `parse returns null for unknown source`() {
        val result = CatalogUrlParser.parse("https://imdb.com/list/ls123")
        assertThat(result).isNull()
    }

    @Test
    fun `parse handles malformed URL gracefully`() {
        val result = CatalogUrlParser.parse("not a valid url at all")
        assertThat(result).isNull()
    }
}
