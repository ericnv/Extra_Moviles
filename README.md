# YouTube Stream Bluetooth (YT Stream BT)

🇬🇧 English version below · 🇪🇸 Saltar a la versión en español

📚 **Documentación técnica detallada / Detailed technical docs**: La documentación profunda del protocolo, arquitectura MCP y gestión de buffers vive en la carpeta `README_IAS/`. Está diseñada para asistentes de IA. Este README es la visión general.

---

## 🇬🇧 English Version

**YouTube Stream Bluetooth (YT Stream BT)** is an innovative Android application that enables real-time YouTube video searching and streaming between two devices using only **Bluetooth Classic (RFCOMM)**. It is designed for scenarios where a "Client" device has no internet access but needs to consume content from a "Server" device that acts as an **MCP (Media Control Protocol) Gateway**.

The project is built with **Kotlin + Jetpack Compose**, following an **MVVM architecture**, and implements a custom **Verified Block Transfer** protocol to overcome Bluetooth bandwidth limitations and hardware constraints on devices like the Samsung S10 and Tab A8.

### 🔄 Recent Changes
*   **Verified Block Transfer (VBT)**: Implemented a robust chunking system (8KB - 16KB blocks) with magic bytes (`BTB1`) and CRC32 checksums to prevent data corruption.
*   **Dual Progress Monitoring**: Added real-time visual feedback on the Server side to track internet download vs. Bluetooth upload speeds.
*   **Adaptive Buffer Strategy**: The client now waits for a validated 500KB buffer before starting playback to ensure MP4 headers are correctly parsed by ExoPlayer.
*   **Multi-Instance Failover**: The server rotates through 5 different YouTube API gateways (Piped, Invidious) to guarantee search results even if primary sources are throttled.
*   **Link Support**: Direct YouTube URL input is now supported in the search bar.

### ⚙️ Architecture
*   **Model**: Immutable data classes (`VideoModel`, `TransferBlock`) using `kotlinx.serialization`.
*   **ViewModel**: Activity-scoped logic for Bluetooth discovery, message routing, and stream management.
*   **View**: Pure Compose UI with theme switching (IPN/ESCOM styles) and Material 3 components.

---

## 🇪🇸 Versión en Español

**YouTube Stream Bluetooth (YT Stream BT)** es una aplicación Android innovadora que permite la búsqueda y reproducción de videos de YouTube en tiempo real entre dos dispositivos usando únicamente **Bluetooth Clásico (RFCOMM)**. Está diseñada para escenarios donde un dispositivo "Cliente" no tiene acceso a internet pero necesita consumir contenido a través de un dispositivo "Servidor" que actúa como un **Gateway MCP (Media Control Protocol)**.

El proyecto está desarrollado con **Kotlin + Jetpack Compose**, siguiendo una **arquitectura MVVM**, e implementa un protocolo personalizado de **Transferencia de Bloques Verificada** para superar las limitaciones de ancho de banda de Bluetooth y las restricciones de hardware en dispositivos como el Samsung S10 y la Tab A8.

### 🔄 Cambios Recientes
*   **Transferencia de Bloques Verificada (VBT)**: Implementación de un sistema de fragmentación robusto (bloques de 8KB - 16KB) con bytes mágicos (`BTB1`) y sumas de comprobación CRC32 para evitar la corrupción de datos.
*   **Monitoreo de Doble Progreso**: Se añadió feedback visual en tiempo real en el Servidor para rastrear la descarga de internet vs. la velocidad de subida por Bluetooth.
*   **Estrategia de Buffer Adaptativo**: El cliente ahora espera un buffer validado de 500KB antes de iniciar la reproducción para asegurar que ExoPlayer lea correctamente los encabezados MP4.
*   **Failover Multi-Instancia**: El servidor rota entre 5 gateways de API de YouTube (Piped, Invidious) para garantizar resultados de búsqueda incluso si las fuentes principales están saturadas.
*   **Soporte de Enlaces**: Ahora se pueden pegar URLs directas de YouTube en la barra de búsqueda.

### ⚙️ Arquitectura
*   **Modelo**: Clases de datos inmutables (`VideoModel`, `TransferBlock`) usando `kotlinx.serialization`.
*   **ViewModel**: Lógica de descubrimiento Bluetooth, enrutamiento de mensajes y gestión de streams (Activity-scoped).
*   **Vista**: Interfaz Compose pura con cambio de temas (Estilos IPN/ESCOM) y componentes Material 3.

### 🚀 Stack Tecnológico
| Capa | Tecnología |
| :--- | :--- |
| **UI** | Jetpack Compose, Material 3 |
| **Reproductor** | Media3 ExoPlayer |
| **Red (Servidor)** | OkHttp 4, YouTube Scraping |
| **Comunicación** | Bluetooth Classic (RFCOMM) |
| **Serialización** | Kotlinx Serialization (JSON) |
| **Arquitectura** | MVVM + Flow/Coroutines |

### 🐳 Estado Actual
*   **Funciona hoy**: Búsqueda multi-fuente, transferencia por bloques verificada, monitoreo de barras de progreso en servidor, soporte de links directos, y reproducción fluida en baja resolución (360p) optimizada para Bluetooth.
*   **Próximamente**: Soporte para listas de reproducción, caché persistente de videos en el cliente, y chat de texto simultáneo por el mismo canal BT.
