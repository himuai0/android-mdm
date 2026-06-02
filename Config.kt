package com.hackerai.rat

/**
 * Configuration - Modified by APK Builder
 */
object Config {
    var SERVER_URL = "http://192.168.1.100:3000"
    var SERVER_PORT = 3000
    const val HEARTBEAT_INTERVAL = 30000L
    const val RECONNECT_BASE_DELAY = 1000L
    const val RECONNECT_MAX_DELAY = 60000L
    const val LOCATION_INTERVAL = 5000L
    const val SCREEN_FPS_DEFAULT = 2
}
