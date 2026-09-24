package com.example.afetmesh.data.mesh

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class VideoStreamManager(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    var isStreamingVideo = false
        private set

    fun startVideoStream(
        lifecycleOwner: LifecycleOwner,
        onFrameEncoded: (String) -> Unit
    ) {
        if (isStreamingVideo) return
        isStreamingVideo = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                var lastFrameTime = 0L

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    // Cap video stream frame rate to ~10 FPS for off-grid socket efficiency
                    if (now - lastFrameTime >= 100) {
                        lastFrameTime = now
                        val bitmap = imageProxy.toBitmap()
                        val rotatedBitmap = rotateBitmapIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
                        val scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 320, 240, true)

                        val outputStream = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 45, outputStream)
                        val byteArray = outputStream.toByteArray()
                        val base64Frame = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                        onFrameEncoded(base64Frame)
                    }
                    imageProxy.close()
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
                isStreamingVideo = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopVideoStream() {
        isStreamingVideo = false
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        fun decodeBase64ToBitmap(base64String: String): Bitmap? {
            return try {
                val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
