package com.example.sign_language_app.camera

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.util.Log
import android.widget.Toast
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.UVCCamera
import com.jiangdg.uvc.IFrameCallback
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class UsbCameraService(
    private val activity: Activity,
    private val resultCallback: (HolisticLandmarkerResult, String) -> Unit 
) {
    // 1. Dependencies and Primitives
    // Removed EmotionRules
    private var holisticLandmarker: HolisticLandmarker? = null
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private var currentWidth = 640
    private var currentHeight = 480

    private var mUVCCamera: UVCCamera? = null
    private var mPreviewSurface: android.view.Surface? = null
    private var lastTimestamp: Long = 0L

    // 2. Frame Callback (Depends on dimensions)
    private val frameCallback = IFrameCallback { frame -> 
        processFrame(frame, currentWidth, currentHeight)
    }

    // 3. Listener (Depends on Executor, Camera, Callback)
    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
             activity.runOnUiThread {
                 Toast.makeText(activity, "Cámara USB Detectada", Toast.LENGTH_SHORT).show()
             }
             if (device != null && !mUSBMonitor.hasPermission(device)) {
                 mUSBMonitor.requestPermission(device)
             }
        }

        override fun onDetach(device: UsbDevice?) {
             activity.runOnUiThread {
                 Toast.makeText(activity, "Cámara Desconectada", Toast.LENGTH_SHORT).show()
             }
             closeCamera()
        }

        override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
             releaseCamera()
             backgroundExecutor.execute {
                 try {
                     mUVCCamera = UVCCamera()
                     mUVCCamera?.open(ctrlBlock)
                     
                     try {
                         mUVCCamera?.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG)
                     } catch (e: IllegalArgumentException) {
                         try {
                              mUVCCamera?.setPreviewSize(640, 480, UVCCamera.FRAME_FORMAT_MJPEG)
                         } catch (e2: IllegalArgumentException) {
                              // Fallback
                         }
                     }
                     
                     if (mPreviewSurface != null) {
                         mUVCCamera?.setPreviewDisplay(mPreviewSurface)
                         mUVCCamera?.startPreview()
                         // Force safe NV21
                         mUVCCamera?.setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_NV21)
                     }
                 } catch (e: Exception) {
                     Log.e("UsbCameraService", "Error open camera: ${e.message}")
                 }
             }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
             closeCamera()
        }
        
        override fun onCancel(device: UsbDevice?) {}
    }

    // 4. Monitor (Depends on Listener)
    private val mUSBMonitor: USBMonitor = USBMonitor(activity, onDeviceConnectListener)

    init {
        initMediaPipe()
        mUSBMonitor.register()
    }

    private fun initMediaPipe() {
        backgroundExecutor.execute {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("holistic_landmarker.task")
                    .setDelegate(Delegate.GPU) 
                    .build()

                val options = HolisticLandmarker.HolisticLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onHolisticResult)
                    .setErrorListener { e -> 
                        Log.e("MediaPipe", "Error: ${e.message}") 
                        isProcessing = false
                    }
                    .build()

                holisticLandmarker = HolisticLandmarker.createFromOptions(activity, options)
                Log.d("UsbCameraService", "MediaPipe Holistic inicializado ✔️")
            } catch (e: Exception) {
                Log.e("UsbCameraService", "Error iniciando MediaPipe: ${e.message}")
            }
        }
    }

    private fun onHolisticResult(result: HolisticLandmarkerResult, inputImage: com.google.mediapipe.framework.image.MPImage) {
        isProcessing = false
        resultCallback(result, "NEUTRO")
    }

    @Volatile
    private var isProcessing = false

    private fun processFrame(frame: ByteBuffer, width: Int, height: Int) {
        if (holisticLandmarker == null || isProcessing) return
        isProcessing = true
        
        try {
             val len = frame.capacity()
             val nv21 = ByteArray(len)
             frame.get(nv21)
             
             val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
             val out = ByteArrayOutputStream()
             // Use 50 quality for speed
             yuvImage.compressToJpeg(Rect(0, 0, width, height), 50, out)
             val imageBytes = out.toByteArray()
             
             val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
             }
             val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
             
             if (bitmap != null) {
                 val mpImage = BitmapImageBuilder(bitmap).build()
                 
                 // Ensure monotonically increasing timestamp
                 var timestamp = System.currentTimeMillis()
                 if (timestamp <= lastTimestamp) {
                     timestamp = lastTimestamp + 1
                 }
                 lastTimestamp = timestamp
                 
                 holisticLandmarker?.detectAsync(mpImage, timestamp)
             } else {
                 isProcessing = false
             }
        } catch (e: Exception) {
             Log.e("UsbCameraService", "Frame proc error: ${e.message}")
             isProcessing = false
        }
    }

    fun setSurface(surface: android.view.Surface) {
        mPreviewSurface = surface
        if (mUVCCamera != null) {
            try {
                mUVCCamera?.setPreviewDisplay(surface)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun updateConfig(width: Int, height: Int, fps: Int) {
        currentWidth = width
        currentHeight = height
    }

    fun connectToDevice(device: UsbDevice) {
        mUSBMonitor.requestPermission(device)
    }

    fun release() {
        releaseCamera()
        mUSBMonitor.unregister()
        mUSBMonitor.destroy()
        holisticLandmarker?.close()
        backgroundExecutor.shutdown()
    }
    
    private fun releaseCamera() {
        try {
            mUVCCamera?.stopPreview()
            mUVCCamera?.destroy()
            mUVCCamera = null
        } catch (e: Exception) {
            // ignore
        }
    }
    
    private fun closeCamera() {
        releaseCamera()
    }
}
