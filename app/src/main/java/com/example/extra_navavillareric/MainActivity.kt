package com.example.extra_navavillareric

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Public
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.extra_navavillareric.ui.theme.AppThemeType
import com.example.extra_navavillareric.ui.theme.Extra_NavaVillarEricTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private val searchManager = YouTubeSearchManager()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Estados para monitoreo en el servidor
    private var serverDownloadBytes by mutableStateOf(0L)
    private var serverUploadBytes by mutableStateOf(0L)
    private var serverTotalBytes by mutableStateOf(0L)
    private var serverStatusMsg by mutableStateOf("Servidor listo")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothManager = BluetoothManager(this)
        
        // MCP Server Core: Escucha y procesa en tiempo real
        lifecycleScope.launch {
            bluetoothManager.messageFlow.collect { (type, data) ->
                when (type) {
                    BTProtocol.TYPE_SEARCH_QUERY -> {
                        val query = String(data, StandardCharsets.UTF_8)
                        Log.d("SERVER", "Recibida consulta MCP: $query")
                        
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                // Simular procesamiento MCP
                                Log.d("SERVER", "Buscando en YouTube: $query")
                                val resultsJson = searchManager.search(query)
                                
                                if (resultsJson == "[]") {
                                    Log.w("SERVER", "No se encontraron resultados para: $query")
                                } else {
                                    Log.d("SERVER", "Enviando resultados (${resultsJson.length} caracteres)")
                                }

                                bluetoothManager.sendData(BTProtocol.TYPE_SEARCH_RESULTS, resultsJson.toByteArray(StandardCharsets.UTF_8))
                            } catch (e: Exception) {
                                Log.e("SERVER", "Error en búsqueda MCP", e)
                                bluetoothManager.sendData(BTProtocol.TYPE_SEARCH_RESULTS, "[]".toByteArray(StandardCharsets.UTF_8))
                            }
                        }
                    }
                    BTProtocol.TYPE_CONTROL_CMD -> {
                        val cmd = String(data, StandardCharsets.UTF_8)
                        Log.d("SERVER_CMD", "Comando recibido en Servidor: $cmd")
                        if (cmd.startsWith("STREAM:")) {
                            startStreamingRealVideo(cmd.substring(7))
                        }
                    }
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            var currentTheme by remember { mutableStateOf(AppThemeType.GUINDA) }
            var isDarkMode by remember { mutableStateOf(false) }
            var currentScreen by remember { mutableStateOf("home") }
            var permissionsGranted by remember { mutableStateOf(false) }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                permissionsGranted = results.values.all { it }
            }

            LaunchedEffect(Unit) {
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (permissions.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }) {
                    permissionsGranted = true
                } else {
                    permissionLauncher.launch(permissions)
                }
            }

            Extra_NavaVillarEricTheme(darkTheme = isDarkMode, themeType = currentTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!permissionsGranted) {
                        PermissionScreen { permissionLauncher.launch(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }
                    } else {
                        Column(modifier = Modifier.padding(innerPadding)) {
                            ThemeControls(currentTheme, { currentTheme = it }, isDarkMode, { isDarkMode = it })
                            val connectionState by bluetoothManager.connectionState.collectAsState()
                            Text("BT: $connectionState", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)

                            when (currentScreen) {
                                "home" -> HomeScreen { currentScreen = it }
                                "client" -> ClientScreen(bluetoothManager) { currentScreen = "home" }
                                "server" -> ServerScreen(
                                    bluetoothManager, 
                                    serverDownloadBytes, 
                                    serverUploadBytes, 
                                    serverTotalBytes,
                                    serverStatusMsg,
                                    { currentScreen = "home" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startStreamingRealVideo(videoId: String) {
        Log.d("SERVER_FLOW", "Iniciando proceso para videoID: $videoId")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Reset de progreso visible inmediatamente
                withContext(Dispatchers.Main) {
                    serverDownloadBytes = 0
                    serverUploadBytes = 0
                    serverTotalBytes = 1
                    serverStatusMsg = "Buscando video en YouTube..."
                }

                val endpoints = listOf(
                    "https://pipedapi.kavin.rocks/streams/$videoId",
                    "https://api.piped.dev/streams/$videoId",
                    "https://pipedapi.riverside.rocks/streams/$videoId",
                    "https://yewtu.be/api/v1/videos/$videoId"
                )

                var streamUrl = ""
                for (url in endpoints) {
                    try {
                        Log.d("SERVER_FLOW", "Consultando metadata en: $url")
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build()
                        
                        httpClient.newCall(request).execute().use { response ->
                            val responseBody = response.body?.string() ?: ""
                            if (response.isSuccessful && responseBody.isNotBlank()) {
                                Log.d("SERVER_FLOW", "Respuesta metadata recibida (${responseBody.length} bytes)")
                                streamUrl = searchManager.extractStreamUrl(responseBody)
                                if (streamUrl.isNotBlank()) {
                                    Log.d("SERVER_FLOW", "Stream URL obtenida con éxito desde $url")
                                    return@use
                                }
                            } else {
                                Log.w("SERVER_FLOW", "Endpoint $url devolvió código ${response.code}")
                            }
                        }
                        if (streamUrl.isNotBlank()) break
                    } catch (e: Exception) {
                        Log.w("SERVER_FLOW", "Fallo consulta metadata: $url", e)
                    }
                }

                if (streamUrl.isBlank()) {
                    Log.e("SERVER_FLOW", "Error: No se pudo obtener la URL de descarga directa")
                    withContext(Dispatchers.Main) { serverStatusMsg = "Error: Video no encontrado" }
                    bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "ERROR_STREAM".toByteArray(StandardCharsets.UTF_8))
                    return@launch
                }

                Log.d("SERVER_FLOW", "URL FINAL DE DESCARGA: $streamUrl")
                withContext(Dispatchers.Main) { serverStatusMsg = "Conectando al flujo..." }
                val videoRequest = Request.Builder().url(streamUrl).build()
                httpClient.newCall(videoRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("SERVER_FLOW", "Error al abrir el stream de video: ${response.code}")
                        withContext(Dispatchers.Main) { serverStatusMsg = "Error HTTP: ${response.code}" }
                        bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "ERROR_STREAM".toByteArray(StandardCharsets.UTF_8))
                        return@launch
                    }

                    val totalSize = response.body?.contentLength()?.coerceAtLeast(1) ?: 1L
                    withContext(Dispatchers.Main) {
                        serverTotalBytes = totalSize
                        serverStatusMsg = "Descargando y Enviando..."
                        Log.d("SERVER_FLOW", "Descarga iniciada. Tamaño: $totalSize bytes")
                    }

                    val inputStream = response.body?.byteStream() ?: throw Exception("Cuerpo de respuesta vacío")
                    val sessionId = "sess-${System.currentTimeMillis()}"

                    bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "SESSION_START:$sessionId".toByteArray(StandardCharsets.UTF_8))
                    delay(100)
                    bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "SIZE:$totalSize".toByteArray(StandardCharsets.UTF_8))
                    delay(100)
                    bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "START_STREAM".toByteArray(StandardCharsets.UTF_8))

                    val chunkSize = 16 * 1024
                    val buffer = ByteArray(chunkSize)
                    var bytesRead: Int
                    var blockIndex = 0

                    while (true) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead <= 0) break
                        
                        serverDownloadBytes += bytesRead
                        
                        val chunk = buffer.copyOf(bytesRead)
                        val block = TransferBlock(sessionId, blockIndex, serverUploadBytes, chunk, false)
                        bluetoothManager.sendData(BTProtocol.TYPE_TRANSFER_BLOCK, block.toByteArray())
                        
                        serverUploadBytes += bytesRead
                        blockIndex += 1
                        
                        // Delay para estabilidad Samsung S10/Tab A8
                        delay(25) 
                    }

                    val lastBlock = TransferBlock(sessionId, blockIndex, serverUploadBytes, ByteArray(0), true)
                    bluetoothManager.sendData(BTProtocol.TYPE_TRANSFER_BLOCK, lastBlock.toByteArray())
                    bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "END_STREAM".toByteArray(StandardCharsets.UTF_8))
                    withContext(Dispatchers.Main) { serverStatusMsg = "Transferencia Exitosa (100%)" }
                    Log.d("SERVER_FLOW", "Transferencia completada al 100% de $totalSize bytes")
                }
            } catch (e: Exception) {
                Log.e("SERVER_FLOW", "Error crítico en el proceso de descarga/envío", e)
                withContext(Dispatchers.Main) { serverStatusMsg = "Fallo Crítico: ${e.message}" }
                bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "ERROR_STREAM".toByteArray(StandardCharsets.UTF_8))
            }
        }
    }
}

