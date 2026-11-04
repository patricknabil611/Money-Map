package com.example.security

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Manages device-level security and hardware identity inspection.
 */
object DeviceSecurityManager {

  fun getAndroidId(context: Context): String {
    return try {
      Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
    } catch (_: Exception) {
      "unknown_id"
    }
  }

  fun getDeviceModel(): String = Build.MODEL ?: "Unknown Model"
  fun getDeviceManufacturer(): String = Build.MANUFACTURER ?: "Unknown"

  fun getDeviceInfoSummary(context: Context): String {
    val currentModel = getDeviceModel()
    val manufacturer = getDeviceManufacturer()
    return "$manufacturer $currentModel"
  }
}
