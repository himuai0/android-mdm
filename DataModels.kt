package com.hackerai.rat.models

data class Command(
    val id: String,
    val command: String,
    val params: Any? = null
)

data class LocationData(
    val lat: Double,
    val lng: Double,
    val accuracy: Float = 0f,
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f
)

data class KeylogEntry(
    val app: String,
    val text: String,
    val timestamp: Long
)

data class NotificationData(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
