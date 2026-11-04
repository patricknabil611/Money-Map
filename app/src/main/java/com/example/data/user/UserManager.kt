package com.example.data.user

import android.content.Context
import java.io.File

object UserManager {
  /**
   * Constant used ONLY for one-time legacy budget data migration.
   */
  const val LEGACY_OWNER_EMAIL = "patricknabil611@gmail.com"

  private const val PREFS_MIGRATION = "pmoney_migration_prefs"
  private const val KEY_LEGACY_MIGRATED = "legacy_data_migrated"

  fun isLegacyMigratedLocally(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_MIGRATION, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_LEGACY_MIGRATED, false)
  }

  fun setLegacyMigratedLocally(context: Context, migrated: Boolean = true) {
    val prefs = context.getSharedPreferences(PREFS_MIGRATION, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, migrated).apply()
  }

  fun hasLegacyPatrickSnapshot(context: Context): Boolean {
    val file = File(context.filesDir, "user_data_patrick.json")
    return file.exists() && file.length() > 0
  }

  fun loadLegacyPatrickSnapshot(context: Context): String? {
    return try {
      val file = File(context.filesDir, "user_data_patrick.json")
      if (file.exists()) file.readText() else null
    } catch (_: Exception) {
      null
    }
  }

  fun clearAllLegacyLocalData(context: Context) {
    try {
      val dir = context.filesDir
      dir.listFiles()?.forEach { file ->
        if (file.name.startsWith("user_data_")) {
          file.delete()
        }
      }
      val userPrefs = context.getSharedPreferences("money_map_user_prefs", Context.MODE_PRIVATE)
      userPrefs.edit().clear().apply()
    } catch (_: Exception) {}
  }
}