@Composable
fun ClientScreen(bluetoothManager: BluetoothManager, onBack: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val pairedDevices = bluetoothManager.getPairedDevices()
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var videoResults by remember { mutableStateOf<List<VideoModel>>(emptyList()) }
    var playingVideo by remember { mutableStateOf<VideoModel?>(null) }
    var selectedResult by remember { mutableStateOf<VideoModel?>(null) }
    var searchStatus by remember { mutableStateOf("Esperando búsqueda...") }
    var totalBytesExpected by remember { mutableStateOf(0L) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var blocksReceived by remember { mutableStateOf(0) }
    var bytesReceived by remember { mutableStateOf(0L) }
    var isDownloadComplete by remember { mutableStateOf(false) }
    val tempFile = remember { File(context.cacheDir, "bt_video.mp4") }
    
    // Instancia de Json configurada para ser robusta
    val jsonDecoder = remember { Json { ignoreUnknownKeys = true; coerceInputValues = true } }

    LaunchedEffect(Unit) {
        bluetoothManager.messageFlow.collect { (type, data) ->
            when (type) {
                BTProtocol.TYPE_SEARCH_RESULTS -> {
                    try {
                        val jsonStr = String(data, StandardCharsets.UTF_8)
                        Log.d("CLIENT", "JSON recibido (${data.size} bytes)")
                        val results = jsonDecoder.decodeFromString<List<VideoModel>>(jsonStr)
                        
                        if (results.any { it.id == "error" }) {
                            searchStatus = "El servidor no tiene internet o las APIs fallaron"
                            videoResults = emptyList()
                        } else {
                            videoResults = results
                            searchStatus = "Resultados listos: ${videoResults.size}"
                        }
                    } catch (e: Exception) {
                        searchStatus = "Error de comunicación: Datos corruptos"
                        Log.e("CLIENT", "Error parse", e)
                    }
                }
                BTProtocol.TYPE_CONTROL_CMD -> {
                    val cmd = String(data, StandardCharsets.UTF_8)
                    if (cmd.startsWith("SESSION_START:")) {
                        currentSessionId = cmd.substringAfter(":")
                        if (tempFile.exists()) tempFile.delete()
                        bytesReceived = 0
                        blocksReceived = 0
                        isDownloadComplete = false
                    } else if (cmd.startsWith("SIZE:")) {
                        totalBytesExpected = cmd.substring(5).toLongOrNull() ?: 0L
                    } else if (cmd == "START_STREAM") {
                        searchStatus = "Recibiendo video por bloques..."
                    } else if (cmd == "END_STREAM") {
                        isDownloadComplete = true
                        searchStatus = "Transferencia finalizada"
                    } else if (cmd == "ERROR_STREAM") {
                        searchStatus = "No se pudo obtener el stream del video"
                    }
                }
                BTProtocol.TYPE_TRANSFER_BLOCK -> {
                    try {
                        val block = TransferBlock.fromByteArray(data)
                        if (block == null) {
                            bluetoothManager.sendData(BTProtocol.TYPE_TRANSFER_NACK, "bad-block".toByteArray(StandardCharsets.UTF_8))
                            return@collect
                        }
                        FileOutputStream(tempFile, true).use { it.write(block.data) }
                        bytesReceived += block.data.size
                        blocksReceived += 1
                        bluetoothManager.sendData(BTProtocol.TYPE_TRANSFER_ACK, block.blockIndex.toString().toByteArray(StandardCharsets.UTF_8))
                        if (bytesReceived > 10000 && searchStatus != "Recibiendo video...") {
                            searchStatus = "Recibiendo video..."
                        }
                        if (block.isLast) {
                            isDownloadComplete = true
                            searchStatus = "Buffer listo para reproducir"
                        }
                    } catch (e: Exception) {
                        Log.e("CLIENT", "Error escribiendo bloque", e)
                    }
                }
                BTProtocol.TYPE_TRANSFER_ACK -> {
                    Log.d("CLIENT", "ACK recibido: ${String(data, StandardCharsets.UTF_8)}")
                }
                BTProtocol.TYPE_TRANSFER_NACK -> {
                    Log.w("CLIENT", "NACK recibido: ${String(data, StandardCharsets.UTF_8)}")
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedResult != null) {
            ResultDetailScreen(selectedResult!!, onBack = { selectedResult = null })
        } else if (playingVideo != null) {
            VideoPlayerScreen(playingVideo!!, tempFile, bytesReceived, isDownloadComplete) { playingVideo = null }
        } else {
            Button(onClick = onBack) { Text("Volver") }
            if (selectedDevice == null) {
                Text("Vincular Servidor:", style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(pairedDevices) { device ->
                        @SuppressLint("MissingPermission")
                        val deviceName = device.name ?: device.address
                        Card(modifier = Modifier.fillMaxWidth().padding(4.dp).clickable { 
                            selectedDevice = device
                            searchStatus = "Conectando con $deviceName..."
                            bluetoothManager.connectToServer(device)
                        }) { Text(deviceName, modifier = Modifier.padding(16.dp)) }
                    }
                }
            } else {
                TextField(value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("¿Qué quieres ver hoy?") },
                    trailingIcon = { IconButton(onClick = {
                        if (searchQuery.isBlank()) return@IconButton
                        videoResults = emptyList()
                        searchStatus = "Buscando '$searchQuery'..."
                        bluetoothManager.sendData(BTProtocol.TYPE_SEARCH_QUERY, searchQuery.toByteArray(StandardCharsets.UTF_8))
                    }) { Icon(Icons.Default.Search, null) } }
                )
                
                Text(searchStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (videoResults.isEmpty() && searchStatus.contains("No se")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )

                if (bytesReceived > 0) {
                    val progress = if (totalBytesExpected > 0) bytesReceived.toFloat() / totalBytesExpected else 0f
                    Text("Recibiendo: ${(bytesReceived / 1024)} KB / ${(totalBytesExpected / 1024)} KB", color = MaterialTheme.colorScheme.secondary)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(videoResults) { video ->
                        VideoResultCard(video) {
                            if (video.type == "web") {
                                selectedResult = video
                                searchStatus = "Mostrando resultado web"
                                return@VideoResultCard
                            }

                            playingVideo = video
                            searchStatus = "Solicitando stream: ${video.title}"
                            bluetoothManager.sendData(BTProtocol.TYPE_CONTROL_CMD, "STREAM:${video.id}".toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultDetailScreen(result: VideoModel, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Volver") }
        Spacer(modifier = Modifier.height(12.dp))
        Text(result.title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(result.author, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(result.snippet.ifBlank { "Este resultado fue recibido desde el servidor por Bluetooth." }, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Fuente", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(result.url.ifBlank { "Sin URL disponible" }, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun VideoPlayerScreen(video: VideoModel, file: File, bytes: Long, isComplete: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var playerInitialized by remember { mutableStateOf(false) }
    var playbackStatus by remember { mutableStateOf("Esperando datos...") }
    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }

    // Log para depurar qué está pasando con el archivo
    LaunchedEffect(bytes, isComplete) {
        val fileSize = if (file.exists()) file.length() else 0L
        Log.d("PLAYER_DEBUG", "Bytes recibidos: $bytes, Tamaño archivo: $fileSize, Completo: $isComplete")

        // Estrategia: Solo intentar reproducir cuando tengamos una base sólida (500KB) o esté completo
        // 500KB es suficiente para que Media3 identifique los headers del MP4 (moov atom)
        if (!playerInitialized && (bytes > 500_000L || isComplete) && fileSize > 50_000L) {
            try {
                playbackStatus = "Cargando archivo..."
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                playerInitialized = true
                playbackStatus = if (isComplete) "Reproduciendo (Completo)" else "Reproduciendo (Streaming)"
            } catch (e: Exception) {
                Log.e("PLAYER_DEBUG", "Error al inicializar ExoPlayer", e)
                playbackStatus = "Error de formato: ${e.message}"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Volver") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(video.title, style = MaterialTheme.typography.titleMedium)
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Black)
        ) {
            if (!playerInitialized) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Buffering: ${(bytes / 1024)} KB", color = androidx.compose.ui.graphics.Color.White)
                        Text(playbackStatus, color = androidx.compose.ui.graphics.Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Información de progreso
        Text("Estado: $playbackStatus", style = MaterialTheme.typography.bodySmall)
        if (bytes > 0) {
            LinearProgressIndicator(
                progress = { if (isComplete) 1f else (bytes % 1000000L) / 1000000f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    }
}

private fun getMediaDuration(file: File): Long? {
    if (!file.exists() || file.length() < 10_000) return null
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        retriever.release()
        duration
    } catch (e: Exception) {
        Log.e("PLAYER", "No se pudo leer la duración del archivo", e)
        null
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).coerceAtLeast(1)
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${seconds}s"
}

@Composable
fun VideoResultCard(video: VideoModel, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (video.type == "web") Icons.Default.Public else Icons.Default.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                if (video.snippet.isNotBlank()) {
                    Text(video.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                }
                Text("${video.author} • ${if (video.duration.isBlank() || video.duration == "0") "web" else "${video.duration}s"}", style = MaterialTheme.typography.bodySmall)
                Text(video.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Permisos necesarios", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onGrant) { Text("Configurar") }
    }
}

@Composable
fun ThemeControls(theme: AppThemeType, onTheme: (AppThemeType) -> Unit, dark: Boolean, onDark: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { onTheme(if (theme == AppThemeType.GUINDA) AppThemeType.AZUL else AppThemeType.GUINDA) }) {
            Text(if (theme == AppThemeType.GUINDA) "Tema IPN" else "Tema ESCOM")
        }
        Switch(checked = dark, onCheckedChange = onDark)
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("YT Stream BT", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Button(onClick = { onNavigate("server") }, modifier = Modifier.fillMaxWidth(0.6f)) { Text("SERVIDOR") }
        Button(onClick = { onNavigate("client") }, modifier = Modifier.fillMaxWidth(0.6f)) { Text("CLIENTE") }
    }
}

@Composable
fun ServerScreen(
    bluetoothManager: BluetoothManager, 
    downloaded: Long, 
    uploaded: Long, 
    total: Long, 
    statusMsg: String,
    onBack: () -> Unit
) {
    val log by bluetoothManager.receivedData.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Volver") }
        Button(onClick = { bluetoothManager.startServer() }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar Servidor") }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Estado: $statusMsg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("YouTube -> Servidor: ${(downloaded / 1024)} KB / ${(total / 1024)} KB")
        val downloadProgress = if (total > 0) downloaded.toFloat() / total else 0f
        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text("Servidor -> Cliente (BT): ${(uploaded / 1024)} KB / ${(total / 1024)} KB")
        val uploadProgress = if (total > 0) uploaded.toFloat() / total else 0f
        LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(16.dp))
        Text("Consola de Peticiones:")
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) { Text(log, modifier = Modifier.padding(16.dp)) }
    }
}
