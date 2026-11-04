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
import com.example.data.user.UserManager
import com.example.data.user.UserProfile
import com.example.util.ExportConfig
import com.example.util.ImageExporter
import com.example.util.ScheduleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

enum class AppTab(val title: String) {
  HOME("Home"),
  ALL("All"),
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

  private val _showVoiceDialog = MutableStateFlow(false)
  val showVoiceDialog = _showVoiceDialog.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage = _toastMessage.asStateFlow()

  // User Profile state
  private val _currentProfile = MutableStateFlow<com.example.data.user.UserProfile?>(null)
  val currentProfile = _currentProfile.asStateFlow()

  private val _showUserSelection = MutableStateFlow(true)
  val showUserSelection = _showUserSelection.asStateFlow()

  // App Lock state (Fingerprint removed, profile selection is the gateway)
  private val _isAppUnlocked = MutableStateFlow(true)
  val isAppUnlocked = _isAppUnlocked.asStateFlow()

  fun unlockApp() {
    _isAppUnlocked.value = true
  }

  fun lockApp() {
    _showUserSelection.value = true
  }

  fun openUserSelection() {
    _showUserSelection.value = true
  }

  fun closeUserSelection() {
    if (_currentProfile.value != null) {
      _showUserSelection.value = false
    }
  }

  fun selectUserProfile(profile: com.example.data.user.UserProfile) {
    viewModelScope.launch {
      val prev = _currentProfile.value
      val app = getApplication<Application>()

      // 1. If selecting the profile that is ALREADY the active user:
      // The Room database ALREADY contains this user's live, up-to-date data!
      // Do NOT reset or overwrite Room DB.
      if (prev != null && prev.id == profile.id) {
        UserManager.setSelectedUserId(app, profile.id)
        _currentProfile.value = profile
        _showUserSelection.value = false
        showToast("Profile: ${profile.name}")
        // Ensure snapshot file is synced with live DB without wiping anything
        try {
          val currentJson = repository.exportToJson()
          UserManager.saveUserSnapshot(app, profile.id, currentJson)
        } catch (_: Exception) {}
        return@launch
      }

      // 2. Switching away from another profile: save previous user's live data first
      if (prev != null && prev.id != profile.id) {
        try {
          val prevJson = repository.exportToJson()
          UserManager.saveUserSnapshot(app, prev.id, prevJson)
        } catch (_: Exception) {}
      }

      // 3. Load target profile's data into Room DB
      val existingSnapshot = UserManager.loadUserSnapshot(app, profile.id)
      if (existingSnapshot != null) {
        repository.importFromJson(existingSnapshot)
      } else if (profile.isPatrick) {
        // If Patrick has no snapshot saved yet:
        // Check if Room DB already has rows (from initial seed or previous work)
        val existingRows = repository.budgetRows.first()
        if (existingRows.isEmpty()) {
          repository.resetToDefault()
        }
        val currentJson = repository.exportToJson()
        UserManager.saveUserSnapshot(app, profile.id, currentJson)
      } else {
        val dummyJson = UserManager.getDummyUserDataJson()
        repository.importFromJson(dummyJson)
        UserManager.saveUserSnapshot(app, profile.id, dummyJson)
      }

      UserManager.setSelectedUserId(app, profile.id)
      _currentProfile.value = profile
      _showUserSelection.value = false
      showToast("Profile: ${profile.name}")
    }
  }

  val syncState = com.example.data.drive.GoogleDriveSyncManager.syncState
  val realtimeSyncStatus = com.example.data.sync.FirestoreRealtimeSyncManager.syncStatus

  fun openVoiceDialog() {
    _showVoiceDialog.value = true
  }

  fun closeVoiceDialog() {
    _showVoiceDialog.value = false
  }

