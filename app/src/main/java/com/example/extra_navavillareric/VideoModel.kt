package com.example.extra_navavillareric

import kotlinx.serialization.Serializable

@Serializable
data class VideoModel(
    val id: String = "",
    val title: String = "Sin título",
    val author: String = "Desconocido",
    val duration: String = "0",
    val isSafe: Boolean = true,
    val url: String = "",
    val snippet: String = "",
    val type: String = "video"
)
