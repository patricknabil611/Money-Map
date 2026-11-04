package com.example.data.user

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UserProfile(
  val id: String,
  val name: String,
  val isPatrick: Boolean = false,
  val avatarKey: String = if (isPatrick) "patrick" else "classic_smile",
  val email: String = if (isPatrick) "patricknabil611@gmail.com" else "",
  val autoSyncEnabled: Boolean = true,
  val lastSyncTime: Long = 0L
)

object UserManager {
  private const val PREFS_NAME = "money_map_user_prefs"
  private const val KEY_SELECTED_USER = "selected_user_id"
  private const val KEY_USERS_JSON = "users_list_json"
  const val PATRICK_ID = "patrick"
  const val PATRICK_PASSWORD = "K@Pbros38"
  const val PATRICK_EMAIL = "patricknabil611@gmail.com"

  fun getSelectedUserId(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_SELECTED_USER, null)
  }

  fun setSelectedUserId(context: Context, userId: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_SELECTED_USER, userId).apply()
  }

  fun clearSelectedUser(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_SELECTED_USER).apply()
  }

  fun getUsers(context: Context): List<UserProfile> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(KEY_USERS_JSON, null)
    if (jsonStr.isNullOrBlank()) {
      // Initialize with default users (Patrick has patrick photo avatar and patricknabil611@gmail.com)
      val defaults = listOf(
        UserProfile(
          id = PATRICK_ID,
          name = "Patrick",
          isPatrick = true,
          avatarKey = "patrick",
          email = PATRICK_EMAIL,
          autoSyncEnabled = true
        ),
        UserProfile(
          id = "new_user",
          name = "New User",
          isPatrick = false,
          avatarKey = "classic_smile",
          email = "",
          autoSyncEnabled = true
        )
      )
      saveUsers(context, defaults)
      return defaults
    }
    return try {
      val arr = JSONArray(jsonStr)
      val list = mutableListOf<UserProfile>()
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val isPat = obj.optBoolean("isPatrick", obj.getString("id") == PATRICK_ID)
        val rawAvatar = obj.optString("avatarKey", if (isPat) "patrick" else "classic_smile")
        val effectiveAvatar = if (isPat && (rawAvatar == "red_dragon" || rawAvatar.isBlank())) "patrick" else rawAvatar
        val email = obj.optString("email", if (isPat) PATRICK_EMAIL else "")
        val autoSync = obj.optBoolean("autoSyncEnabled", true)
        val lastSync = obj.optLong("lastSyncTime", 0L)
        list.add(
          UserProfile(
            id = obj.getString("id"),
            name = obj.getString("name"),
            isPatrick = isPat,
            avatarKey = effectiveAvatar,
            email = email,
            autoSyncEnabled = autoSync,
            lastSyncTime = lastSync
          )
        )
      }
      if (list.none { it.id == PATRICK_ID }) {
        list.add(0, UserProfile(id = PATRICK_ID, name = "Patrick", isPatrick = true, avatarKey = "patrick", email = PATRICK_EMAIL))
        saveUsers(context, list)
      }
      list
    } catch (_: Exception) {
      listOf(
        UserProfile(id = PATRICK_ID, name = "Patrick", isPatrick = true, avatarKey = "patrick", email = PATRICK_EMAIL),
        UserProfile(id = "new_user", name = "New User", isPatrick = false, avatarKey = "classic_smile")
      )
    }
  }

  fun saveUsers(context: Context, users: List<UserProfile>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val arr = JSONArray()
    for (u in users) {
      val obj = JSONObject()
      obj.put("id", u.id)
      obj.put("name", u.name)
      obj.put("isPatrick", u.isPatrick)
      obj.put("avatarKey", u.avatarKey)
      obj.put("email", u.email)
      obj.put("autoSyncEnabled", u.autoSyncEnabled)
      obj.put("lastSyncTime", u.lastSyncTime)
      arr.put(obj)
    }
    prefs.edit().putString(KEY_USERS_JSON, arr.toString()).apply()
  }

  fun addUser(context: Context, name: String, avatarKey: String = "classic_smile", email: String = ""): UserProfile {
    val users = getUsers(context).toMutableList()
    val id = "user_${System.currentTimeMillis()}"
    val newUser = UserProfile(
      id = id,
      name = name.trim().ifBlank { "User ${users.size}" },
      isPatrick = false,
      avatarKey = avatarKey,
      email = email.trim(),
      autoSyncEnabled = true
    )
    users.add(newUser)
    saveUsers(context, users)
    return newUser
  }

  fun editUser(
    context: Context,
    id: String,
    newName: String,
    newAvatarKey: String? = null,
    newEmail: String? = null,
    autoSync: Boolean? = null
  ): Boolean {
    val users = getUsers(context).toMutableList()
    val index = users.indexOfFirst { it.id == id }
    if (index >= 0) {
      users[index] = users[index].copy(
        name = newName.trim().ifBlank { users[index].name },
        avatarKey = newAvatarKey ?: users[index].avatarKey,
        email = newEmail?.trim() ?: users[index].email,
        autoSyncEnabled = autoSync ?: users[index].autoSyncEnabled
      )
      saveUsers(context, users)
      return true
    }
    return false
  }

  fun updateLastSyncTime(context: Context, id: String, timestamp: Long) {
    val users = getUsers(context).toMutableList()
    val index = users.indexOfFirst { it.id == id }
    if (index >= 0) {
      users[index] = users[index].copy(lastSyncTime = timestamp)
      saveUsers(context, users)
    }
  }

  fun deleteUser(context: Context, id: String): Boolean {
    if (id == PATRICK_ID) return false // Cannot delete Patrick
    val users = getUsers(context).toMutableList()
    val removed = users.removeAll { it.id == id }
    if (removed) {
      saveUsers(context, users)
      // Delete snapshot file if exists
      val file = File(context.filesDir, "user_data_$id.json")
      if (file.exists()) file.delete()
      if (getSelectedUserId(context) == id) {
        clearSelectedUser(context)
      }
    }
    return removed
  }

  fun verifyPatrickPassword(password: String): Boolean {
    return password.trim() == PATRICK_PASSWORD
  }

  fun sendResetEmailIntent(context: Context): Intent {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
      data = Uri.parse("mailto:$PATRICK_EMAIL")
      putExtra(Intent.EXTRA_EMAIL, arrayOf(PATRICK_EMAIL))
      putExtra(Intent.EXTRA_SUBJECT, "Password Reset Request for Patrick Profile")
      putExtra(
        Intent.EXTRA_TEXT,
        "Hello Patrick,\n\nHere is your password recovery for the P Money app.\nPassword: $PATRICK_PASSWORD\n\nSent from P Money for Samsung Galaxy S25 Ultra."
      )
    }
    return intent
  }

  fun saveUserSnapshot(context: Context, userId: String, jsonString: String) {
    try {
      val file = File(context.filesDir, "user_data_$userId.json")
      file.writeText(jsonString)
    } catch (_: Exception) {}
  }

  fun loadUserSnapshot(context: Context, userId: String): String? {
    return try {
      val file = File(context.filesDir, "user_data_$userId.json")
      if (file.exists()) file.readText() else null
    } catch (_: Exception) {
      null
    }
  }

  fun getDummyUserDataJson(): String {
    return """
    {
      "app": "P Money",
      "format_version": 1,
      "settings": {
        "start": "2026-01",
        "end": "2026-12"
      },
      "rows": [
        {
          "id": "dummy_sal",
          "name": "Monthly Salary",
          "kind": "in",
          "amount": 4500.0,
          "day": 1,
          "start": "2026-01",
          "every": 1,
          "months": null,
          "total": null,
          "isLoan": false,
          "order": 10,
          "steps": [],
          "overrides": {},
          "paid": {}
        },
        {
          "id": "dummy_side",
          "name": "Side Income",
          "kind": "in",
          "amount": 800.0,
          "day": 15,
          "start": "2026-01",
          "every": 1,
          "months": null,
          "total": null,
          "isLoan": false,
          "order": 20,
          "steps": [],
          "overrides": {},
          "paid": {}
        },
        {
          "id": "dummy_rent",
          "name": "House Rent",
          "kind": "out",
          "amount": 1600.0,
          "day": 1,
          "start": "2026-01",
          "every": 1,
          "months": null,
          "total": null,
          "isLoan": false,
          "order": 30,
          "steps": [],
          "overrides": {},
          "paid": {}
        },
        {
          "id": "dummy_groc",
          "name": "Groceries & Food",
          "kind": "out",
          "amount": 900.0,
          "day": null,
          "start": "2026-01",
          "every": 1,
          "months": null,
          "total": null,
          "isLoan": false,
          "order": 40,
          "steps": [],
          "overrides": {},
          "paid": {}
        },
        {
          "id": "dummy_util",
          "name": "Utilities & Bills",
          "kind": "out",
          "amount": 350.0,
          "day": 10,
          "start": "2026-01",
          "every": 1,
          "months": null,
          "total": null,
          "isLoan": false,
          "order": 50,
          "steps": [],
          "overrides": {},
          "paid": {}
        },
        {
          "id": "dummy_loan",
          "name": "Personal Loan",
          "kind": "out",
          "amount": 400.0,
          "day": 20,
          "start": "2026-01",
          "every": 1,
          "months": 12,
          "total": 4800.0,
          "isLoan": true,
          "order": 60,
          "steps": [],
          "overrides": {},
          "paid": {"2026-01": true}
        }
      ],
      "sha2a": [],
      "photos": []
    }
    """.trimIndent()
  }
}