  fun handleVoiceTask(task: com.example.util.VoiceParsedTask, setReminder: Boolean) {
    viewModelScope.launch {
      val isIncome = task.kind == "in"
      val isLoan = task.kind == "loan"
      val kind = if (isIncome) "in" else "out"
      val currentM = ScheduleEngine.currentMonth()
      val st = settings.value.startMonth
      val startMonth = if (ScheduleEngine.mIdx(currentM) >= ScheduleEngine.mIdx(st)) currentM else st

      val newRow = BudgetRow(
        id = "row-${UUID.randomUUID().toString().substring(0, 8)}",
        name = task.title,
        kind = kind,
        amount = task.amount,
        day = task.day,
        start = startMonth,
        every = 1,
        months = task.months,
        total = if (task.months != null && task.amount > 0) task.amount * task.months else null,
        stepsJson = "[]",
        overridesJson = "{}",
        isLoan = isLoan,
        paidJson = "{}",
        sortOrder = (budgetRows.value.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: 0) + 10
      )

      repository.saveRow(newRow)
      persistCurrentProfileSnapshot()

      if (setReminder) {
        val app = getApplication<Application>()
        val dayStr = if (task.day != null) "due on day ${task.day}" else "scheduled"
        com.example.reminder.ReminderManager.showNotification(
          context = app,
          notificationId = (100..9999).random(),
          title = "New ${if (isIncome) "Income" else if (isLoan) "Loan" else "Expense"} Added: ${task.title}",
          message = "${task.title} for ${task.amount} EGP is $dayStr. Cloud-synced across devices.",
          isTask = true
        )
      }

      showToast("Voice entry added: ${task.title} (${task.amount} EGP)")
    }
  }

  private fun persistCurrentProfileSnapshot() {
    viewModelScope.launch {
      try {
        val user = _currentProfile.value ?: return@launch
        val json = repository.exportToJson()
        UserManager.saveUserSnapshot(getApplication(), user.id, json)
        // 1. Google Drive auto-sync
        com.example.data.drive.GoogleDriveSyncManager.scheduleContinuousAutoSync(
          context = getApplication(),
          profile = user,
          jsonSupplier = { json }
        )
        // 2. Firestore real-time cloud sync across devices
        com.example.data.sync.FirestoreRealtimeSyncManager.pushLocalChangesRealtime(
          context = getApplication(),
          profile = user,
          jsonSupplier = { json }
        )
      } catch (_: Exception) {}
    }
  }

  fun syncNow() {
    val user = _currentProfile.value ?: return
    viewModelScope.launch {
      val res = com.example.data.drive.GoogleDriveSyncManager.manualSyncNow(
        context = getApplication(),
        profile = user,
        jsonSupplier = { repository.exportToJson() }
      )
      com.example.data.sync.FirestoreRealtimeSyncManager.pushLocalChangesRealtime(
        context = getApplication(),
        profile = user,
        jsonSupplier = { repository.exportToJson() }
      )
      if (res.isSuccess) {
        showToast("Auto-synced to Google Drive & Firestore (${user.email.ifBlank { "Google Account" }})")
      } else {
        showToast("Sync finished: ${res.exceptionOrNull()?.message ?: "OK"}")
      }
    }
  }

  fun toggleAutoSync(enabled: Boolean) {
    val user = _currentProfile.value ?: return
    val app = getApplication<Application>()
    UserManager.editUser(app, user.id, user.name, user.avatarKey, user.email, enabled)
    val updated = user.copy(autoSyncEnabled = enabled)
    _currentProfile.value = updated
    if (enabled) {
      persistCurrentProfileSnapshot()
      showToast("Cloud continuous auto-sync turned ON")
    } else {
      showToast("Cloud continuous auto-sync turned OFF")
    }
  }

  fun updateUserEmail(email: String) {
    val user = _currentProfile.value ?: return
    val app = getApplication<Application>()
    UserManager.editUser(app, user.id, user.name, user.avatarKey, email, user.autoSyncEnabled)
    _currentProfile.value = user.copy(email = email)
    persistCurrentProfileSnapshot()
  }

