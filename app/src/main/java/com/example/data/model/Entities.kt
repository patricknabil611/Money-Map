package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

data class Step(
  val from: String,
  val amount: Double
)

@Entity(tableName = "budget_rows")
data class BudgetRow(
  @PrimaryKey val id: String,
  val name: String,
  val kind: String, // "in" or "out"
  val amount: Double,
  val day: Int?,
  val start: String,
  val every: Int = 1,
  val months: Int? = null,
  val total: Double? = null,
  val stepsJson: String = "[]",
  val overridesJson: String = "{}",
  val isLoan: Boolean = false,
  val paidJson: String = "{}",
  val sortOrder: Int = 0
) {
  fun getSteps(): List<Step> {
    if (stepsJson.isBlank()) return emptyList()
    val list = mutableListOf<Step>()
    try {
      val arr = JSONArray(stepsJson)
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        list.add(
          Step(
            from = obj.optString("from", ""),
            amount = obj.optDouble("amount", 0.0)
          )
        )
      }
    } catch (_: Exception) {}
    return list
  }

  fun getOverrides(): Map<String, Double> {
    if (overridesJson.isBlank()) return emptyMap()
    val map = mutableMapOf<String, Double>()
    try {
      val obj = JSONObject(overridesJson)
      val keys = obj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        map[k] = obj.optDouble(k, 0.0)
      }
    } catch (_: Exception) {}
    return map
  }

  fun getPaid(): Map<String, Boolean> {
    if (paidJson.isBlank()) return emptyMap()
    val map = mutableMapOf<String, Boolean>()
    try {
      val obj = JSONObject(paidJson)
      val keys = obj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        map[k] = obj.optBoolean(k, false)
      }
    } catch (_: Exception) {}
    return map
  }

  companion object {
    fun stepsToJson(steps: List<Step>): String {
      val arr = JSONArray()
      steps.forEach { s ->
        val obj = JSONObject()
        obj.put("from", s.from)
        obj.put("amount", s.amount)
        arr.put(obj)
      }
      return arr.toString()
    }

    fun overridesToJson(overrides: Map<String, Double>): String {
      val obj = JSONObject()
      overrides.forEach { (k, v) -> obj.put(k, v) }
      return obj.toString()
    }

    fun paidToJson(paid: Map<String, Boolean>): String {
      val obj = JSONObject()
      paid.forEach { (k, v) -> obj.put(k, v) }
      return obj.toString()
    }
  }
}

@Entity(tableName = "projects")
data class ProjectEntity(
  @PrimaryKey val id: String,
  val name: String,
  val description: String = "",
  val sortOrder: Int = 0
)

@Entity(tableName = "sha2a_items")
data class Sha2aItem(
  @PrimaryKey val id: String,
  val name: String,
  val amount: Double,
  val side: String, // "in" or "out"
  val sortOrder: Int = 0,
  val projectId: String = "sha2a"
)

@Entity(tableName = "photo_receipts")
data class PhotoReceipt(
  @PrimaryKey val id: String,
  val caption: String = "",
  val localFileName: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val projectId: String = "sha2a"
)

@Entity(tableName = "app_settings")
data class AppSettings(
  @PrimaryKey val id: String = "main",
  val startMonth: String = "2026-11",
  val endMonth: String = "2027-12",
  val themeMode: String = "system", // "system", "light", "dark"
  val driveAccount: String = "",
  val lastDriveBackup: Long = 0L
)
