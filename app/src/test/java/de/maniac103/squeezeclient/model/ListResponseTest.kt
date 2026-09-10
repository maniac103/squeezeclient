package de.maniac103.squeezeclient.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListResponseTest {
    @Test
    fun emptyListHasNoMorePages() {
        // Regular empty list
        assertFalse(Response(emptyList(), 0, 0).serverHasMoreData)
        // favorites add returns count: 1 without an item_loop or offset
        assertFalse(Response(emptyList(), 0, 1).serverHasMoreData)
        assertFalse(Response(emptyList(), 100, 200).serverHasMoreData)
    }

    @Test
    fun finalPageHasNoMorePages() {
        assertFalse(Response(listOf("track"), 199, 200).serverHasMoreData)
    }

    @Test
    fun partialPageHasMorePages() {
        assertTrue(Response(listOf("track"), 100, 200).serverHasMoreData)
    }

    private data class Response(
        override val items: List<String>,
        override val offset: Int,
        override val totalCount: Int
    ) : ListResponse<String>
}
