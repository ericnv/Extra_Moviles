# YouTube Stream Bluetooth (YT Stream BT)

🇬🇧 English version below · 🇪🇸 Saltar a la versión en español

📚 **Documentación técnica detallada / Detailed technical docs**: La documentación profunda del protocolo, arquitectura MCP y gestión de buffers vive en la carpeta `README_IAS/`. Está diseñada para asistentes de IA. Este README es la visión general.

---

## 🇬🇧 English Version

**YouTube Stream Bluetooth (YT Stream BT)** is an innovative Android application that enables real-time YouTube video searching and streaming between two devices using only **Bluetooth Classic (RFCOMM)**. It is designed for scenarios where a "Client" device has no internet access but needs to consume content from a "Server" device that acts as an **MCP (Media Control Protocol) Gateway**.

The project is built with **Kotlin + Jetpack Compose**, following an **MVVM architecture**, and implements a custom **Verified Block Transfer** protocol to overcome Bluetooth bandwidth limitations and hardware constraints on devices like the Samsung S10 and Tab A8.

### 🔄 Recent Changes
*   **Real YouTube Extraction (NewPipeExtractor)**: Replaced the old multi-instance Piped/Invidious failover (public third-party proxies that were frequently down or serving stale results) with [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), the same library used by the NewPipe app. Search results are now fetched live and directly from YouTube, and any YouTube URL (watch, `youtu.be`, Shorts, Music) is resolved with real metadata instead of a guessed video ID.
*   **Ordered Block Delivery**: `sendData` used to fire each Bluetooth write on its own coroutine, so block order over the wire wasn't guaranteed under load — occasionally scrambling the received video file. Sends are now serialized through a `Mutex`-backed `sendDataOrdered`, guaranteeing blocks arrive in the exact order they were produced.
*   **Visible Playback Errors**: The client used to show a silent black player forever when the server failed to fetch a stream, because the error message updated a state that was hidden behind the player screen. Errors (server-side extraction failures and ExoPlayer's own `onPlayerError`) are now surfaced directly to the user instead of an unexplained black screen.
*   **Verified Block Transfer (VBT)**: Robust chunking system (8KB - 16KB blocks) with magic bytes (`BTB1`) and CRC32 checksums to prevent data corruption.
*   **Dual Progress Monitoring**: Real-time visual feedback on the Server side to track internet download vs. Bluetooth upload speeds.
*   **Adaptive Buffer Strategy**: The client waits for a validated 500KB buffer before starting playback to ensure MP4 headers are correctly parsed by ExoPlayer.
*   **Bandwidth-Aware Stream Selection**: The server now prefers the lowest-resolution *muxed* stream (video+audio together) available for a video, minimizing the data that has to cross the Bluetooth link while still keeping audio.
*   **Link Support**: Direct YouTube URL input is supported in the search bar, validated and resolved through NewPipeExtractor.

### ⚙️ Architecture
*   **Model**: Immutable data classes (`VideoModel`, `TransferBlock`) using `kotlinx.serialization`.
*   **ViewModel**: Activity-scoped logic for Bluetooth discovery, message routing, and stream management.
*   **View**: Pure Compose UI with theme switching (IPN/ESCOM styles) and Material 3 components.

---

## 🇪🇸 Versión en Español

**YouTube Stream Bluetooth (YT Stream BT)** es una aplicación Android innovadora que permite la búsqueda y reproducción de videos de YouTube en tiempo real entre dos dispositivos usando únicamente **Bluetooth Clásico (RFCOMM)**. Está diseñada para escenarios donde un dispositivo "Cliente" no tiene acceso a internet pero necesita consumir contenido a través de un dispositivo "Servidor" que actúa como un **Gateway MCP (Media Control Protocol)**.

El proyecto está desarrollado con **Kotlin + Jetpack Compose**, siguiendo una **arquitectura MVVM**, e implementa un protocolo personalizado de **Transferencia de Bloques Verificada** para superar las limitaciones de ancho de banda de Bluetooth y las restricciones de hardware en dispositivos como el Samsung S10 y la Tab A8.

### 🔄 Cambios Recientes
*   **Extracción real de YouTube (NewPipeExtractor)**: Se reemplazó el antiguo failover multi-instancia de Piped/Invidious (proxies públicos de terceros que solían estar caídos o devolvían resultados desactualizados) por [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), la misma librería que usa la app NewPipe. La búsqueda ahora es en tiempo real directo contra YouTube, y cualquier URL de YouTube (watch, `youtu.be`, Shorts, Music) se valida y resuelve con metadata real en vez de adivinar el ID del video.
*   **Envío de bloques en orden garantizado**: `sendData` lanzaba cada escritura Bluetooth en su propia corrutina, así que el orden real de los bloques en el cable no estaba garantizado bajo carga, corrompiendo ocasionalmente el video recibido. Ahora los envíos se serializan con un `Mutex` (`sendDataOrdered`), asegurando que los bloques lleguen en el mismo orden en que se generaron.
*   **Errores de reproducción visibles**: Antes, si el servidor fallaba al obtener el stream, el cliente se quedaba con el reproductor en negro para siempre, porque el mensaje de error actualizaba un estado oculto detrás de la pantalla del reproductor. Ahora los errores (fallas de extracción del servidor y `onPlayerError` de ExoPlayer) se muestran directamente al usuario en vez de una pantalla negra sin explicación.
*   **Transferencia de Bloques Verificada (VBT)**: Sistema de fragmentación robusto (bloques de 8KB - 16KB) con bytes mágicos (`BTB1`) y sumas de comprobación CRC32 para evitar la corrupción de datos.
*   **Monitoreo de Doble Progreso**: Feedback visual en tiempo real en el Servidor para rastrear la descarga de internet vs. la velocidad de subida por Bluetooth.
*   **Estrategia de Buffer Adaptativo**: El cliente espera un buffer validado de 500KB antes de iniciar la reproducción para asegurar que ExoPlayer lea correctamente los encabezados MP4.
*   **Selección de stream consciente del ancho de banda**: El servidor ahora prefiere el stream *muxed* (video+audio combinados) de menor resolución disponible, minimizando los datos que cruzan el enlace Bluetooth sin perder el audio.
*   **Soporte de Enlaces**: Se pueden pegar URLs directas de YouTube en la barra de búsqueda, validadas y resueltas vía NewPipeExtractor.

### ⚙️ Arquitectura
*   **Modelo**: Clases de datos inmutables (`VideoModel`, `TransferBlock`) usando `kotlinx.serialization`.
*   **ViewModel**: Lógica de descubrimiento Bluetooth, enrutamiento de mensajes y gestión de streams (Activity-scoped).
*   **Vista**: Interfaz Compose pura con cambio de temas (Estilos IPN/ESCOM) y componentes Material 3.

### 🚀 Stack Tecnológico
| Capa | Tecnología |
| :--- | :--- |
| **UI** | Jetpack Compose, Material 3 |
| **Reproductor** | Media3 ExoPlayer |
| **Red (Servidor)** | OkHttp 4, NewPipeExtractor |
| **Comunicación** | Bluetooth Classic (RFCOMM) |
| **Serialización** | Kotlinx Serialization (JSON) |
| **Arquitectura** | MVVM + Flow/Coroutines |

### 🐳 Estado Actual
*   **Funciona hoy**: Búsqueda real y en tiempo real vía NewPipeExtractor, transferencia por bloques verificada y en orden garantizado, monitoreo de barras de progreso en servidor, soporte de links directos, errores de reproducción visibles, y reproducción fluida en baja resolución optimizada para Bluetooth.
*   **Próximamente**: Soporte para listas de reproducción, caché persistente de videos en el cliente, y chat de texto simultáneo por el mismo canal BT.
