package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.PhotoReceipt
import com.example.data.model.Sha2aItem
import com.example.data.repository.BudgetRepository
import com.example.util.ExportConfig
import com.example.util.ImageExporter
import com.example.util.ScheduleEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

enum class AppTab(val title: String) {
  HOME("Home"),
  MONTH("Month"),
  PROJECTS("Projects"),
  LOANS("Loans")
}

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: BudgetRepository

  val budgetRows: StateFlow<List<BudgetRow>>
  val projects: StateFlow<List<com.example.data.model.ProjectEntity>>
  val sha2aItems: StateFlow<List<Sha2aItem>>
  val photoReceipts: StateFlow<List<PhotoReceipt>>
  val settings: StateFlow<AppSettings>

  private val _currentTab = MutableStateFlow(AppTab.HOME)
  val currentTab = _currentTab.asStateFlow()

  private val _selectedProjectId = MutableStateFlow<String?>(null)
  val selectedProjectId = _selectedProjectId.asStateFlow()

  private val _selectedMonth = MutableStateFlow(ScheduleEngine.currentMonth())
  val selectedMonth = _selectedMonth.asStateFlow()

  private val _openLoanIds = MutableStateFlow<Set<String>>(emptySet())
  val openLoanIds = _openLoanIds.asStateFlow()

  // Sheet / Dialog states
  private val _editingRow = MutableStateFlow<BudgetRow?>(null)
  val editingRow = _editingRow.asStateFlow()

  private val _isNewRow = MutableStateFlow(false)
  val isNewRow = _isNewRow.asStateFlow()

  private val _editingCell = MutableStateFlow<Pair<String, String>?>(null) // rowId, month
  val editingCell = _editingCell.asStateFlow()

  private val _editingSha2a = MutableStateFlow<Pair<String?, String>?>(null) // id?, side
  val editingSha2a = _editingSha2a.asStateFlow()

  private val _viewingPhoto = MutableStateFlow<PhotoReceipt?>(null)
  val viewingPhoto = _viewingPhoto.asStateFlow()

  private val _showExportMariam = MutableStateFlow(false)
  val showExportMariam = _showExportMariam.asStateFlow()

  private val _showSettings = MutableStateFlow(false)
  val showSettings = _showSettings.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage = _toastMessage.asStateFlow()

  // App Lock state
  private val _isAppUnlocked = MutableStateFlow(
    !com.example.security.BiometricAuthManager.isFingerprintAssigned(application) &&
      !com.example.security.BiometricAuthManager.isFingerprintEnabled(application)
  )
  val isAppUnlocked = _isAppUnlocked.asStateFlow()

  fun unlockApp() {
    _isAppUnlocked.value = true
  }

  fun lockApp() {
    _isAppUnlocked.value = false
  }

  init {
    val db = AppDatabase.getDatabase(application)
    repository = BudgetRepository(db.budgetDao(), application)

    budgetRows = repository.budgetRows.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )
    projects = repository.projects.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )
    sha2aItems = repository.sha2aItems.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )
    photoReceipts = repository.photoReceipts.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList()
    )
    settings = repository.settings.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      AppSettings()
    )

    viewModelScope.launch {
      repository.checkAndSeedIfEmpty()
    }
  }

  fun setTab(tab: AppTab) {
    _currentTab.value = tab
    if (tab != AppTab.PROJECTS) {
      _selectedProjectId.value = null
    }
  }

  fun selectProject(id: String?) {
    _selectedProjectId.value = id
  }

  fun addProject(name: String, description: String = "") {
    viewModelScope.launch {
      val id = "proj-${System.currentTimeMillis()}"
      val p = com.example.data.model.ProjectEntity(
        id = id,
        name = name.trim(),
        description = description.trim(),
        sortOrder = (projects.value.size + 1) * 10
      )
      repository.saveProject(p)
      showToast("Project added.")
    }
  }

  fun deleteProject(id: String) {
    viewModelScope.launch {
      repository.deleteProject(id)
      if (_selectedProjectId.value == id) {
        _selectedProjectId.value = null
      }
      showToast("Project deleted.")
    }
  }

  fun setSelectedMonth(m: String) {
    _selectedMonth.value = m
  }

  fun toggleLoanExpanded(id: String) {
    val current = _openLoanIds.value
    if (current.contains(id)) {
      _openLoanIds.value = current - id
    } else {
      _openLoanIds.value = current + id
    }
  }

  fun showToast(msg: String) {
    _toastMessage.value = msg
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  // Row operations
  fun openAddRow(kind: String, isLoan: Boolean = false) {
    val rows = budgetRows.value.filter { it.kind == kind }
    val maxOrder = rows.maxOfOrNull { it.sortOrder } ?: 0
    val currentM = ScheduleEngine.currentMonth()
    val st = settings.value.startMonth
    val startMonth = if (ScheduleEngine.mIdx(currentM) >= ScheduleEngine.mIdx(st)) currentM else st

    _editingRow.value = BudgetRow(
      id = "row-${UUID.randomUUID().toString().substring(0, 8)}",
      name = "",
      kind = kind,
      amount = 0.0,
      day = null,
      start = startMonth,
      every = 1,
      months = null,
      total = null,
      stepsJson = "[]",
      overridesJson = "{}",
      isLoan = isLoan,
      paidJson = "{}",
      sortOrder = maxOrder + 10
    )
    _isNewRow.value = true
  }

  fun openEditRow(id: String) {
    val row = budgetRows.value.find { it.id == id } ?: return
    _editingRow.value = row
    _isNewRow.value = false
  }

  fun closeRowEditor() {
    _editingRow.value = null
  }

  fun saveRow(row: BudgetRow) {
    viewModelScope.launch {
      repository.saveRow(row)
      closeRowEditor()
    }
  }

  fun deleteRow(id: String) {
    viewModelScope.launch {
      repository.deleteRow(id)
      closeRowEditor()
      showToast("Row deleted.")
    }
  }

  fun moveRow(id: String, direction: Int) {
    viewModelScope.launch {
      val moved = repository.moveRow(id, direction)
      if (moved) {
        showToast(if (direction < 0) "Moved up." else "Moved down.")
      } else {
        showToast(if (direction < 0) "Already at the top." else "Already at the bottom.")
      }
    }
  }

  // Cell operations
  fun openCell(rowId: String, month: String) {
    _editingCell.value = Pair(rowId, month)
  }

  fun closeCell() {
    _editingCell.value = null
  }

  fun saveCellOverride(rowId: String, month: String, amount: Double, forThisMonthOnly: Boolean) {
    viewModelScope.launch {
      val st = settings.value
      repository.updateSingleMonthOverride(
        id = rowId,
        month = month,
        amount = amount,
        forThisMonthOnly = forThisMonthOnly,
        rangeStart = st.startMonth,
        rangeEnd = st.endMonth
      )
      closeCell()
    }
  }

  fun resetCellOverride(rowId: String, month: String) {
    viewModelScope.launch {
      repository.resetMonthOverride(rowId, month)
      closeCell()
    }
  }

  fun togglePaid(rowId: String, month: String, isPaid: Boolean) {
    viewModelScope.launch {
      repository.setRowPaidMonth(rowId, month, isPaid)
    }
  }

  // Sha2a operations
  fun openAddSha2a(side: String) {
    _editingSha2a.value = Pair(null, side)
  }

  fun openEditSha2a(id: String) {
    val item = sha2aItems.value.find { it.id == id } ?: return
    _editingSha2a.value = Pair(item.id, item.side)
  }

  fun closeSha2aEditor() {
    _editingSha2a.value = null
  }

  fun saveSha2a(item: Sha2aItem) {
    viewModelScope.launch {
      repository.saveSha2aItem(item)
      closeSha2aEditor()
    }
  }

  fun deleteSha2a(id: String) {
    viewModelScope.launch {
      repository.deleteSha2aItem(id)
      closeSha2aEditor()
      showToast("Item deleted.")
    }
  }

  // Photo receipts operations
  fun openPhoto(photo: PhotoReceipt) {
    _viewingPhoto.value = photo
  }

  fun closePhoto() {
    _viewingPhoto.value = null
  }

  fun updatePhotoCaption(id: String, caption: String) {
    viewModelScope.launch {
      repository.updatePhotoReceiptCaption(id, caption)
      closePhoto()
    }
  }

  fun deletePhoto(id: String) {
    viewModelScope.launch {
      repository.deletePhotoReceipt(id)
      closePhoto()
      showToast("Photo deleted.")
    }
  }

  fun addPhotoFromUri(uri: Uri, caption: String = "", projectId: String = "sha2a") {
    viewModelScope.launch {
      try {
        val context = getApplication<Application>()
        val receiptsDir = File(context.filesDir, "receipts")
        if (!receiptsDir.exists()) receiptsDir.mkdirs()

        val fileName = "rcpt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 6)}.jpg"
        val destFile = File(receiptsDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
          val bitmap = BitmapFactory.decodeStream(input)
          FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
          }
        }
        repository.savePhotoReceipt(caption, fileName, projectId)
        showToast("Receipt photo added.")
      } catch (e: Exception) {
        showToast("Couldn't add photo. Try again.")
      }
    }
  }

  // Settings operations
  fun openSettings() {
    _showSettings.value = true
  }

  fun closeSettings() {
    _showSettings.value = false
  }

  fun updateRange(start: String, end: String) {
    viewModelScope.launch {
      repository.updateSettings(start, end)
      showToast("Range updated.")
    }
  }

  fun updateTheme(themeMode: String) {
    viewModelScope.launch {
      repository.updateThemeMode(themeMode)
    }
  }

  fun exportJson(): String {
    var result = ""
    viewModelScope.launch {
      result = repository.exportToJson()
    }
    return result
  }

  suspend fun exportJsonSuspend(): String {
    return repository.exportToJson()
  }

  fun recordDriveBackupDone() {
    viewModelScope.launch {
      repository.recordDriveBackup()
      showToast("Backed up to Google Drive!")
    }
  }

  fun importJson(content: String, onComplete: (Boolean) -> Unit) {
    viewModelScope.launch {
      val res = repository.importFromJson(content)
      if (res.isSuccess) {
        showToast("Backup imported successfully.")
        onComplete(true)
      } else {
        showToast("Invalid backup format.")
        onComplete(false)
      }
    }
  }

  fun resetToDefault() {
    viewModelScope.launch {
      repository.resetToDefault()
      showToast("Reset to initial budget.")
      closeSettings()
    }
  }

  // Export to Mariam
  fun openExportMariam() {
    _showExportMariam.value = true
  }

  fun closeExportMariam() {
    _showExportMariam.value = false
  }

  fun exportToMariam(config: ExportConfig, onImageReady: (Bitmap) -> Unit) {
    viewModelScope.launch {
      val rows = budgetRows.value
      val st = settings.value
      val bitmap = ImageExporter.generateBitmap(
        config = config,
        allRows = rows,
        rangeStart = st.startMonth,
        rangeEnd = st.endMonth
      )
      onImageReady(bitmap)
    }
  }
}
