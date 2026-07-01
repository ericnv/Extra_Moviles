# Protocolo de Transferencia por Bloques (VBT)

Este documento describe la capa de transporte personalizada implementada sobre RFCOMM para garantizar la integridad de los datos de video.

## Estructura del Bloque (TransferBlock)
Cada bloque enviado por Bluetooth tiene la siguiente cabecera:
1. `Magic Bytes (4 bytes)`: Siempre "BTB1".
2. `Session Length (2 bytes)`: Longitud del ID de sesión.
3. `Session ID (Variable)`: Identificador único de la transferencia actual.
4. `Block Index (4 bytes)`: Número secuencial del bloque.
5. `Offset (8 bytes)`: Posición del byte en el archivo destino.
6. `Is Last (1 byte)`: Flag de finalización.
7. `Data Size (4 bytes)`: Tamaño del payload.
8. `Checksum (8 bytes)`: CRC32 de los datos.

## Control de Flujo
Para evitar desbordamientos en el hardware de dispositivos Samsung (S10/Tab A8):
- **Tamaño de bloque**: 8KB - 16KB.
- **Delay (Pacing)**: 25ms - 32ms entre bloques.
- **Agradecimientos (ACK)**: El protocolo soporta mensajes de tipo `TYPE_TRANSFER_ACK` para confirmar la recepción íntegra del bloque antes de proceder (opcional según congestión).
