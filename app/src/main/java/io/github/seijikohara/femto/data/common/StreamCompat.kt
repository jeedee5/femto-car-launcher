package io.github.seijikohara.femto.data.common

import java.io.ByteArrayOutputStream
import java.io.InputStream

/*
 * Android 10-12 stand-ins for InputStream.readNBytes, which Android's
 * java.io only gained at API 33.
 */

private const val READ_CHUNK_BYTES = 8 * 1024

/** Read up to [limit] bytes, stopping early at end of stream (readNBytes(int) semantics). */
internal fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream(minOf(limit, READ_CHUNK_BYTES))
    val buffer = ByteArray(minOf(limit, READ_CHUNK_BYTES).coerceAtLeast(1))
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(remaining, buffer.size))
        if (read < 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }
    return out.toByteArray()
}

/** Fill [buffer] from [offset] with up to [length] bytes; return how many were read (readNBytes(byte[], int, int) semantics). */
internal fun InputStream.readInto(
    buffer: ByteArray,
    offset: Int,
    length: Int,
): Int {
    var total = 0
    while (total < length) {
        val read = read(buffer, offset + total, length - total)
        if (read < 0) break
        total += read
    }
    return total
}
