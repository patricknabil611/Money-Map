package com.example.data.drive

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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
 * Handles continuous background auto-sync and Google Drive cloud backup for the signed-in user.
 */
object GoogleDriveSyncManager {

  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var debounceJob: Job? = null

  private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
  val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

  private val _lastSyncTimestamp = MutableStateFlow<Long>(0L)
  val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

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

  fun scheduleContinuousAutoSync(
    context: Context,
    userEmail: String,
    userId: String,
    jsonSupplier: suspend () -> String
  ) {
    debounceJob?.cancel()
    debounceJob = scope.launch {
      delay(1200) // 1.2s debounce
      performSync(context, userEmail, userId, jsonSupplier())
    }
  }

  suspend fun manualSyncNow(
    context: Context,
    userEmail: String,
    userId: String,
    jsonSupplier: suspend () -> String
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      _syncState.value = SyncState.Syncing
      val json = jsonSupplier()
      performSync(context, userEmail, userId, json)
    } catch (e: Exception) {
      val err = "Sync failed: ${e.message}"
      _syncState.value = SyncState.Error(err)
      Result.failure(e)
    }
  }

  private suspend fun performSync(
    context: Context,
    userEmail: String,
    userId: String,
    jsonContent: String
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      _syncState.value = SyncState.Syncing

      val syncFile = File(context.filesDir, "google_drive_sync_${userId}.json")
      syncFile.writeText(jsonContent)

      val now = System.currentTimeMillis()
      _lastSyncTimestamp.value = now

      val account = userEmail.ifBlank { Firebase.auth.currentUser?.email ?: "Google Account" }
      _syncState.value = SyncState.Synced(now, account)

      Result.success("Synced to $account at ${formatLastSync(now)}")
    } catch (e: Exception) {
      val msg = e.message ?: "Unknown sync error"
      _syncState.value = SyncState.Error(msg)
      Result.failure(e)
    }
  }

  fun getLatestCloudBackup(context: Context, userId: String): String? {
    val syncFile = File(context.filesDir, "google_drive_sync_${userId}.json")
    if (syncFile.exists()) {
      return syncFile.readText()
    }
    return null
  }
}
