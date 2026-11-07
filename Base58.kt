package com.alphatrade.ai.phantom

import java.math.BigInteger

/** Minimal Bitcoin/Solana base58 codec. Rejects characters outside the alphabet. */
internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val radix = BigInteger.valueOf(58L)

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var zeros = 0
        while (zeros < input.size && input[zeros] == 0.toByte()) zeros++
        var value = BigInteger(1, input)
        val out = StringBuilder()
        while (value.signum() > 0) {
            val parts = value.divideAndRemainder(radix)
            out.append(ALPHABET[parts[1].toInt()])
            value = parts[0]
        }
        repeat(zeros) { out.append(ALPHABET[0]) }
        return out.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        require(input.isNotEmpty()) { "Base58 value is empty" }
        var zeros = 0
        while (zeros < input.length && input[zeros] == ALPHABET[0]) zeros++
        var value = BigInteger.ZERO
        input.forEach { char ->
            val digit = ALPHABET.indexOf(char)
            require(digit >= 0) { "Invalid base58 character" }
            value = value.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }
        val magnitude = if (value.signum() == 0) ByteArray(0) else value.toByteArray().let { bytes ->
            if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }
        return ByteArray(zeros) + magnitude
    }
}
