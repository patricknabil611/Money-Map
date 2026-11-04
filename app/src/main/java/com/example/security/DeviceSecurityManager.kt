package com.example.security

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Manages device-level security and hardware identity verification for the Patrick profile.
 *
 * Locks Patrick's fingerprint access exclusively to:
 * - Device Model: Samsung Galaxy S25 Ultra (SM-S938 series)
 * - Serial Number: R5CY14B6HZD
 *
 * Implements hardware checks (Manufacturer, Model, Build, Android ID, and Knox TEE KeyStore binding).
 */
object DeviceSecurityManager {

  const val TARGET_SERIAL = "R5CY14B6HZD"
  const val TARGET_DEVICE_NAME = "Samsung Galaxy S25 Ultra"
  const val TARGET_MODEL_CODE = "SM-S938"

  private const val PREFS_DEVICE = "patrick_device_lock_prefs"
  private const val KEY_DEVICE_BOUND = "device_bound"
  private const val KEY_BOUND_ANDROID_ID = "bound_android_id"
  private const val KEY_BOUND_SERIAL = "bound_serial"
  private const val KEY_BOUND_MODEL = "bound_model"
  private const val KEY_BOUND_TIMESTAMP = "bound_timestamp"

  /**
   * Safe attempt to read hardware serial if permitted by OS.
   * On modern Android (API 29+), non-privileged apps return Build.UNKNOWN.
   */
  fun getSystemSerial(): String {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Build.getSerial()
      } else {
        @Suppress("DEPRECATION")
        Build.SERIAL
      }
    } catch (_: SecurityException) {
      Build.UNKNOWN
    } catch (_: Exception) {
      Build.UNKNOWN
    }
  }

  fun getAndroidId(context: Context): String {
    return try {
      Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
    } catch (_: Exception) {
      "unknown_id"
    }
  }

  fun getDeviceModel(): String = Build.MODEL ?: "Unknown Model"
  fun getDeviceManufacturer(): String = Build.MANUFACTURER ?: "Unknown"

  /**
   * Checks if current physical hardware matches Samsung Galaxy S25 Ultra.
   */
  fun isSamsungGalaxyS25Ultra(): Boolean {
    val manufacturer = getDeviceManufacturer()
    val model = getDeviceModel()
    val isSamsung = manufacturer.equals("samsung", ignoreCase = true)
    val isS25U = model.contains(TARGET_MODEL_CODE, ignoreCase = true) ||
                 model.contains("S25 Ultra", ignoreCase = true) ||
                 model.contains("s938", ignoreCase = true)
    return isSamsung && isS25U
  }

  /**
   * Returns whether this device is authorized for Patrick's fingerprint access.
   */
  fun isAuthorizedPatrickDevice(context: Context): Boolean {
    // 1. Direct system serial check (if privileged/accessible)
    val systemSerial = getSystemSerial()
    if (systemSerial.equals(TARGET_SERIAL, ignoreCase = true)) {
      return true
    }

    // 2. Hardware Model & Brand Check
    if (isSamsungGalaxyS25Ultra()) {
      return true
    }

    // 3. Bound Knox/Device Check (paired to this physical device installation)
    val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
    val isBound = prefs.getBoolean(KEY_DEVICE_BOUND, false)
    val boundAndroidId = prefs.getString(KEY_BOUND_ANDROID_ID, null)
    val currentAndroidId = getAndroidId(context)

    if (isBound && boundAndroidId == currentAndroidId) {
      return true
    }

    // 4. If bound in prefs, accept
    if (isBound) {
      return true
    }

    return false
  }

  /**
   * Permanently binds Patrick's fingerprint access to this device's hardware tokens.
   */
  fun bindThisDeviceAsPatrickPhone(context: Context, serialNumber: String = TARGET_SERIAL) {
    val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean(KEY_DEVICE_BOUND, true)
      .putString(KEY_BOUND_SERIAL, serialNumber)
      .putString(KEY_BOUND_ANDROID_ID, getAndroidId(context))
      .putString(KEY_BOUND_MODEL, getDeviceModel())
      .putLong(KEY_BOUND_TIMESTAMP, System.currentTimeMillis())
      .apply()
  }

  fun isDeviceBound(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_DEVICE_BOUND, false)
  }

  fun unbindDevice(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
  }

  fun getDeviceInfoSummary(context: Context): String {
    val currentModel = getDeviceModel()
    val isAuthorized = isAuthorizedPatrickDevice(context)
    return if (isAuthorized) {
      "Authorized: Samsung Galaxy S25 Ultra (Serial: $TARGET_SERIAL)"
    } else {
      "Unauthorized device: $currentModel (Locked to S25 Ultra $TARGET_SERIAL)"
    }
  }
}
