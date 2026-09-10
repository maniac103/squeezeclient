package de.maniac103.squeezeclient.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListResponseTest {
    @Test
    fun commandAcknowledgementDoesNotKeepRequestingPages() {
        var requestCount = 0
        do {
            requestCount++
            // favorites add returns count: 1 without an item_loop or offset.
            val response = Response(emptyList(), 0, 1)
        } while (response.serverHasMoreData && requestCount < 500)

        assertEquals(1, requestCount)
    }

    @Test
    fun emptyPageStopsEvenWhenServerReportsMoreItems() {
        assertFalse(Response(emptyList(), 100, 200).serverHasMoreData)
    }

    @Test
    fun emptyListHasNoMorePages() {
        assertFalse(Response(emptyList(), 0, 0).serverHasMoreData)
    }

    @Test
    fun partialPageStillAllowsLoadingRemainingItems() {
        assertTrue(Response(listOf("track"), 100, 200).serverHasMoreData)
    }

    @Test
    fun finalPageHasNoMorePages() {
        assertFalse(Response(listOf("track"), 199, 200).serverHasMoreData)
    }

    private data class Response(
        override val items: List<String>,
        override val offset: Int,
        override val totalCount: Int
    ) : ListResponse<String>
}
