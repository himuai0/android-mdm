package com.hackerai.rat

object Config {
    var SERVER_URL = "${SERVER_URL}"
    var SERVER_PORT = ${SERVER_PORT}
    const val HEARTBEAT_INTERVAL = 30000L
    const val RECONNECT_BASE_DELAY = 1000L
    const val RECONNECT_MAX_DELAY = 60000L
    const val LOCATION_INTERVAL = 5000L
    const val SCREEN_FPS_DEFAULT = 2
}
