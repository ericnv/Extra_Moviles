package com.example.extra_navavillareric

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeSearchManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
        
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val searchEndpoints = listOf(
        "https://pipedapi.kavin.rocks/search?q=%s&filter=videos",
        "https://yewtu.be/api/v1/search?q=%s&type=video",
        "https://invidious.lunar.icu/api/v1/search?q=%s&type=video"
    )

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext "[]"
        
        if (query.contains("youtube.com/watch?v=") || query.contains("youtu.be/")) {
            val id = extractIdFromLink(query)
            if (id.isNotBlank()) {
                return@withContext json.encodeToString(listOf(
                    VideoModel(id = id, title = "Video desde enlace", author = "YouTube", url = query, type = "video")
                ))
            }
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        for (endpointTemplate in searchEndpoints) {
            val url = String.format(endpointTemplate, encodedQuery)
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: ""
                    val results = if (url.contains("piped")) parsePipedResults(body) else parseInvidiousResults(body)
                    if (results.isNotEmpty()) return@withContext json.encodeToString(results.take(15))
                }
            } catch (e: Exception) { Log.e("SEARCH", "Fail: $url") }
        }

        return@withContext json.encodeToString(scrapeYouTubeSearch(encodedQuery))
    }

    private fun extractIdFromLink(link: String): String {
        return if (link.contains("v=")) link.substringAfter("v=").substringBefore("&")
        else link.substringAfterLast("/")
    }

    private fun parsePipedResults(body: String): List<VideoModel> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val content = root["content"]?.jsonArray ?: return emptyList()
            content.mapNotNull { item ->
                val obj = item.jsonObject
                val vUrl = obj["url"]?.jsonPrimitive?.content ?: ""
                val id = vUrl.substringAfter("v=", "").ifBlank { vUrl.substringAfterLast("/") }
                if (id.isBlank()) return@mapNotNull null
                VideoModel(id = id, title = obj["title"]?.jsonPrimitive?.content ?: "Video", 
                    author = obj["uploaderName"]?.jsonPrimitive?.content ?: "Canal", url = "https://www.youtube.com/watch?v=$id")
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseInvidiousResults(body: String): List<VideoModel> {
        return try {
            val array = json.parseToJsonElement(body).jsonArray
            array.map { item ->
                val obj = item.jsonObject
                VideoModel(id = obj["videoId"]?.jsonPrimitive?.content ?: "", 
                    title = obj["title"]?.jsonPrimitive?.content ?: "Video",
                    author = obj["author"]?.jsonPrimitive?.content ?: "YouTube")
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun scrapeYouTubeSearch(encodedQuery: String): List<VideoModel> {
        val results = mutableListOf<VideoModel>()
        try {
            val url = "https://www.youtube.com/results?search_query=$encodedQuery"
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                val pattern = Pattern.compile("\\{\"videoId\":\"([a-zA-Z0-9_-]{11})\",\"thumbnail\":.*?\"title\":\\{\"runs\":\\[\\{\"text\":\"(.*?)\"\\}\\]")
                val matcher = pattern.matcher(html)
                var count = 0
                while (matcher.find() && count < 10) {
                    val id = matcher.group(1) ?: continue
                    val title = matcher.group(2)?.replace("\\u0026", "&") ?: "Video"
                    results.add(VideoModel(id = id, title = title, author = "YouTube", type = "video"))
                    count++
                }
            }
        } catch (e: Exception) { }
        return results
    }

    fun extractStreamUrl(jsonResponse: String): String {
        Log.d("SEARCH_DEBUG", "Analizando respuesta para extraer Stream URL...")
        return try {
            val root = json.parseToJsonElement(jsonResponse).jsonObject
            val videoStreams = root["videoStreams"]?.jsonArray
            
            if (videoStreams != null && videoStreams.isNotEmpty()) {
                Log.d("SEARCH_DEBUG", "Encontrados ${videoStreams.size} streams en Piped")
                val stream = videoStreams.map { it.jsonObject }.filter {
                    val mime = it["mimeType"]?.jsonPrimitive?.content ?: ""
                    val quality = it["quality"]?.jsonPrimitive?.content ?: ""
                    mime.contains("video/mp4") && !mime.contains("audio") && 
                    (quality.contains("144p") || quality.contains("240p") || quality.contains("360p"))
                }.minByOrNull { 
                    it["quality"]?.jsonPrimitive?.content?.replace("p", "")?.toIntOrNull() ?: 360
                } ?: videoStreams.firstOrNull { it.jsonObject["mimeType"]?.jsonPrimitive?.content?.contains("video/mp4") == true }?.jsonObject
                
                val url = stream?.get("url")?.jsonPrimitive?.content ?: ""
                if (url.isNotBlank()) Log.d("SEARCH_DEBUG", "Stream URL de Piped extraída")
                return url
            }
            
            val formats = root["formatStreams"]?.jsonArray
            if (formats != null) {
                Log.d("SEARCH_DEBUG", "Buscando en formatos de Invidious...")
                val url = formats.firstOrNull { 
                    it.jsonObject["container"]?.jsonPrimitive?.content == "mp4" 
                }?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
                if (url.isNotBlank()) Log.d("SEARCH_DEBUG", "Stream URL de Invidious extraída")
                return url
            }

            Log.e("SEARCH_DEBUG", "No se encontró URL válida en el JSON")
            ""
        } catch (e: Exception) { 
            Log.e("SEARCH_DEBUG", "Error parseando stream JSON", e)
            "" 
        }
    }
}
