package com.example.extra_navavillareric

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

object BTProtocol {
    const val TYPE_SEARCH_QUERY = 0x01
    const val TYPE_SEARCH_RESULTS = 0x02
    const val TYPE_VIDEO_CHUNK = 0x03
    const val TYPE_CONTROL_CMD = 0x04
    const val TYPE_TRANSFER_BLOCK = 0x05
    const val TYPE_TRANSFER_ACK = 0x06
    const val TYPE_TRANSFER_NACK = 0x07
    const val HEADER_SIZE = 5 // 1 byte type + 4 bytes length
}

class BluetoothManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow("Desconectado")
    val connectionState: StateFlow<String> = _connectionState

    private val _receivedData = MutableStateFlow("")
    val receivedData: StateFlow<String> = _receivedData

    private val _messageFlow = MutableSharedFlow<Pair<Int, ByteArray>>(replay = 0, extraBufferCapacity = 1000)
    val messageFlow: SharedFlow<Pair<Int, ByteArray>> = _messageFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun startServer() {
        scope.launch {
            try {
                val serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("VideoServer", MY_UUID)
                _connectionState.value = "Esperando..."
                socket = serverSocket?.accept()
                serverSocket?.close()
                _connectionState.value = "Servidor Conectado"
                listenToSocket()
            } catch (e: IOException) {
                _connectionState.value = "Error Servidor"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToServer(device: BluetoothDevice) {
        scope.launch {
            try {
                _connectionState.value = "Conectando..."
                socket = device.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket?.connect()
                _connectionState.value = "Cliente Conectado"
                listenToSocket()
            } catch (e: IOException) {
                _connectionState.value = "Error Conexión"
            }
        }
    }

    private suspend fun listenToSocket() {
        val inputStream = socket?.inputStream ?: return
        outputStream = socket?.outputStream
        try {
            while (true) {
                val header = ByteArray(BTProtocol.HEADER_SIZE)
                readExactly(inputStream, header)

                val type = header[0].toInt()
                val length = ByteBuffer.wrap(header, 1, 4).int

                val payload = ByteArray(length)
                readExactly(inputStream, payload)

                if (type == BTProtocol.TYPE_SEARCH_QUERY) {
                    _receivedData.value = "SEARCH: " + String(payload, StandardCharsets.UTF_8)
                } else if (type == BTProtocol.TYPE_SEARCH_RESULTS) {
                    _receivedData.value = "RESULTADOS: ${payload.size} bytes"
                }

                Log.d("BT_MANAGER", "Recibido tipo=$type, length=$length")
                val delivered = _messageFlow.tryEmit(Pair(type, payload))
                if (!delivered) {
                    Log.w("BT_MANAGER", "No se pudo emitir mensaje tipo=$type")
                }
            }
        } catch (e: IOException) {
            Log.w("BT_MANAGER", "Conexión cerrada", e)
            _connectionState.value = "Desconectado"
        }
    }

    private fun readExactly(inputStream: InputStream, buffer: ByteArray) {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            val result = inputStream.read(buffer, bytesRead, buffer.size - bytesRead)
            if (result == -1) throw IOException("EOF")
            bytesRead += result
        }
    }

    // Mutex en vez de "synchronized": serializa el ORDEN real de envío,
    // no solo evita escrituras solapadas. Antes, cada sendData() lanzaba su propia
    // corrutina en Dispatchers.IO y el orden de adquisición del lock no coincidía
    // necesariamente con el orden de llamada, lo que podía enviar los bloques del
    // video fuera de orden y corromper el archivo recibido en el cliente.
    private val sendMutex = Mutex()

    // Fire-and-forget: para mensajes sueltos disparados desde la UI (no forman parte
    // de una secuencia que deba preservar orden).
    fun sendData(type: Int, data: ByteArray) {
        scope.launch { sendDataOrdered(type, data) }
    }

    // Debe llamarse siempre desde la misma corrutina/secuencia cuando el orden importa
    // (p. ej. el bucle de streaming de video), ya que el suspend + mutex garantiza que
    // los envíos se sirvan en el orden en que se invoca esta función.
    suspend fun sendDataOrdered(type: Int, data: ByteArray) {
        sendMutex.withLock {
            try {
                val currentSocket = socket
                if (currentSocket == null || !currentSocket.isConnected) {
                    Log.w("BT", "No hay socket conectado para enviar tipo $type")
                    return@withLock
                }

                val out = currentSocket.outputStream
                outputStream = out

                val header = ByteBuffer.allocate(BTProtocol.HEADER_SIZE)
                    .put(type.toByte())
                    .putInt(data.size)
                    .array()
                out.write(header)
                out.write(data)
                out.flush()
                Log.d("BT_MANAGER", "Enviado tipo=$type, size=${data.size}")
            } catch (e: IOException) {
                Log.e("BT", "Error enviando tipo $type", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
}
