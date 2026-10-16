package io.github.yulbax.erkin.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun InputStream.readBounded(maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        require(total <= maxBytes) { "Data exceeds ${maxBytes / 1024 / 1024} MB" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
