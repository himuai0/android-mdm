package com.hackerai.rat.utils

/**
 * XOR obfuscation for string hiding
 */
object CryptoUtils {
    private val key = "H4ck3rA1_R@T_2025!".toByteArray()

    fun obfuscate(input: String): ByteArray {
        val data = input.toByteArray()
        return data.mapIndexed { i, b -> (b xor key[i % key.size]).toByte() }.toByteArray()
    }

    fun deobfuscate(data: ByteArray): String {
        return String(data.mapIndexed { i, b -> (b xor key[i % key.size]).toByte() }.toByteArray())
    }

    // Pre-obfuscated sensitive strings
    fun getServerUrl(): String = deobfuscate(byteArrayOf(
        // This will be replaced by the APK builder
    ))

    fun obfuscateString(input: String): String {
        val obfuscated = obfuscate(input)
        return obfuscated.joinToString(",") { it.toInt().toString() }
    }

    fun deobfuscateString(obfuscated: String): String {
        val bytes = obfuscated.split(",").map { it.toInt().toByte() }.toByteArray()
        return deobfuscate(bytes)
    }
}
