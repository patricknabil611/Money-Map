package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetDao
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.PhotoReceipt
import com.example.data.model.Sha2aItem
import com.example.util.ScheduleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BudgetRepository(
  private val dao: BudgetDao,
  private val context: Context
) {

  val budgetRows: Flow<List<BudgetRow>> = dao.getAllBudgetRows()
  val projects: Flow<List<com.example.data.model.ProjectEntity>> = dao.getAllProjects()
  val sha2aItems: Flow<List<Sha2aItem>> = dao.getAllSha2aItems()
  val photoReceipts: Flow<List<PhotoReceipt>> = dao.getAllPhotoReceipts()
  val settings: Flow<AppSettings> = dao.getSettings().map { it ?: AppSettings() }

  suspend fun checkAndSeedIfEmpty() = withContext(Dispatchers.IO) {
    val existing = dao.getSettingsSync()
    if (existing == null) {
      AppDatabase.populateInitialData(context, AppDatabase.getDatabase(context))
    }
    val existingProjects = dao.getAllProjects().first()
    if (existingProjects.isEmpty()) {
      dao.insertProject(
        com.example.data.model.ProjectEntity(
          id = "sha2a",
          name = "Sha2a",
          description = "Apartment costs & Andrew’s receipts",
          sortOrder = 1
        )
      )
    }
  }

  // --- Projects CRUD ---
  suspend fun saveProject(project: com.example.data.model.ProjectEntity) = withContext(Dispatchers.IO) {
    val existing = dao.getProjectById(project.id)
    if (existing != null) {
      dao.updateProject(project)
    } else {
      dao.insertProject(project)
    }
  }

  suspend fun insertProject(project: com.example.data.model.ProjectEntity) = withContext(Dispatchers.IO) {
    dao.insertProject(project)
  }

  suspend fun updateProject(project: com.example.data.model.ProjectEntity) = withContext(Dispatchers.IO) {
    dao.updateProject(project)
  }

  suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
    dao.deleteProject(id)
  }

  // --- Budget Rows CRUD ---
  suspend fun saveRow(row: BudgetRow) = withContext(Dispatchers.IO) {
    val existing = dao.getBudgetRowById(row.id)
    if (existing != null) {
      dao.updateBudgetRow(row)
    } else {
      dao.insertBudgetRow(row)
    }
  }

  suspend fun insertRow(row: BudgetRow) = withContext(Dispatchers.IO) {
    dao.insertBudgetRow(row)
  }

  suspend fun updateRow(row: BudgetRow) = withContext(Dispatchers.IO) {
    dao.updateBudgetRow(row)
  }

  suspend fun deleteRow(id: String) = withContext(Dispatchers.IO) {
    dao.deleteBudgetRow(id)
  }

  suspend fun moveRow(id: String, direction: Int): Boolean = withContext(Dispatchers.IO) {
    val row = dao.getBudgetRowById(id) ?: return@withContext false
    val allRows = dao.getAllBudgetRows().first()
    val groupRows = allRows.filter { (it.kind == "in") == (row.kind == "in") }
      .sortedWith(compareBy({ it.sortOrder }, { it.name }))
      .toMutableList()

    val currentIndex = groupRows.indexOfFirst { it.id == id }
    if (currentIndex == -1) return@withContext false
    val targetIndex = currentIndex + direction
    if (targetIndex < 0 || targetIndex >= groupRows.size) return@withContext false

    val temp = groupRows[currentIndex]
    groupRows[currentIndex] = groupRows[targetIndex]
    groupRows[targetIndex] = temp

    groupRows.forEachIndexed { idx, item ->
      val newOrder = (idx + 1) * 10
      if (item.sortOrder != newOrder) {
        dao.updateBudgetRow(item.copy(sortOrder = newOrder))
      }
    }
    true
  }

  suspend fun setRowPaidMonth(id: String, month: String, isPaid: Boolean) = withContext(Dispatchers.IO) {
    val row = dao.getBudgetRowById(id) ?: return@withContext
    val paid = row.getPaid().toMutableMap()
    if (isPaid) {
      paid[month] = true
    } else {
      paid.remove(month)
    }
    dao.updateBudgetRow(row.copy(paidJson = BudgetRow.paidToJson(paid)))
  }

  suspend fun updateSingleMonthOverride(
    id: String,
    month: String,
    amount: Double,
    forThisMonthOnly: Boolean,
    rangeStart: String,
    rangeEnd: String
  ) = withContext(Dispatchers.IO) {
    val row = dao.getBudgetRowById(id) ?: return@withContext
    val ov = row.getOverrides().toMutableMap()
    val steps = row.getSteps().toMutableList()

    if (forThisMonthOnly) {
      val planned = ScheduleEngine.planAmt(row, month, rangeStart, rangeEnd)
      if (amount == planned) {
        ov.remove(month)
      } else {
        ov[month] = amount
      }
      dao.updateBudgetRow(row.copy(overridesJson = BudgetRow.overridesToJson(ov)))
    } else {
      // Save for month and after
      ov.remove(month)
      val monthIdx = ScheduleEngine.mIdx(month)
      val startIdx = if (ScheduleEngine.validM(row.start)) ScheduleEngine.mIdx(row.start) else ScheduleEngine.mIdx(rangeStart)

      if (monthIdx <= startIdx) {
        val remainingSteps = steps.filter { ScheduleEngine.mIdx(it.from) > monthIdx }
        dao.updateBudgetRow(
          row.copy(
            amount = amount,
            stepsJson = BudgetRow.stepsToJson(remainingSteps),
            overridesJson = BudgetRow.overridesToJson(ov)
          )
        )
      } else {
        val remainingSteps = steps.filter { ScheduleEngine.mIdx(it.from) < monthIdx }.toMutableList()
        remainingSteps.add(com.example.data.model.Step(from = month, amount = amount))
        dao.updateBudgetRow(
          row.copy(
            stepsJson = BudgetRow.stepsToJson(remainingSteps),
            overridesJson = BudgetRow.overridesToJson(ov)
          )
        )
      }
    }
  }

  suspend fun resetMonthOverride(id: String, month: String) = withContext(Dispatchers.IO) {
    val row = dao.getBudgetRowById(id) ?: return@withContext
    val ov = row.getOverrides().toMutableMap()
    ov.remove(month)
    dao.updateBudgetRow(row.copy(overridesJson = BudgetRow.overridesToJson(ov)))
  }

  // --- Sha2a Items CRUD ---
  suspend fun saveSha2aItem(item: Sha2aItem) = withContext(Dispatchers.IO) {
    val existing = dao.getSha2aItemById(item.id)
    if (existing != null) {
      dao.updateSha2aItem(item)
    } else {
      dao.insertSha2aItem(item)
    }
  }

  suspend fun insertSha2aItem(item: Sha2aItem) = withContext(Dispatchers.IO) {
    dao.insertSha2aItem(item)
  }

  suspend fun updateSha2aItem(item: Sha2aItem) = withContext(Dispatchers.IO) {
    dao.updateSha2aItem(item)
  }

  suspend fun deleteSha2aItem(id: String) = withContext(Dispatchers.IO) {
    dao.deleteSha2aItem(id)
  }

  // --- Photo Receipts CRUD ---
  suspend fun savePhotoReceipt(caption: String, fileName: String, projectId: String = "sha2a") = withContext(Dispatchers.IO) {
    val photo = PhotoReceipt(
      id = "photo-${System.currentTimeMillis()}-${(1000..9999).random()}",
      caption = caption,
      localFileName = fileName,
      createdAt = System.currentTimeMillis(),
      projectId = projectId
    )
    dao.insertPhotoReceipt(photo)
  }

  suspend fun updatePhotoReceiptCaption(id: String, caption: String) = withContext(Dispatchers.IO) {
    val photo = dao.getPhotoReceiptById(id) ?: dao.getAllPhotoReceipts().first().find { it.id == id } ?: return@withContext
    dao.updatePhotoReceipt(photo.copy(caption = caption))
  }

  suspend fun deletePhotoReceipt(id: String) = withContext(Dispatchers.IO) {
    val photo = dao.getPhotoReceiptById(id) ?: dao.getAllPhotoReceipts().first().find { it.id == id }
    if (photo != null && photo.localFileName.isNotBlank()) {
      try {
        val file = File(context.filesDir, "receipts/${photo.localFileName}")
        if (file.exists()) file.delete()
      } catch (_: Exception) {}
    }
    dao.deletePhotoReceipt(id)
  }

  // --- Settings CRUD ---
  suspend fun updateSettings(startMonth: String, endMonth: String, themeMode: String? = null) = withContext(Dispatchers.IO) {
    val current = dao.getSettingsSync()
    val newSettings = (current ?: AppSettings()).copy(
      startMonth = startMonth,
      endMonth = endMonth,
      themeMode = themeMode ?: (current?.themeMode ?: "system")
    )
    if (current != null) {
      dao.updateSettings(newSettings)
    } else {
      dao.insertSettings(newSettings)
    }
  }

  suspend fun updateThemeMode(themeMode: String) = withContext(Dispatchers.IO) {
    val current = dao.getSettingsSync()
    if (current != null) {
      dao.updateSettings(current.copy(themeMode = themeMode))
    } else {
      dao.insertSettings(AppSettings(themeMode = themeMode))
    }
  }

  suspend fun recordDriveBackup(timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
    val current = dao.getSettingsSync()
    if (current != null) {
      dao.updateSettings(current.copy(lastDriveBackup = timestamp))
    } else {
      dao.insertSettings(AppSettings(lastDriveBackup = timestamp))
    }
  }

  suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
    val currentSettings = dao.getSettingsSync() ?: AppSettings()
    val rows = dao.getAllBudgetRows().first()
    val sha2a = dao.getAllSha2aItems().first()
    val photos = dao.getAllPhotoReceipts().first()

    val root = JSONObject()
    root.put("app", "P Money")
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    root.put("exported_on", df.format(Date()))
    root.put("format_version", 1)

    val settingsObj = JSONObject()
    settingsObj.put("start", currentSettings.startMonth)
    settingsObj.put("end", currentSettings.endMonth)
    root.put("settings", settingsObj)

    val rowsArr = JSONArray()
    for (r in rows) {
      val ro = JSONObject()
      ro.put("id", r.id)
      ro.put("name", r.name)
      ro.put("kind", r.kind)
      ro.put("amount", r.amount)
      if (r.day != null) ro.put("day", r.day) else ro.put("day", JSONObject.NULL)
      ro.put("start", r.start)
      ro.put("every", r.every)
      if (r.months != null) ro.put("months", r.months) else ro.put("months", JSONObject.NULL)
      if (r.total != null) ro.put("total", r.total) else ro.put("total", JSONObject.NULL)
      ro.put("isLoan", r.isLoan)
      ro.put("order", r.sortOrder)

      try {
        ro.put("steps", JSONArray(r.stepsJson))
      } catch (_: Exception) {
        ro.put("steps", JSONArray())
      }
      try {
        ro.put("overrides", JSONObject(r.overridesJson))
      } catch (_: Exception) {
        ro.put("overrides", JSONObject())
      }
      try {
        ro.put("paid", JSONObject(r.paidJson))
      } catch (_: Exception) {
        ro.put("paid", JSONObject())
      }
      rowsArr.put(ro)
    }
    root.put("rows", rowsArr)

    val sha2aArr = JSONArray()
    for (s in sha2a) {
      val so = JSONObject()
      so.put("id", s.id)
      so.put("name", s.name)
      so.put("amount", s.amount)
      so.put("side", s.side)
      so.put("order", s.sortOrder)
      sha2aArr.put(so)
    }
    root.put("sha2a", sha2aArr)

    val photosArr = JSONArray()
    for (p in photos) {
      val po = JSONObject()
      po.put("id", p.id)
      po.put("caption", p.caption)
      po.put("fileName", p.localFileName)
      po.put("createdAt", p.createdAt)
      photosArr.put(po)
    }
    root.put("photos", photosArr)

    root.toString(2)
  }

  suspend fun importFromJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val root = JSONObject(jsonString)
      val settingsObj = root.optJSONObject("settings")
      val startM = settingsObj?.optString("start", "2026-11") ?: "2026-11"
      val endM = settingsObj?.optString("end", "2027-12") ?: "2027-12"
      val current = dao.getSettingsSync()
      val settings = AppSettings(
        startMonth = startM,
        endMonth = endM,
        themeMode = current?.themeMode ?: "system"
      )

      val rowsList = mutableListOf<BudgetRow>()
      val rowsArr = root.optJSONArray("rows")
      if (rowsArr != null) {
        for (i in 0 until rowsArr.length()) {
          val r = rowsArr.getJSONObject(i)
          val id = r.optString("id", "row-$i")
          val name = r.optString("name", "")
          val kind = r.optString("kind", "out")
          val amount = r.optDouble("amount", 0.0)
          val day = if (r.isNull("day")) null else r.optInt("day")
          val start = r.optString("start", "2026-08")
          val every = r.optInt("every", 1)
          val months = if (r.isNull("months")) null else r.optInt("months")
          val total = if (r.isNull("total")) null else r.optDouble("total")
          val isLoan = r.optBoolean("isLoan", false)
          val order = r.optInt("order", (i + 1) * 10)
          val stepsJson = r.optJSONArray("steps")?.toString() ?: "[]"
          val overridesJson = r.optJSONObject("overrides")?.toString() ?: "{}"
          val paidJson = r.optJSONObject("paid")?.toString() ?: "{}"

          rowsList.add(
            BudgetRow(
              id = id,
              name = name,
              kind = kind,
              amount = amount,
              day = day,
              start = start,
              every = every,
              months = months,
              total = total,
              stepsJson = stepsJson,
              overridesJson = overridesJson,
              isLoan = isLoan,
              paidJson = paidJson,
              sortOrder = order
            )
          )
        }
      }

      val sha2aList = mutableListOf<Sha2aItem>()
      val sha2aArr = root.optJSONArray("sha2a")
      if (sha2aArr != null) {
        for (i in 0 until sha2aArr.length()) {
          val item = sha2aArr.getJSONObject(i)
          sha2aList.add(
            Sha2aItem(
              id = item.optString("id", "sha2a-$i"),
              name = item.optString("name", ""),
              amount = item.optDouble("amount", 0.0),
              side = item.optString("side", "out"),
              sortOrder = item.optInt("order", i + 1)
            )
          )
        }
      }

      val existingPhotos = dao.getAllPhotoReceipts().first()
      dao.replaceAllData(
        settings = settings,
        rows = rowsList,
        sha2a = sha2aList,
        photos = existingPhotos
      )
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun resetToDefault() = withContext(Dispatchers.IO) {
    AppDatabase.populateInitialData(context, AppDatabase.getDatabase(context))
  }
}
