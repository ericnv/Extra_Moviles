package com.example.extra_navavillareric

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

class TransferBlock(
    val sessionId: String,
    val blockIndex: Int,
    val offset: Long,
    val data: ByteArray,
    val isLast: Boolean
) {
    val checksum: Long = crc32(data)

    fun toByteArray(): ByteArray {
        val sessionBytes = sessionId.toByteArray(StandardCharsets.UTF_8)
        val header = ByteBuffer.allocate(4 + 2 + sessionBytes.size + 4 + 8 + 1 + 4 + 8)
        header.put("BTB1".toByteArray(StandardCharsets.UTF_8))
        header.putShort(sessionBytes.size.toShort())
        header.put(sessionBytes)
        header.putInt(blockIndex)
        header.putLong(offset)
        header.put(if (isLast) 1.toByte() else 0.toByte())
        header.putInt(data.size)
        header.putLong(checksum)
        val body = ByteArray(header.capacity() + data.size)
        header.flip()
        header.get(body, 0, header.remaining())
        System.arraycopy(data, 0, body, header.capacity(), data.size)
        return body
    }

    companion object {
        fun fromByteArray(payload: ByteArray): TransferBlock? {
            return try {
                val buffer = ByteBuffer.wrap(payload)
                val magic = ByteArray(4)
                buffer.get(magic)
                if (String(magic, StandardCharsets.UTF_8) != "BTB1") return null

                val sessionLength = buffer.short.toInt()
                val sessionBytes = ByteArray(sessionLength)
                buffer.get(sessionBytes)
                val sessionId = String(sessionBytes, StandardCharsets.UTF_8)
                val blockIndex = buffer.int
                val offset = buffer.long
                val isLast = buffer.get().toInt() == 1
                val dataSize = buffer.int
                val checksum = buffer.long
                val data = ByteArray(dataSize)
                buffer.get(data)

                TransferBlock(sessionId, blockIndex, offset, data, isLast).also {
                    if (it.checksum != checksum) return null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun crc32(data: ByteArray): Long {
            val crc = CRC32()
            crc.update(data)
            return crc.value
        }
    }
}
