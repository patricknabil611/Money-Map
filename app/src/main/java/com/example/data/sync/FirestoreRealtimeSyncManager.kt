package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.user.UserManager
import com.example.data.user.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RealtimeSyncStatus {
  object Disconnected : RealtimeSyncStatus()
  object Connecting : RealtimeSyncStatus()
  data class LiveConnected(val activeDevicesCount: Int, val lastSyncMs: Long) : RealtimeSyncStatus()
  data class Error(val message: String) : RealtimeSyncStatus()
}

object FirestoreRealtimeSyncManager {

  private const val TAG = "RealtimeSync"
  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private var liveListener: ListenerRegistration? = null
  private var pushDebounceJob: Job? = null

  private val _syncStatus = MutableStateFlow<RealtimeSyncStatus>(RealtimeSyncStatus.Disconnected)
  val syncStatus: StateFlow<RealtimeSyncStatus> = _syncStatus.asStateFlow()

  private fun getFirestore(context: Context): FirebaseFirestore {
    val dbId = context.getString(R.string.firestore_database_id)
    return FirebaseFirestore.getInstance(dbId)
  }

  fun startObservingRemoteChanges(
    context: Context,
    profile: UserProfile,
    onRemoteDataReceived: (jsonPayload: String) -> Unit
  ) {
    val authUser = Firebase.auth.currentUser
    if (authUser == null) {
      _syncStatus.value = RealtimeSyncStatus.Disconnected
      return
    }

    liveListener?.remove()
    _syncStatus.value = RealtimeSyncStatus.Connecting

    try {
      val db = getFirestore(context)
      val docRef = db.collection("users")
        .document(authUser.uid)
        .collection("budgets")
        .document("live_snapshot")

      liveListener = docRef.addSnapshotListener { snapshot, error ->
        if (error != null) {
          Log.e(TAG, "Real-time snapshot error: ${error.message}", error)
          _syncStatus.value = RealtimeSyncStatus.Error(error.localizedMessage ?: "Sync error")
          return@addSnapshotListener
        }

        if (snapshot != null && snapshot.exists()) {
          val payload = snapshot.getString("budgetPayload")
          val remoteTimestamp = snapshot.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
          val localLastSync = profile.lastSyncTime

          _syncStatus.value = RealtimeSyncStatus.LiveConnected(
            activeDevicesCount = 1,
            lastSyncMs = remoteTimestamp
          )

          if (!payload.isNullOrBlank() && remoteTimestamp > localLastSync) {
            Log.d(TAG, "Received newer live snapshot from another device")
            onRemoteDataReceived(payload)
          }
        } else {
          _syncStatus.value = RealtimeSyncStatus.LiveConnected(1, System.currentTimeMillis())
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start observing remote changes", e)
      _syncStatus.value = RealtimeSyncStatus.Error(e.message ?: "Failed to connect")
    }
  }

  fun stopObserving() {
    liveListener?.remove()
    liveListener = null
    _syncStatus.value = RealtimeSyncStatus.Disconnected
  }

  fun pushLocalChangesRealtime(
    context: Context,
    profile: UserProfile,
    jsonSupplier: suspend () -> String
  ) {
    val authUser = Firebase.auth.currentUser ?: return
    pushDebounceJob?.cancel()

    pushDebounceJob = scope.launch {
      delay(1000) // 1s debounce to avoid flooding
      try {
        val json = jsonSupplier()
        val db = getFirestore(context)
        val docRef = db.collection("users")
          .document(authUser.uid)
          .collection("budgets")
          .document("live_snapshot")

        val data = mapOf(
          "userId" to authUser.uid,
          "profileId" to profile.id,
          "profileName" to profile.name,
          "budgetPayload" to json,
          "updatedAt" to FieldValue.serverTimestamp()
        )

        docRef.set(data)
        val now = System.currentTimeMillis()
        UserManager.updateLastSyncTime(context, profile.id, now)
        _syncStatus.value = RealtimeSyncStatus.LiveConnected(1, now)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to push real-time updates: ${e.message}", e)
      }
    }
  }
}
