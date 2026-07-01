package com.example.extra_navavillareric

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

// Estas pruebas dependen de red real: la extracción ahora consulta a YouTube directamente
// vía NewPipeExtractor (en vez de parsear una respuesta JSON fija de un proxy Piped), así
// que requieren conexión a Internet para ejecutarse.
class YouTubeSearchManagerTest {
    @Test
    fun search_withDirectVideoLink_returnsSingleResult() = runBlocking {
        val manager = YouTubeSearchManager()
        val resultsJson = manager.search("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertTrue(resultsJson.contains("dQw4w9WgXcQ"))
    }

    @Test
    fun extractStreamUrl_forKnownVideo_returnsPlayableUrl() {
        val manager = YouTubeSearchManager()
        val streamUrl = manager.extractStreamUrl("dQw4w9WgXcQ")

        assertTrue("Se esperaba una URL de stream http(s), se obtuvo: $streamUrl", streamUrl.startsWith("http"))
    }
}