  init {
    val db = AppDatabase.getDatabase(application)
    repository = BudgetRepository(db.budgetDao(), application)

    val app = application
    val selectedId = UserManager.getSelectedUserId(app)
    val allUsers = UserManager.getUsers(app)
    val chosen = allUsers.find { it.id == selectedId }
    if (chosen != null) {
      _currentProfile.value = chosen
    }
    _showUserSelection.value = true

    budgetRows = repository.budgetRows.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      emptyList()
    )
    projects = repository.projects.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      emptyList()
    )
    sha2aItems = repository.sha2aItems.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      emptyList()
    )
    photoReceipts = repository.photoReceipts.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      emptyList()
    )
    settings = repository.settings.stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
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
      persistCurrentProfileSnapshot()
      showToast("Project added.")
    }
  }

  fun deleteProject(id: String) {
    viewModelScope.launch {
      repository.deleteProject(id)
      if (_selectedProjectId.value == id) {
        _selectedProjectId.value = null
      }
      persistCurrentProfileSnapshot()
      showToast("Project deleted.")
    }
  }

  fun updateProject(id: String, name: String, description: String = "") {
    viewModelScope.launch {
      val existing = projects.value.find { it.id == id } ?: return@launch
      val updated = existing.copy(name = name.trim(), description = description.trim())
      repository.updateProject(updated)
      persistCurrentProfileSnapshot()
      showToast("Project updated.")
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
      persistCurrentProfileSnapshot()
    }
  }

  fun insertRow(row: BudgetRow) {
    viewModelScope.launch {
      repository.insertRow(row)
      closeRowEditor()
      persistCurrentProfileSnapshot()
    }
  }

  fun updateRow(row: BudgetRow) {
    viewModelScope.launch {
      repository.updateRow(row)
      closeRowEditor()
      persistCurrentProfileSnapshot()
    }
  }

  fun deleteRow(id: String) {
    viewModelScope.launch {
      repository.deleteRow(id)
      closeRowEditor()
      persistCurrentProfileSnapshot()
      showToast("Row deleted.")
    }
  }

  fun moveRow(id: String, direction: Int) {
    viewModelScope.launch {
      val moved = repository.moveRow(id, direction)
      if (moved) {
        persistCurrentProfileSnapshot()
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
      persistCurrentProfileSnapshot()
    }
  }

  fun resetCellOverride(rowId: String, month: String) {
    viewModelScope.launch {
      repository.resetMonthOverride(rowId, month)
      closeCell()
      persistCurrentProfileSnapshot()
    }
  }

  fun togglePaid(rowId: String, month: String, isPaid: Boolean) {
    viewModelScope.launch {
      repository.setRowPaidMonth(rowId, month, isPaid)
      persistCurrentProfileSnapshot()
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
      persistCurrentProfileSnapshot()
    }
  }

  fun insertSha2aItem(item: Sha2aItem) {
    viewModelScope.launch {
      repository.insertSha2aItem(item)
      closeSha2aEditor()
      persistCurrentProfileSnapshot()
    }
  }

  fun updateSha2aItem(item: Sha2aItem) {
    viewModelScope.launch {
      repository.updateSha2aItem(item)
      closeSha2aEditor()
      persistCurrentProfileSnapshot()
    }
  }

  fun deleteSha2a(id: String) {
    viewModelScope.launch {
      repository.deleteSha2aItem(id)
      closeSha2aEditor()
      persistCurrentProfileSnapshot()
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
      persistCurrentProfileSnapshot()
    }
  }

  fun deletePhoto(id: String) {
    viewModelScope.launch {
      repository.deletePhotoReceipt(id)
      closePhoto()
      persistCurrentProfileSnapshot()
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
        persistCurrentProfileSnapshot()
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
      persistCurrentProfileSnapshot()
      showToast("Range updated.")
    }
  }

  fun updateTheme(themeMode: String) {
    viewModelScope.launch {
      repository.updateThemeMode(themeMode)
      persistCurrentProfileSnapshot()
    }
  }

  fun exportJson(): String {
    return runBlocking(Dispatchers.IO) {
      repository.exportToJson()
    }
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
        persistCurrentProfileSnapshot()
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
      persistCurrentProfileSnapshot()
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
