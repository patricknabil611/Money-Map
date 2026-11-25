package com.example.data.repository

import android.content.Context
import com.example.data.db.BudgetDao
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.MonthEntry
import com.example.data.model.PhotoReceipt
import com.example.data.model.ProjectEntity
import com.example.data.model.Sha2aItem
import com.example.util.RecordOrder
import com.example.util.ScheduleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BudgetRepository(
  private val dao: BudgetDao,
  private val context: Context
) {

  private val materializeMutex = Mutex()

  val budgetRows: Flow<List<BudgetRow>> = dao.getAllBudgetRows()
  val monthEntries: Flow<List<MonthEntry>> = dao.getAllMonthEntries()
  val projects: Flow<List<ProjectEntity>> = dao.getAllProjects()
  val sha2aItems: Flow<List<Sha2aItem>> = dao.getAllSha2aItems()
  val photoReceipts: Flow<List<PhotoReceipt>> = dao.getAllPhotoReceipts()
  val settings: Flow<AppSettings> = dao.getSettings().map { it ?: AppSettings() }

  suspend fun checkAndSeedIfEmpty() = withContext(Dispatchers.IO) {
    val existing = dao.getSettingsSync()
    if (existing == null) {
      dao.insertSettings(AppSettings())
    }
    val existingProjects = dao.getAllProjects().first()
    if (existingProjects.isEmpty()) {
      dao.insertProject(
        ProjectEntity(
          id = "sha2a",
          name = "Sha2a",
          description = "Apartment costs & Andrew’s receipts",
          sortOrder = 1
        )
      )
    }
  }

  /**
   * Evaluates whether the one-time "Arrange old records" screen must be shown.
   * If there are NO legacy records (createdAt == null) anywhere, silently sets
   * legacyArrangeDone = true and returns false.
   */
  suspend fun evaluateLegacyArrangeNeeded(): Boolean = withContext(Dispatchers.IO) {
    val currentSettings = dao.getSettingsSync() ?: AppSettings().also { dao.insertSettings(it) }
    if (currentSettings.legacyArrangeDone) return@withContext false

    val hasLegacyBudgetRows = dao.getAllBudgetRowsSync().any { it.createdAt == null }
    val hasLegacyMonthEntries = dao.getAllMonthEntriesSync().any { it.createdAt == null }
    val hasLegacySha2a = dao.getAllSha2aItemsSync().any { it.createdAt == null }

    if (!hasLegacyBudgetRows && !hasLegacyMonthEntries && !hasLegacySha2a) {
      dao.insertSettings(currentSettings.copy(legacyArrangeDone = true))
      false
    } else {
      true
    }
  }

  /**
   * Persists all rearranged legacyOrder values and sets legacyArrangeDone = true
   * in a single atomic database transaction.
   */
  suspend fun saveLegacyArrangement(
    monthEntryOrders: Map<String, Int>,
    budgetRowOrders: Map<String, Int>,
    sha2aOrders: Map<String, Int>
  ) = withContext(Dispatchers.IO) {
    dao.saveLegacyArrangement(
      monthEntryOrders = monthEntryOrders,
      budgetRowOrders = budgetRowOrders,
      sha2aOrders = sha2aOrders
    )
  }

  /**
   * Ensures independent MonthEntry records exist for [targetMonth].
   * If [targetMonth] has not yet been materialized, creates its copies from the
   * most recent prior materialized month (preserving source order with strictly
   * increasing createdAt timestamps: baseTime + 0ms, +1ms, +2ms...) plus any
   * newly scheduled BudgetRows for [targetMonth].
   * Copies of legacy records created here are NEW records with real createdAt values.
   */
  suspend fun ensureMonthMaterialized(
    targetMonth: String,
    nowProvider: () -> Long = { System.currentTimeMillis() }
  ): List<MonthEntry> = withContext(Dispatchers.IO) {
    materializeMutex.withLock {
      val currentSettings = dao.getSettingsSync() ?: AppSettings().also { dao.insertSettings(it) }
      val materialized = currentSettings.getMaterializedMonths()
      if (materialized.contains(targetMonth)) {
        return@withLock dao.getMonthEntriesForMonthSync(targetMonth)
      }

      val targetIdx = ScheduleEngine.mIdx(targetMonth)
      val sourceMonth = materialized
        .filter { ScheduleEngine.validM(it) && ScheduleEngine.mIdx(it) < targetIdx }
        .maxByOrNull { ScheduleEngine.mIdx(it) }

      val allRows = dao.getAllBudgetRowsSync()
      val rowsById = allRows.associateBy { it.id }
      val sourceEntries = if (sourceMonth != null) {
        dao.getMonthEntriesForMonthSync(sourceMonth)
      } else {
        emptyList()
      }

      val baseTime = nowProvider()
      var offset = 0L
      val newEntries = mutableListOf<MonthEntry>()

      for (kind in listOf("in", "out")) {
        val copiedSourceRowIds = mutableSetOf<String>()

        // 1. Copy from sourceMonth in its EXACT current sorted order
        val orderedSource = RecordOrder.sort(sourceEntries.filter { it.kind == kind })
        for (src in orderedSource) {
          val srcRowId = src.sourceRowId
          if (srcRowId != null) {
            val row = rowsById[srcRowId]
            if (row != null) {
              val amt = ScheduleEngine.amt(row, targetMonth, currentSettings.startMonth, currentSettings.endMonth)
              if (amt > 0.0) {
                copiedSourceRowIds.add(row.id)
                newEntries.add(
                  MonthEntry(
                    id = "me-${UUID.randomUUID().toString().substring(0, 8)}",
                    month = targetMonth,
                    sourceRowId = row.id,
                    name = src.name,
                    kind = kind,
                    amount = amt,
                    day = src.day,
                    isPaid = row.getPaid()[targetMonth] == true,
                    isLoan = src.isLoan,
                    payInfoText = ScheduleEngine.payInfo(row, targetMonth, currentSettings.startMonth, currentSettings.endMonth),
                    createdAt = baseTime + (offset++),
                    legacyOrder = null
                  )
                )
              }
            }
          } else {
            // Standalone repeating entry from sourceMonth
            newEntries.add(
              MonthEntry(
                id = "me-${UUID.randomUUID().toString().substring(0, 8)}",
                month = targetMonth,
                sourceRowId = null,
                name = src.name,
                kind = kind,
                amount = src.amount,
                day = src.day,
                isPaid = false,
                isLoan = src.isLoan,
                payInfoText = src.payInfoText,
                createdAt = baseTime + (offset++),
                legacyOrder = null
              )
            )
          }
        }

        // 2. Append any scheduled BudgetRows active in targetMonth not already copied from sourceMonth
        val additionalRows = RecordOrder.sort(
          allRows.filter {
            ((it.kind == "in") == (kind == "in")) &&
              !copiedSourceRowIds.contains(it.id) &&
              ScheduleEngine.amt(it, targetMonth, currentSettings.startMonth, currentSettings.endMonth) > 0.0
          }
        )
        for (row in additionalRows) {
          val amt = ScheduleEngine.amt(row, targetMonth, currentSettings.startMonth, currentSettings.endMonth)
          newEntries.add(
            MonthEntry(
              id = "me-${UUID.randomUUID().toString().substring(0, 8)}",
              month = targetMonth,
              sourceRowId = row.id,
              name = row.name,
              kind = kind,
              amount = amt,
              day = row.day,
              isPaid = row.getPaid()[targetMonth] == true,
              isLoan = row.isLoan,
              payInfoText = ScheduleEngine.payInfo(row, targetMonth, currentSettings.startMonth, currentSettings.endMonth),
              createdAt = baseTime + (offset++),
              legacyOrder = null
            )
          )
        }
      }

      val updatedSettings = currentSettings.copy(
        materializedMonthsJson = AppSettings.materializedMonthsToJson(materialized + targetMonth)
      )
      dao.insertMonthEntriesAndMarkMaterialized(newEntries, updatedSettings)
      newEntries
    }
  }

  // --- Projects CRUD ---
  suspend fun saveProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
    val existing = dao.getProjectById(project.id)
    if (existing != null) {
      dao.updateProject(project)
    } else {
      dao.insertProject(project)
    }
  }

  suspend fun insertProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
    dao.insertProject(project)
  }

  suspend fun updateProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
    dao.updateProject(project)
  }

  suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
    dao.deleteProject(id)
  }

  // --- Budget Rows CRUD ---
  suspend fun saveRow(
    row: BudgetRow,
    nowProvider: () -> Long = { System.currentTimeMillis() }
  ) = withContext(Dispatchers.IO) {
    val existing = dao.getBudgetRowById(row.id)
    if (existing != null) {
      // Editing MUST NEVER change createdAt or legacyOrder
      dao.updateBudgetRow(
        row.copy(
          createdAt = existing.createdAt,
          legacyOrder = existing.legacyOrder
        )
      )
    } else {
      val now = row.createdAt ?: nowProvider()
      val newRow = row.copy(
        createdAt = now,
        legacyOrder = null
      )
      dao.insertBudgetRow(newRow)

      // Also create independent MonthEntry copies for any already-materialized months where this new row is scheduled
      val currentSettings = dao.getSettingsSync() ?: AppSettings()
      val materialized = currentSettings.getMaterializedMonths().sorted()
      val monthCopies = mutableListOf<MonthEntry>()
      var offset = 0L
      for (m in materialized) {
        val amt = ScheduleEngine.amt(newRow, m, currentSettings.startMonth, currentSettings.endMonth)
        if (amt > 0.0) {
          monthCopies.add(
            MonthEntry(
              id = "me-${UUID.randomUUID().toString().substring(0, 8)}",
              month = m,
              sourceRowId = newRow.id,
              name = newRow.name,
              kind = if (newRow.kind == "in") "in" else "out",
              amount = amt,
              day = newRow.day,
              isPaid = newRow.getPaid()[m] == true,
              isLoan = newRow.isLoan,
              payInfoText = ScheduleEngine.payInfo(newRow, m, currentSettings.startMonth, currentSettings.endMonth),
              createdAt = now + (offset++),
              legacyOrder = null
            )
          )
        }
      }
      if (monthCopies.isNotEmpty()) {
        dao.insertMonthEntries(monthCopies)
      }
    }
  }

  suspend fun insertRow(row: BudgetRow) = withContext(Dispatchers.IO) {
    dao.insertBudgetRow(row)
  }

  suspend fun updateRow(row: BudgetRow) = withContext(Dispatchers.IO) {
    val existing = dao.getBudgetRowById(row.id)
    if (existing != null) {
      dao.updateBudgetRow(row.copy(createdAt = existing.createdAt, legacyOrder = existing.legacyOrder))
    } else {
      dao.updateBudgetRow(row)
    }
  }

  suspend fun deleteRow(id: String) = withContext(Dispatchers.IO) {
    dao.deleteBudgetRow(id)
  }

  // --- Month Entries CRUD (Strictly independent per-month records) ---
  suspend fun saveMonthEntry(
    entry: MonthEntry,
    nowProvider: () -> Long = { System.currentTimeMillis() }
  ) = withContext(Dispatchers.IO) {
    val existing = dao.getMonthEntryById(entry.id)
    if (existing != null) {
      // Editing MUST NEVER change createdAt or legacyOrder, and MUST NOT affect other months
      dao.updateMonthEntry(
        entry.copy(
          month = existing.month,
          createdAt = existing.createdAt,
          legacyOrder = existing.legacyOrder
        )
      )
    } else {
      val now = entry.createdAt ?: nowProvider()
      val newEntry = entry.copy(
        createdAt = now,
        legacyOrder = null
      )
      dao.insertMonthEntry(newEntry)
    }
  }

  suspend fun deleteMonthEntry(id: String) = withContext(Dispatchers.IO) {
    dao.deleteMonthEntry(id)
  }

  suspend fun getMonthEntryById(id: String): MonthEntry? = withContext(Dispatchers.IO) {
    dao.getMonthEntryById(id)
  }

  suspend fun setMonthEntryPaid(entryId: String, isPaid: Boolean) = withContext(Dispatchers.IO) {
    val entry = dao.getMonthEntryById(entryId) ?: return@withContext
    dao.updateMonthEntry(entry.copy(isPaid = isPaid))
    if (entry.isLoan && entry.sourceRowId != null) {
      setRowPaidMonth(entry.sourceRowId, entry.month, isPaid)
    }
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
    // Keep matching month entry's isPaid in sync without touching its createdAt/legacyOrder
    val entriesForMonth = dao.getMonthEntriesForMonthSync(month)
    entriesForMonth.filter { it.sourceRowId == id }.forEach { me ->
      if (me.isPaid != isPaid) {
        dao.updateMonthEntry(me.copy(isPaid = isPaid))
      }
    }
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
      // Update ONLY that single month's entry if materialized (preserving createdAt & legacyOrder)
      val entriesForMonth = dao.getMonthEntriesForMonthSync(month)
      entriesForMonth.filter { it.sourceRowId == id }.forEach { me ->
        dao.updateMonthEntry(me.copy(amount = amount))
      }
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
  suspend fun saveSha2aItem(
    item: Sha2aItem,
    nowProvider: () -> Long = { System.currentTimeMillis() }
  ) = withContext(Dispatchers.IO) {
    val existing = dao.getSha2aItemById(item.id)
    if (existing != null) {
      // Editing MUST NEVER change createdAt or legacyOrder
      dao.updateSha2aItem(
        item.copy(
          createdAt = existing.createdAt,
          legacyOrder = existing.legacyOrder
        )
      )
    } else {
      val now = item.createdAt ?: nowProvider()
      dao.insertSha2aItem(
        item.copy(
          sortOrder = 0,
          createdAt = now,
          legacyOrder = null
        )
      )
    }
  }

  suspend fun insertSha2aItem(item: Sha2aItem) = withContext(Dispatchers.IO) {
    dao.insertSha2aItem(item)
  }

  suspend fun updateSha2aItem(item: Sha2aItem) = withContext(Dispatchers.IO) {
    val existing = dao.getSha2aItemById(item.id)
    if (existing != null) {
      dao.updateSha2aItem(item.copy(createdAt = existing.createdAt, legacyOrder = existing.legacyOrder))
    } else {
      dao.updateSha2aItem(item)
    }
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

  suspend fun getRowById(id: String): BudgetRow? = withContext(Dispatchers.IO) {
    dao.getBudgetRowById(id)
  }

  suspend fun getSha2aById(id: String): Sha2aItem? = withContext(Dispatchers.IO) {
    dao.getSha2aItemById(id)
  }

  suspend fun getPhotoById(id: String): PhotoReceipt? = withContext(Dispatchers.IO) {
    dao.getPhotoReceiptById(id)
  }

  suspend fun updatePhotoCaption(id: String, caption: String) = withContext(Dispatchers.IO) {
    updatePhotoReceiptCaption(id, caption)
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
    val rows = dao.getAllBudgetRowsSync()
    val monthEntriesList = dao.getAllMonthEntriesSync()
    val sha2a = dao.getAllSha2aItemsSync()
    val photos = dao.getAllPhotoReceipts().first()

    val root = JSONObject()
    root.put("app", "P Money")
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    root.put("exported_on", df.format(Date()))
    root.put("format_version", 2)

    val settingsObj = JSONObject()
    settingsObj.put("start", currentSettings.startMonth)
    settingsObj.put("end", currentSettings.endMonth)
    settingsObj.put("legacyArrangeDone", currentSettings.legacyArrangeDone)
    try {
      settingsObj.put("materializedMonths", JSONArray(currentSettings.materializedMonthsJson))
    } catch (_: Exception) {
      settingsObj.put("materializedMonths", JSONArray())
    }
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
      if (r.createdAt != null) ro.put("createdAt", r.createdAt) else ro.put("createdAt", JSONObject.NULL)
      if (r.legacyOrder != null) ro.put("legacyOrder", r.legacyOrder) else ro.put("legacyOrder", JSONObject.NULL)

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

    val monthEntriesArr = JSONArray()
    for (me in monthEntriesList) {
      val mo = JSONObject()
      mo.put("id", me.id)
      mo.put("month", me.month)
      if (me.sourceRowId != null) mo.put("sourceRowId", me.sourceRowId) else mo.put("sourceRowId", JSONObject.NULL)
      mo.put("name", me.name)
      mo.put("kind", me.kind)
      mo.put("amount", me.amount)
      if (me.day != null) mo.put("day", me.day) else mo.put("day", JSONObject.NULL)
      mo.put("isPaid", me.isPaid)
      mo.put("isLoan", me.isLoan)
      mo.put("payInfoText", me.payInfoText)
      if (me.createdAt != null) mo.put("createdAt", me.createdAt) else mo.put("createdAt", JSONObject.NULL)
      if (me.legacyOrder != null) mo.put("legacyOrder", me.legacyOrder) else mo.put("legacyOrder", JSONObject.NULL)
      monthEntriesArr.put(mo)
    }
    root.put("monthEntries", monthEntriesArr)

    val sha2aArr = JSONArray()
    for (s in sha2a) {
      val so = JSONObject()
      so.put("id", s.id)
      so.put("name", s.name)
      so.put("amount", s.amount)
      so.put("side", s.side)
      so.put("order", s.sortOrder)
      so.put("projectId", s.projectId)
      if (s.createdAt != null) so.put("createdAt", s.createdAt) else so.put("createdAt", JSONObject.NULL)
      if (s.legacyOrder != null) so.put("legacyOrder", s.legacyOrder) else so.put("legacyOrder", JSONObject.NULL)
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
      po.put("projectId", p.projectId)
      photosArr.put(po)
    }
    root.put("photos", photosArr)

    root.toString(2)
  }

  suspend fun importFromJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val root = JSONObject(jsonString)
      val formatVersion = root.optInt("format_version", 1)
      val settingsObj = root.optJSONObject("settings")
      val startM = settingsObj?.optString("start", "2026-11") ?: "2026-11"
      val endM = settingsObj?.optString("end", "2027-12") ?: "2027-12"
      val current = dao.getSettingsSync()

      val hasArrangeFlagInJson = settingsObj?.has("legacyArrangeDone") == true
      val importedArrangeDone = if (hasArrangeFlagInJson) {
        settingsObj!!.optBoolean("legacyArrangeDone", false)
      } else {
        false
      }
      val importedMatMonthsJson = settingsObj?.optJSONArray("materializedMonths")?.toString() ?: "[]"

      val rowsList = mutableListOf<BudgetRow>()
      val rowsArr = root.optJSONArray("rows")
      var inLegacyIdx = 0
      var expLegacyIdx = 0
      var loanLegacyIdx = 0
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
          val createdAt = if (!r.has("createdAt") || r.isNull("createdAt")) null else r.optLong("createdAt")
          val legacyOrder = if (r.has("legacyOrder") && !r.isNull("legacyOrder")) {
            r.optInt("legacyOrder")
          } else if (createdAt == null) {
            when {
              kind == "in" -> inLegacyIdx++
              isLoan -> 10000 + (loanLegacyIdx++)
              else -> expLegacyIdx++
            }
          } else {
            null
          }

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
              sortOrder = order,
              createdAt = createdAt,
              legacyOrder = legacyOrder
            )
          )
        }
      }

      val sha2aList = mutableListOf<Sha2aItem>()
      val sha2aArr = root.optJSONArray("sha2a")
      val sha2aCounters = mutableMapOf<Pair<String, String>, Int>()
      if (sha2aArr != null) {
        for (i in 0 until sha2aArr.length()) {
          val item = sha2aArr.getJSONObject(i)
          val projId = item.optString("projectId", "sha2a")
          val side = item.optString("side", "out")
          val createdAt = if (!item.has("createdAt") || item.isNull("createdAt")) null else item.optLong("createdAt")
          val legacyOrder = if (item.has("legacyOrder") && !item.isNull("legacyOrder")) {
            item.optInt("legacyOrder")
          } else if (createdAt == null) {
            val key = projId to side
            val pos = sha2aCounters[key] ?: 0
            sha2aCounters[key] = pos + 1
            pos
          } else {
            null
          }
          sha2aList.add(
            Sha2aItem(
              id = item.optString("id", "sha2a-$i"),
              name = item.optString("name", ""),
              amount = item.optDouble("amount", 0.0),
              side = side,
              sortOrder = item.optInt("order", i + 1),
              projectId = projId,
              createdAt = createdAt,
              legacyOrder = legacyOrder
            )
          )
        }
      }

      val monthEntriesList = mutableListOf<MonthEntry>()
      val materializedMonths = mutableSetOf<String>()
      try {
        val matArr = JSONArray(importedMatMonthsJson)
        for (i in 0 until matArr.length()) {
          val m = matArr.optString(i, "")
          if (m.isNotBlank()) materializedMonths.add(m)
        }
      } catch (_: Exception) {}

      val monthEntriesArr = root.optJSONArray("monthEntries")
      if (monthEntriesArr != null) {
        for (i in 0 until monthEntriesArr.length()) {
          val mo = monthEntriesArr.getJSONObject(i)
          val m = mo.optString("month", startM)
          materializedMonths.add(m)
          monthEntriesList.add(
            MonthEntry(
              id = mo.optString("id", "me-imp-$i"),
              month = m,
              sourceRowId = if (!mo.has("sourceRowId") || mo.isNull("sourceRowId")) null else mo.optString("sourceRowId"),
              name = mo.optString("name", ""),
              kind = mo.optString("kind", "out"),
              amount = mo.optDouble("amount", 0.0),
              day = if (!mo.has("day") || mo.isNull("day")) null else mo.optInt("day"),
              isPaid = mo.optBoolean("isPaid", false),
              isLoan = mo.optBoolean("isLoan", false),
              payInfoText = mo.optString("payInfoText", ""),
              createdAt = if (!mo.has("createdAt") || mo.isNull("createdAt")) null else mo.optLong("createdAt"),
              legacyOrder = if (!mo.has("legacyOrder") || mo.isNull("legacyOrder")) null else mo.optInt("legacyOrder")
            )
          )
        }
      } else if (formatVersion < 2 && rowsList.isNotEmpty()) {
        // Old v1 backup: materialize active legacy months up through currentMonth as legacy MonthEntries
        val curMonth = ScheduleEngine.currentMonth()
        var minM = startM
        rowsList.forEach { r ->
          if (ScheduleEngine.validM(r.start) && ScheduleEngine.mIdx(r.start) < ScheduleEngine.mIdx(minM)) {
            minM = r.start
          }
        }
        val maxLegacyMonth = if (ScheduleEngine.mIdx(curMonth) >= ScheduleEngine.mIdx(minM)) curMonth else minM
        for (m in ScheduleEngine.mRange(minM, maxLegacyMonth)) {
          val inRows = rowsList
            .filter { it.kind == "in" && ScheduleEngine.amt(it, m, startM, endM) > 0 }
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
          val outRows = rowsList
            .filter { it.kind != "in" && ScheduleEngine.amt(it, m, startM, endM) > 0 }
            .sortedWith(compareBy({ it.day ?: 99 }, { it.sortOrder }))

          if (inRows.isNotEmpty() || outRows.isNotEmpty()) {
            materializedMonths.add(m)
            inRows.forEachIndexed { idx, r ->
              monthEntriesList.add(
                MonthEntry(
                  id = "legacy-me-$m-${r.id}",
                  month = m,
                  sourceRowId = r.id,
                  name = r.name,
                  kind = "in",
                  amount = ScheduleEngine.amt(r, m, startM, endM),
                  day = r.day,
                  isPaid = r.getPaid()[m] == true,
                  isLoan = false,
                  payInfoText = ScheduleEngine.payInfo(r, m, startM, endM),
                  createdAt = null,
                  legacyOrder = idx
                )
              )
            }
            outRows.forEachIndexed { idx, r ->
              monthEntriesList.add(
                MonthEntry(
                  id = "legacy-me-$m-${r.id}",
                  month = m,
                  sourceRowId = r.id,
                  name = r.name,
                  kind = "out",
                  amount = ScheduleEngine.amt(r, m, startM, endM),
                  day = r.day,
                  isPaid = r.getPaid()[m] == true,
                  isLoan = r.isLoan,
                  payInfoText = ScheduleEngine.payInfo(r, m, startM, endM),
                  createdAt = null,
                  legacyOrder = idx
                )
              )
            }
          }
        }
      }

      val settings = AppSettings(
        startMonth = startM,
        endMonth = endM,
        themeMode = current?.themeMode ?: "system",
        legacyArrangeDone = importedArrangeDone,
        materializedMonthsJson = AppSettings.materializedMonthsToJson(materializedMonths)
      )

      val existingPhotos = dao.getAllPhotoReceipts().first()
      dao.replaceAllData(
        settings = settings,
        rows = rowsList,
        monthEntries = monthEntriesList,
        sha2a = sha2aList,
        photos = existingPhotos
      )
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun resetToDefault() = withContext(Dispatchers.IO) {
    clearAllBudgetData()
  }

  suspend fun clearAllBudgetData() = withContext(Dispatchers.IO) {
    dao.replaceAllData(
      settings = AppSettings(legacyArrangeDone = true),
      rows = emptyList(),
      monthEntries = emptyList(),
      sha2a = emptyList(),
      photos = emptyList()
    )
  }
}
