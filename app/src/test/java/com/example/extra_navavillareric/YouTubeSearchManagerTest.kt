package com.example.extra_navavillareric

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeSearchManagerTest {
    @Test
    fun search_withDirectVideoLink_returnsSingleResult() = runBlocking {
        val manager = YouTubeSearchManager()
        val resultsJson = manager.search("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertEquals(true, resultsJson.contains("dQw4w9WgXcQ") || resultsJson.contains("Video desde enlace"))
    }

    @Test
    fun extractStreamUrl_fromPipedResponse_returnsMp4Url() {
        val manager = YouTubeSearchManager()
        val body = """
            {
              "videoStreams": [
                {
                  "mimeType": "video/webm; codecs=vp9",
                  "url": "https://example.com/stream.webm"
                },
                {
                  "mimeType": "video/mp4; codecs=avc1",
                  "url": "https://example.com/stream.mp4"
                }
              ]
            }
        """.trimIndent()

        val streamUrl = manager.extractStreamUrl(body)

        assertEquals("https://example.com/stream.mp4", streamUrl)
    }
}
