package com.example.sign_language_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class UsbPermissionReceiver : BroadcastReceiver() {
    companion object {
        var callback: ((UsbDevice?) -> Unit)? = null // Allow null to signal failure
        const val ACTION_USB_PERMISSION = "com.example.sign_language_app.USB_PERMISSION"

        fun requestPermission(context: Context, device: UsbDevice) {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val intent = Intent(context, UsbPermissionReceiver::class.java)
            intent.action = ACTION_USB_PERMISSION
            
            // Android 12+ (S) compatibility
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) 
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            else 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT

            // Android 14 specific: Package must be explicit for MUTABLE PendingIntents
            intent.setPackage(context.packageName)

            val permissionIntent = android.app.PendingIntent.getBroadcast(context, 0, intent, flags)
            manager.requestPermission(device, permissionIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (ACTION_USB_PERMISSION == intent.action) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                device?.let {
                    android.util.Log.d("UsbPermissionReceiver", "✅ Permission granted for ${it.deviceName}")
                    callback?.invoke(it)
                }
            } else {
                android.util.Log.e("UsbPermissionReceiver", "❌ Permission denied")
                callback?.invoke(null) // Notify MainActivity of denial
            }
        }
    }
}
