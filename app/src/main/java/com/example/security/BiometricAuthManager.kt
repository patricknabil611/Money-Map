package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class BiometricAvailability {
  READY,
  NOT_ENROLLED,
  NO_HARDWARE,
  UNAVAILABLE
}

object BiometricAuthManager {

  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "money_map_single_fingerprint_key"
  private const val PREFS_NAME = "money_map_security_prefs"

  private const val PREF_FINGERPRINT_ENABLED = "fingerprint_enabled"
  private const val PREF_FINGERPRINT_ASSIGNED = "fingerprint_assigned"
  private const val PREF_ASSIGNED_TIMESTAMP = "assigned_timestamp"
  private const val PREF_ENCRYPTED_TOKEN = "encrypted_token"
  private const val PREF_IV = "encryption_iv"
  private const val PREF_BACKUP_PIN_HASH = "backup_pin_hash"
  private const val PREF_BACKUP_PIN_SALT = "backup_pin_salt"

  private const val VERIFICATION_PAYLOAD = "MONEY_MAP_SINGLE_FINGERPRINT_VERIFIED_TOKEN"

  fun checkBiometricAvailability(context: Context): BiometricAvailability {
    val biometricManager = BiometricManager.from(context)
    return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
      BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.READY
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
      else -> BiometricAvailability.UNAVAILABLE
    }
  }

  fun isFingerprintAssigned(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_FINGERPRINT_ASSIGNED, false)
  }

  fun isFingerprintEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // If assigned, default to enabled
    val assigned = prefs.getBoolean(PREF_FINGERPRINT_ASSIGNED, false)
    return prefs.getBoolean(PREF_FINGERPRINT_ENABLED, assigned)
  }

  fun setFingerprintEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(PREF_FINGERPRINT_ENABLED, enabled).apply()
  }

  fun getAssignedTimestamp(context: Context): Long {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getLong(PREF_ASSIGNED_TIMESTAMP, 0L)
  }

  fun hasBackupPin(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return !prefs.getString(PREF_BACKUP_PIN_HASH, null).isNullOrBlank()
  }

  fun setBackupPin(context: Context, pin: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val salt = System.currentTimeMillis().toString()
    val hash = hashPin(pin, salt)
    prefs.edit()
      .putString(PREF_BACKUP_PIN_HASH, hash)
      .putString(PREF_BACKUP_PIN_SALT, salt)
      .apply()
  }

  fun verifyBackupPin(context: Context, pin: String): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val storedHash = prefs.getString(PREF_BACKUP_PIN_HASH, null) ?: return false
    val salt = prefs.getString(PREF_BACKUP_PIN_SALT, "") ?: ""
    return hashPin(pin, salt) == storedHash
  }

  private fun hashPin(pin: String, salt: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest("$pin:$salt:money_map".toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }

  fun unassignFingerprint(context: Context) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
      keyStore.load(null)
      if (keyStore.containsAlias(KEY_ALIAS)) {
        keyStore.deleteEntry(KEY_ALIAS)
      }
    } catch (_: Exception) {}

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
      .putBoolean(PREF_FINGERPRINT_ASSIGNED, false)
      .putBoolean(PREF_FINGERPRINT_ENABLED, false)
      .remove(PREF_ASSIGNED_TIMESTAMP)
      .remove(PREF_ENCRYPTED_TOKEN)
      .remove(PREF_IV)
      .apply()
  }

  /**
   * Generates a single-use bound hardware key that is invalidated
   * if ANY new biometric is added to the device (setInvalidatedByBiometricEnrollment = true).
   */
  private fun generateSecretKey(): SecretKey {
    val keyGenerator = KeyGenerator.getInstance(
      KeyProperties.KEY_ALGORITHM_AES,
      ANDROID_KEYSTORE
    )
    val builder = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setUserAuthenticationRequired(true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      // Critical for single fingerprint assignment:
      // If user enrolls another fingerprint at device level, this key is destroyed.
      builder.setInvalidatedByBiometricEnrollment(true)
    }

    keyGenerator.init(builder.build())
    return keyGenerator.generateKey()
  }

  private fun getSecretKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null)
    return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
  }

  /**
   * Enrolls and binds the app to exactly one fingerprint.
   * Prompts the user to scan the fingerprint they want to designate for Money Map.
   */
  fun assignSingleFingerprint(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    try {
      val secretKey = generateSecretKey()
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)

      val executor = ContextCompat.getMainExecutor(activity)
      val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Assign Your Fingerprint")
        .setSubtitle("Touch the fingerprint sensor to bind this specific fingerprint to P Money")
        .setDescription("Only this assigned fingerprint will be authorized to unlock the app.")
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

      val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          try {
            val authenticatedCipher = result.cryptoObject?.cipher ?: cipher
            val iv = authenticatedCipher.iv
            val encryptedBytes = authenticatedCipher.doFinal(VERIFICATION_PAYLOAD.toByteArray(Charsets.UTF_8))

            val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
              .putBoolean(PREF_FINGERPRINT_ASSIGNED, true)
              .putBoolean(PREF_FINGERPRINT_ENABLED, true)
              .putLong(PREF_ASSIGNED_TIMESTAMP, System.currentTimeMillis())
              .putString(PREF_ENCRYPTED_TOKEN, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
              .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
              .apply()

            onSuccess()
          } catch (e: Exception) {
            onError("Failed to secure fingerprint binding: ${e.message}")
          }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          onError(errString.toString())
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          onError("Fingerprint not recognized. Please try again with the finger you wish to assign.")
        }
      })

      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (e: Exception) {
      onError("Biometric setup error: ${e.message}")
    }
  }

  /**
   * Authenticates using the previously assigned fingerprint and the cryptographic key.
   */
  fun authenticateWithFingerprint(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    onUseBackupPin: () -> Unit
  ) {
    val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val encryptedTokenStr = prefs.getString(PREF_ENCRYPTED_TOKEN, null)
    val ivStr = prefs.getString(PREF_IV, null)

    if (encryptedTokenStr.isNullOrBlank() || ivStr.isNullOrBlank()) {
      onError("No fingerprint assigned yet. Please assign your fingerprint first.")
      return
    }

    try {
      val secretKey = getSecretKey()
      if (secretKey == null) {
        unassignFingerprint(activity)
        onError("Cryptographic key not found. Please re-assign your fingerprint.")
        return
      }

      val iv = Base64.decode(ivStr, Base64.NO_WRAP)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      val spec = GCMParameterSpec(128, iv)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

      val executor = ContextCompat.getMainExecutor(activity)
      val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock P Money")
        .setSubtitle("Scan your assigned fingerprint to proceed")
        .setNegativeButtonText(if (hasBackupPin(activity)) "Use PIN" else "Cancel")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()

      val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          try {
            val authenticatedCipher = result.cryptoObject?.cipher ?: cipher
            val encryptedBytes = Base64.decode(encryptedTokenStr, Base64.NO_WRAP)
            val decryptedBytes = authenticatedCipher.doFinal(encryptedBytes)
            val decrypted = String(decryptedBytes, Charsets.UTF_8)

            if (decrypted == VERIFICATION_PAYLOAD) {
              onSuccess()
            } else {
              onError("Verification mismatch. Access denied.")
            }
          } catch (e: Exception) {
            onError("Decryption failed: ${e.message}")
          }
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
            onUseBackupPin()
          } else {
            onError(errString.toString())
          }
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          onError("Fingerprint does not match the assigned fingerprint.")
        }
      })

      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (e: KeyPermanentlyInvalidatedException) {
      unassignFingerprint(activity)
      onError("Fingerprint key invalidated! A new fingerprint was added to your device. Please re-assign your authorized fingerprint.")
    } catch (e: Exception) {
      onError("Authentication initialization failed: ${e.message}")
    }
  }

  /**
   * Generic biometric fingerprint authentication.
   */
  fun authenticate(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onNegativeButton: () -> Unit,
    onError: (String) -> Unit
  ) {
    val availability = checkBiometricAvailability(activity)
    if (availability != BiometricAvailability.READY) {
      if (availability == BiometricAvailability.NOT_ENROLLED) {
        onError("No biometrics enrolled on this device. Please register a fingerprint in device settings.")
      } else {
        onError("Biometric sensor unavailable on this device.")
      }
      onNegativeButton()
      return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle("Fingerprint Unlock")
      .setSubtitle("P Money biometric security")
      .setDescription("Touch fingerprint sensor to unlock the app")
      .setNegativeButtonText("Cancel")
      .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
      .build()

    val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        onSuccess()
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
          onNegativeButton()
        } else {
          onError(errString.toString())
        }
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        onError("Fingerprint not recognized. Try again.")
      }
    })

    try {
      var secretKey = getSecretKey()
      if (secretKey == null) {
        secretKey = generateSecretKey()
      }
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (_: Exception) {
      biometricPrompt.authenticate(promptInfo)
    }
  }
}

