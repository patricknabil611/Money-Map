package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
  private var lastLocalPushMs = 0L

  private val _syncStatus = MutableStateFlow<RealtimeSyncStatus>(RealtimeSyncStatus.Disconnected)
  val syncStatus: StateFlow<RealtimeSyncStatus> = _syncStatus.asStateFlow()

  fun getFirestore(context: Context): FirebaseFirestore {
    val dbId = context.getString(R.string.firestore_database_id)
    return FirebaseFirestore.getInstance(dbId)
  }

  suspend fun isFirestoreBudgetEmpty(context: Context, uid: String): Boolean {
    return try {
      val db = getFirestore(context)
      val snapshot = db.collection("users")
        .document(uid)
        .collection("budgets")
        .get()
        .await()
      snapshot.isEmpty
    } catch (e: Exception) {
      Log.e(TAG, "Error checking if budget is empty: ${e.message}", e)
      false
    }
  }

  suspend fun isLegacyMigratedInFirestore(context: Context, uid: String): Boolean {
    return try {
      val db = getFirestore(context)
      val userDoc = db.collection("users").document(uid).get().await()
      userDoc.getBoolean("legacyMigrated") == true
    } catch (e: Exception) {
      Log.e(TAG, "Error checking legacyMigrated: ${e.message}", e)
      false
    }
  }

  suspend fun markLegacyMigratedInFirestore(context: Context, uid: String) {
    try {
      val db = getFirestore(context)
      val data = mapOf(
        "legacyMigrated" to true,
        "migratedAt" to FieldValue.serverTimestamp()
      )
      db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    } catch (e: Exception) {
      Log.e(TAG, "Error marking legacyMigrated: ${e.message}", e)
    }
  }

  suspend fun fetchRemoteBudget(context: Context, uid: String): String? {
    return try {
      val db = getFirestore(context)
      val doc = db.collection("users")
        .document(uid)
        .collection("budgets")
        .document("live_snapshot")
        .get()
        .await()
      if (doc.exists()) {
        doc.getString("budgetPayload")
      } else {
        null
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error fetching remote budget: ${e.message}", e)
      null
    }
  }

  fun startObservingRemoteChanges(
    context: Context,
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

          _syncStatus.value = RealtimeSyncStatus.LiveConnected(
            activeDevicesCount = 1,
            lastSyncMs = remoteTimestamp
          )

          if (!payload.isNullOrBlank() && remoteTimestamp > lastLocalPushMs) {
            Log.d(TAG, "Received newer live snapshot from cloud")
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

  suspend fun uploadBudgetDirect(
    context: Context,
    uid: String,
    json: String
  ) {
    try {
      val db = getFirestore(context)
      val docRef = db.collection("users")
        .document(uid)
        .collection("budgets")
        .document("live_snapshot")

      val data = mapOf(
        "userId" to uid,
        "budgetPayload" to json,
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(data).await()
      lastLocalPushMs = System.currentTimeMillis()
      _syncStatus.value = RealtimeSyncStatus.LiveConnected(1, lastLocalPushMs)
    } catch (e: Exception) {
      Log.e(TAG, "Direct upload failed: ${e.message}", e)
    }
  }

  fun pushLocalChangesRealtime(
    context: Context,
    jsonSupplier: suspend () -> String
  ) {
    val authUser = Firebase.auth.currentUser ?: return
    pushDebounceJob?.cancel()

    pushDebounceJob = scope.launch {
      delay(1000) // 1s debounce
      try {
        val json = jsonSupplier()
        val db = getFirestore(context)
        val docRef = db.collection("users")
          .document(authUser.uid)
          .collection("budgets")
          .document("live_snapshot")

        val data = mapOf(
          "userId" to authUser.uid,
          "budgetPayload" to json,
          "updatedAt" to FieldValue.serverTimestamp()
        )

        docRef.set(data).await()
        lastLocalPushMs = System.currentTimeMillis()
        _syncStatus.value = RealtimeSyncStatus.LiveConnected(1, lastLocalPushMs)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to push real-time updates: ${e.message}", e)
      }
    }
  }
}
