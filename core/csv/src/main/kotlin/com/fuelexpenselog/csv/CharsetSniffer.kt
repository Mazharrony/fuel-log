package com.fuelexpenselog.csv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Bytes to text, without silently mangling anyone's station names.
 *
 * A byte-order mark decides. Otherwise the bytes are tried as strict UTF-8, which rejects
 * anything malformed rather than replacing it. What is not UTF-8 is read as Windows-1252,
 * which is what Excel writes on a Western Windows machine and what older exports from other
 * apps tend to be. The preview names the guess, so a wrong one is visible, not buried.
 */
object CharsetSniffer {

    enum class Encoding(val charset: Charset) {
        UTF_8(Charsets.UTF_8),
        UTF_16LE(Charsets.UTF_16LE),
        UTF_16BE(Charsets.UTF_16BE),
        WINDOWS_1252(Charset.forName("windows-1252")),
    }

    data class Decoded(val text: String, val encoding: Encoding, val hadBom: Boolean)

    fun decode(bytes: ByteArray): Decoded {
        when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> return Decoded(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), Encoding.UTF_8, true)
            bytes.startsWith(0xFF, 0xFE) -> return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), Encoding.UTF_16LE, true)
            bytes.startsWith(0xFE, 0xFF) -> return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), Encoding.UTF_16BE, true)
        }
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            Decoded(strict.decode(ByteBuffer.wrap(bytes)).toString(), Encoding.UTF_8, false)
        } catch (notUtf8: CharacterCodingException) {
            Decoded(String(bytes, Encoding.WINDOWS_1252.charset), Encoding.WINDOWS_1252, false)
        }
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }
}
