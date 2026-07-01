package com.example.extra_navavillareric

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

class YouTubeSearchManager {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    init {
        NewPipeBootstrap.ensureInitialized()
    }

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext "[]"

        try {
            if (isYouTubeUrl(query)) {
                // Pegar cualquier URL de YouTube (watch, youtu.be, shorts, music) debe
                // reproducirse: se valida y trae metadata real en vez de solo adivinar el id.
                val info = StreamInfo.getInfo(ServiceList.YouTube, query)
                return@withContext json.encodeToString(listOf(
                    VideoModel(
                        id = extractVideoId(info.url ?: query),
                        title = (info.name ?: "").ifBlank { "Video desde enlace" },
                        author = info.uploaderName ?: "YouTube",
                        duration = if (info.duration > 0) info.duration.toString() else "0",
                        url = info.url ?: query,
                        type = "video"
                    )
                ))
            }

            // Búsqueda real y en tiempo real directo contra YouTube (misma extracción que usa
            // la app NewPipe), en vez de instancias públicas de Piped/Invidious que suelen
            // estar caídas y devuelven resultados viejos o vacíos.
            val extractor = ServiceList.YouTube.getSearchExtractor(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                ""
            )
            extractor.fetchPage()

            val results = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { item ->
                    val id = extractVideoId(item.url)
                    if (id.isBlank()) return@mapNotNull null
                    VideoModel(
                        id = id,
                        title = (item.name ?: "").ifBlank { "Video" },
                        author = item.uploaderName ?: "YouTube",
                        duration = if (item.duration > 0) item.duration.toString() else "0",
                        url = item.url ?: "",
                        type = "video"
                    )
                }
                .take(15)

            if (results.isEmpty()) {
                return@withContext json.encodeToString(listOf(errorResult("Sin resultados para \"$query\"")))
            }
            json.encodeToString(results)
        } catch (e: Exception) {
            Log.e("SEARCH", "Fallo de búsqueda para \"$query\"", e)
            json.encodeToString(listOf(errorResult(e.message ?: "Error de extracción de YouTube")))
        }
    }

    // Dado un videoId recibido del cliente por Bluetooth, resuelve la URL directa
    // descargable (progresiva) del stream de menor resolución disponible.
    fun extractStreamUrl(videoId: String): String {
        return try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")

            fun smallestProgressive(streams: List<VideoStream>?): VideoStream? =
                streams.orEmpty()
                    .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .minByOrNull { it.resolution?.takeWhile(Char::isDigit)?.toIntOrNull() ?: Int.MAX_VALUE }

            // Se prefiere un stream "muxed" (video+audio) sobre uno solo-video: minimiza el
            // ancho de banda necesario en el enlace Bluetooth y es el formato con más
            // probabilidad de servirse sin requerir poToken.
            val chosen = smallestProgressive(info.videoStreams) ?: smallestProgressive(info.videoOnlyStreams)
            Log.d("SEARCH_DEBUG", "Stream elegido para $videoId: resolución=${chosen?.resolution ?: "ninguno disponible"}")
            chosen?.content ?: ""
        } catch (e: Exception) {
            Log.e("SEARCH_DEBUG", "Error extrayendo stream para $videoId", e)
            ""
        }
    }

    private fun isYouTubeUrl(text: String): Boolean =
        text.contains("youtube.com/watch") || text.contains("youtu.be/") ||
        text.contains("youtube.com/shorts/") || text.contains("music.youtube.com/watch")

    private fun extractVideoId(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            else -> url.substringAfterLast("/")
        }
    }

    private fun errorResult(message: String) = VideoModel(
        id = "error",
        title = "No se pudo completar la búsqueda",
        author = "Sistema",
        snippet = message,
        type = "error"
    )
}
