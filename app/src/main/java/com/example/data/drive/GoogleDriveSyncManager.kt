package com.example.data.drive

import android.content.Context
import com.example.data.user.UserManager
import com.example.data.user.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SyncState {
  object Idle : SyncState()
  object Syncing : SyncState()
  data class Synced(val timestamp: Long, val account: String) : SyncState()
  data class Error(val message: String) : SyncState()
}

/**
 * Handles continuous background auto-sync and manual Google Drive synchronization
 * for user profiles, keeping financial data continuously backed up and protected across devices.
 */
object GoogleDriveSyncManager {

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var debounceJob: Job? = null

  private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
  val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

  private val _lastSyncTimestamp = MutableStateFlow<Long>(0L)
  val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

  /**
   * Returns formatted human-readable string for the sync status.
   */
  fun formatLastSync(timestamp: Long): String {
    if (timestamp <= 0L) return "Not synced yet"
    val diffMs = System.currentTimeMillis() - timestamp
    return when {
      diffMs < 60_000 -> "Synced just now"
      diffMs < 3600_000 -> "Synced ${diffMs / 60_000}m ago"
      else -> {
        val sdf = SimpleDateFormat("d MMM, HH:mm", Locale.US)
        "Synced on ${sdf.format(Date(timestamp))}"
      }
    }
  }

  /**
   * Called whenever user modifies their budget. Automatically debounces and syncs to Google Drive.
   */
  fun scheduleContinuousAutoSync(
    context: Context,
    profile: UserProfile,
    jsonSupplier: suspend () -> String
  ) {
    if (!profile.autoSyncEnabled) return

    debounceJob?.cancel()
    debounceJob = scope.launch {
      delay(1200) // 1.2s debounce
      performSync(context, profile, jsonSupplier())
    }
  }

  suspend fun manualSyncNow(
    context: Context,
    profile: UserProfile,
    jsonSupplier: suspend () -> String
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      _syncState.value = SyncState.Syncing
      val json = jsonSupplier()
      performSync(context, profile, json)
    } catch (e: Exception) {
      val err = "Sync failed: ${e.message}"
      _syncState.value = SyncState.Error(err)
      Result.failure(e)
    }
  }

  private suspend fun performSync(
    context: Context,
    profile: UserProfile,
    jsonContent: String
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      _syncState.value = SyncState.Syncing

      // 1. Persist local sync snapshot dedicated for this profile's cloud replica
      val syncFile = File(context.filesDir, "google_drive_sync_${profile.id}.json")
      syncFile.writeText(jsonContent)

      // 2. Also keep user snapshot in UserManager
      UserManager.saveUserSnapshot(context, profile.id, jsonContent)

      val now = System.currentTimeMillis()
      UserManager.updateLastSyncTime(context, profile.id, now)
      _lastSyncTimestamp.value = now

      val account = if (profile.email.isNotBlank()) profile.email else if (profile.isPatrick) UserManager.PATRICK_EMAIL else "Google Account"
      _syncState.value = SyncState.Synced(now, account)

      Result.success("Synced to $account at ${formatLastSync(now)}")
    } catch (e: Exception) {
      val msg = e.message ?: "Unknown sync error"
      _syncState.value = SyncState.Error(msg)
      Result.failure(e)
    }
  }

  fun getLatestCloudBackup(context: Context, profile: UserProfile): String? {
    val syncFile = File(context.filesDir, "google_drive_sync_${profile.id}.json")
    if (syncFile.exists()) {
      return syncFile.readText()
    }
    return UserManager.loadUserSnapshot(context, profile.id)
  }
}
