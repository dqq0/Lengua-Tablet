package com.example.sign_language_app

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.example.sign_language_app.camera.UsbCameraService
import com.example.sign_language_app.UsbPermissionReceiver
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult

class MainActivity: FlutterActivity() {
    private val CHANNEL_CAMERA = "com.example.sign_language_app/camera"
    private val CHANNEL_LANDMARKS = "com.example.sign_language_app/landmarks"

    private var mCameraService: UsbCameraService? = null
    private var eventSink: EventChannel.EventSink? = null
    private var flutterTextureId: Long? = null
    private var surfaceTextureEntry: io.flutter.view.TextureRegistry.SurfaceTextureEntry? = null

    private var surface: Surface? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // 1. Configurar MethodChannel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_CAMERA).setMethodCallHandler { call, result ->
            when (call.method) {
                "requestUsbPermission" -> {
                    requestManualPermission()
                    result.success(null)
                }
                "updateConfig" -> {
                    val width = call.argument<Int>("width") ?: 1920
                    val height = call.argument<Int>("height") ?: 1080
                    val fps = call.argument<Int>("fps") ?: 30
                    mCameraService?.updateConfig(width, height, fps)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        // 2. Configurar EventChannel (Landmarks)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_LANDMARKS).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                }
                override fun onCancel(arguments: Any?) {
                    eventSink = null
                }
            }
        )
        

        // 3. Crear Textura Flutter para Video
        surfaceTextureEntry = flutterEngine.renderer.createSurfaceTexture()
        flutterTextureId = surfaceTextureEntry!!.id()
        // Create Surface from Texture
        surface = Surface(surfaceTextureEntry!!.surfaceTexture())
        
        // Notificar ID a Flutter
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_CAMERA).invokeMethod("onTextureId", flutterTextureId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializar servicio
        mCameraService = UsbCameraService(this) { result: HolisticLandmarkerResult, emotion: String ->
            sendLandmarksToFlutter(result, emotion)
        }
        if (surface != null) {
            mCameraService?.setSurface(surface!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mCameraService?.release()
        surface?.release()
        surfaceTextureEntry?.release()
    }

    // --- Lógica de Hardware/Permisos ---

    private fun requestManualPermission() {
        UsbPermissionReceiver.callback = { device ->
            if (device != null) {
                mCameraService?.connectToDevice(device)
            } else {
                Toast.makeText(this, "Permiso Denegado", Toast.LENGTH_SHORT).show()
            }
        }
        
        val manager = getSystemService(android.content.Context.USB_SERVICE) as android.hardware.usb.UsbManager
        val deviceList = manager.deviceList
        val device = deviceList.values.firstOrNull { 
             it.deviceClass == 239 || it.interfaceCount > 0 
        } as? UsbDevice

        if (device != null) {
            if (manager.hasPermission(device)) {
                mCameraService?.connectToDevice(device)
            } else {
                UsbPermissionReceiver.requestPermission(this, device)
            }
        } else {
            Toast.makeText(this, "No se encontró cámara USB", Toast.LENGTH_LONG).show()
        }
    }

    // --- Enviar Data a Flutter ---
    
    private fun sendLandmarksToFlutter(result: HolisticLandmarkerResult, emotion: String) {
        if (eventSink == null) return

        val data = hashMapOf<String, Any>()
        data["emotion"] = emotion 
        
        val landmarksMap = hashMapOf<String, Any>()
        
        // 1. Face Landmarks
        // HolisticLandmarkerResult in some versions returns List directly
        val faceList = result.faceLandmarks()
        if (faceList != null && faceList.isNotEmpty()) {
             // Optimization: Only send x,y. Drop z.
             val face = faceList.map { lm ->
                 mapOf("x" to lm.x(), "y" to lm.y()) 
             }
             landmarksMap["faceLandmarks"] = face
        }
        
        // 2. Pose Landmarks
        val poseList = result.poseLandmarks()
        if (poseList != null && poseList.isNotEmpty()) {
             val pose = poseList.map { lm ->
                 mapOf("x" to lm.x(), "y" to lm.y()) 
             }
             landmarksMap["poseLandmarks"] = pose
        }
        
        // 3. Hands
        val leftList = result.leftHandLandmarks()
        if (leftList != null && leftList.isNotEmpty()) {
             val left = leftList.map { lm ->
                 mapOf("x" to lm.x(), "y" to lm.y()) 
             }
             landmarksMap["leftHandLandmarks"] = left
        }
        
        val rightList = result.rightHandLandmarks()
        if (rightList != null && rightList.isNotEmpty()) {
             val right = rightList.map { lm ->
                 mapOf("x" to lm.x(), "y" to lm.y()) 
             }
             landmarksMap["rightHandLandmarks"] = right
        }
        
        data["landmarks"] = landmarksMap
        
        runOnUiThread {
            eventSink?.success(data)
        }
    }
}
