package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.drive.GoogleDriveSyncManager
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.PhotoReceipt
import com.example.data.model.Sha2aItem
import com.example.data.repository.BudgetRepository
import com.example.data.sync.FirestoreRealtimeSyncManager
import com.example.data.user.UserManager
import com.example.reminder.ReminderManager
import com.example.util.ExportConfig
import com.example.util.ImageExporter
import com.example.util.ScheduleEngine
import com.example.util.VoiceParsedTask
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
import java.util.Locale
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

  // Authenticated Firebase User state
  private val _currentUser = MutableStateFlow<FirebaseUser?>(FirebaseAuth.getInstance().currentUser)
  val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

  // App Lock state
  private val _isAppUnlocked = MutableStateFlow(true)
  val isAppUnlocked = _isAppUnlocked.asStateFlow()

  val syncState = GoogleDriveSyncManager.syncState
  val realtimeSyncStatus = FirestoreRealtimeSyncManager.syncStatus

  init {
    val db = AppDatabase.getDatabase(application)
    repository = BudgetRepository(db.budgetDao(), application)

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
      val user = _currentUser.value
      if (user != null) {
        onUserSignedIn(user)
      }
    }
  }

  fun onUserSignedIn(user: FirebaseUser) {
    _currentUser.value = user
    val app = getApplication<Application>()

    // Start live Firestore listening
    FirestoreRealtimeSyncManager.startObservingRemoteChanges(app) { payload ->
      viewModelScope.launch {
        repository.importFromJson(payload)
      }
    }

    viewModelScope.launch {
      val email = user.email?.trim()?.lowercase(Locale.ROOT) ?: ""
      val legacyOwner = UserManager.LEGACY_OWNER_EMAIL.lowercase(Locale.ROOT)

      if (email == legacyOwner) {
        // One-time legacy migration for Patrick's existing data
        val alreadyMigratedInFirestore = FirestoreRealtimeSyncManager.isLegacyMigratedInFirestore(app, user.uid)
        if (!alreadyMigratedInFirestore && !UserManager.isLegacyMigratedLocally(app)) {
          val isFirestoreEmpty = FirestoreRealtimeSyncManager.isFirestoreBudgetEmpty(app, user.uid)
          if (isFirestoreEmpty) {
            val legacySnapshot = UserManager.loadLegacyPatrickSnapshot(app)
            val localRows = repository.budgetRows.first()
            val localDataExists = localRows.isNotEmpty() || !legacySnapshot.isNullOrBlank()

            if (localDataExists) {
              val jsonToUpload = if (localRows.isNotEmpty()) repository.exportToJson() else legacySnapshot!!
              if (localRows.isEmpty()) {
                repository.importFromJson(jsonToUpload)
              }
              FirestoreRealtimeSyncManager.uploadBudgetDirect(app, user.uid, jsonToUpload)
              FirestoreRealtimeSyncManager.markLegacyMigratedInFirestore(app, user.uid)
              UserManager.setLegacyMigratedLocally(app, true)
              showToast("Your budget was restored to your Google account.")
            }
          }
        }
      } else {
        // DIFFERENT account signed in:
        // Discard local legacy data and start account empty if no cloud data exists
        UserManager.clearAllLegacyLocalData(app)
        val remoteJson = FirestoreRealtimeSyncManager.fetchRemoteBudget(app, user.uid)
        if (!remoteJson.isNullOrBlank()) {
          repository.importFromJson(remoteJson)
        } else {
          val localRows = repository.budgetRows.first()
          if (localRows.isNotEmpty()) {
            repository.clearAllBudgetData()
          }
        }
      }
    }
  }

  fun signOut(context: Context, onComplete: () -> Unit = {}) {
    viewModelScope.launch {
      try {
        FirestoreRealtimeSyncManager.stopObserving()
        FirebaseAuth.getInstance().signOut()
        val credentialManager = CredentialManager.create(context)
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        repository.clearAllBudgetData()
        _currentUser.value = null
        showToast("Signed out successfully.")
        onComplete()
      } catch (e: Exception) {
        _currentUser.value = null
        onComplete()
      }
    }
  }

  fun unlockApp() {
    _isAppUnlocked.value = true
  }

  fun lockApp() {
    _isAppUnlocked.value = false
  }

  private fun persistCurrentBudget() {
    viewModelScope.launch {
      try {
        val user = _currentUser.value ?: return@launch
        val json = repository.exportToJson()
        // 1. Google Drive auto-sync
        GoogleDriveSyncManager.scheduleContinuousAutoSync(
          context = getApplication(),
          userEmail = user.email ?: "",
          userId = user.uid,
          jsonSupplier = { json }
        )
        // 2. Firestore real-time cloud sync across devices
        FirestoreRealtimeSyncManager.pushLocalChangesRealtime(
          context = getApplication(),
          jsonSupplier = { json }
        )
      } catch (_: Exception) {}
    }
  }

  fun syncNow() {
    val user = _currentUser.value ?: return
    viewModelScope.launch {
      val res = GoogleDriveSyncManager.manualSyncNow(
        context = getApplication(),
        userEmail = user.email ?: "",
        userId = user.uid,
        jsonSupplier = { repository.exportToJson() }
      )
      FirestoreRealtimeSyncManager.pushLocalChangesRealtime(
        context = getApplication(),
        jsonSupplier = { repository.exportToJson() }
      )
      if (res.isSuccess) {
        showToast("Auto-synced to Google Drive & Firestore (${user.email ?: "Google Account"})")
      } else {
        showToast("Sync finished: ${res.exceptionOrNull()?.message ?: "OK"}")
      }
    }
  }

  fun toggleAutoSync(enabled: Boolean) {
    if (enabled) {
      persistCurrentBudget()
      showToast("Cloud continuous auto-sync turned ON")
    } else {
      showToast("Cloud continuous auto-sync turned OFF")
    }
  }

  fun openVoiceDialog() {
    _showVoiceDialog.value = true
  }

  fun closeVoiceDialog() {
    _showVoiceDialog.value = false
  }

  fun handleVoiceTask(task: VoiceParsedTask, setReminder: Boolean) {
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
      persistCurrentBudget()

      if (setReminder) {
        val app = getApplication<Application>()
        val dayStr = if (task.day != null) "due on day ${task.day}" else "scheduled"
        ReminderManager.showNotification(
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
      val maxOrder = projects.value.maxOfOrNull { it.sortOrder } ?: 0
      val newProj = com.example.data.model.ProjectEntity(
        id = "proj_${System.currentTimeMillis()}",
        name = name,
        description = description,
        sortOrder = maxOrder + 1
      )
      repository.saveProject(newProj)
      _selectedProjectId.value = newProj.id
      persistCurrentBudget()
      showToast("Project '$name' created.")
    }
  }

  fun deleteProject(id: String) {
    viewModelScope.launch {
      repository.deleteProject(id)
      if (_selectedProjectId.value == id) {
        _selectedProjectId.value = null
      }
      persistCurrentBudget()
      showToast("Project deleted.")
    }
  }

  fun setSelectedMonth(m: String) {
    _selectedMonth.value = m
  }

  fun toggleLoanExpanded(rowId: String) {
    val current = _openLoanIds.value
    _openLoanIds.value = if (current.contains(rowId)) current - rowId else current + rowId
  }

  fun togglePaid(rowId: String, month: String, isPaid: Boolean) {
    viewModelScope.launch {
      repository.setRowPaidMonth(rowId, month, isPaid)
      persistCurrentBudget()
    }
  }

  fun openAddRow(kind: String, isLoan: Boolean = false) {
    val maxOrder = budgetRows.value.filter { it.kind == kind }.maxOfOrNull { it.sortOrder } ?: 0
    _editingRow.value = BudgetRow(
      id = "",
      name = "",
      kind = kind,
      amount = 0.0,
      day = null,
      start = settings.value.startMonth,
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
    viewModelScope.launch {
      val row = repository.getRowById(id)
      if (row != null) {
        _editingRow.value = row
        _isNewRow.value = false
      }
    }
  }

  fun closeRowEditor() {
    _editingRow.value = null
    _isNewRow.value = false
  }

  fun saveRow(row: BudgetRow) {
    viewModelScope.launch {
      val toSave = if (row.id.isBlank()) {
        row.copy(id = "row-${UUID.randomUUID().toString().substring(0, 8)}")
      } else {
        row
      }
      repository.saveRow(toSave)
      closeRowEditor()
      persistCurrentBudget()
      showToast("Saved ${toSave.name}")
    }
  }

  fun deleteRow(id: String) {
    viewModelScope.launch {
      val row = repository.getRowById(id)
      repository.deleteRow(id)
      closeRowEditor()
      persistCurrentBudget()
      showToast("Deleted ${row?.name ?: "row"}")
    }
  }

  fun moveRow(id: String, direction: Int) {
    viewModelScope.launch {
      val success = repository.moveRow(id, direction)
      if (success) {
        persistCurrentBudget()
      }
    }
  }

  fun openCell(rowId: String, month: String) {
    _editingCell.value = Pair(rowId, month)
  }

  fun closeCell() {
    _editingCell.value = null
  }

  fun saveCellOverride(rowId: String, month: String, amount: Double, forThisMonthOnly: Boolean) {
    viewModelScope.launch {
      repository.updateSingleMonthOverride(
        id = rowId,
        month = month,
        amount = amount,
        forThisMonthOnly = forThisMonthOnly,
        rangeStart = settings.value.startMonth,
        rangeEnd = settings.value.endMonth
      )
      closeCell()
      persistCurrentBudget()
      showToast("Updated cell for $month")
    }
  }

  fun resetCellOverride(rowId: String, month: String) {
    viewModelScope.launch {
      repository.resetMonthOverride(rowId, month)
      closeCell()
      persistCurrentBudget()
      showToast("Reset cell for $month")
    }
  }

  fun openAddSha2a(side: String) {
    _editingSha2a.value = Pair(null, side)
  }

  fun openEditSha2a(id: String) {
    viewModelScope.launch {
      val item = repository.getSha2aById(id)
      if (item != null) {
        _editingSha2a.value = Pair(id, item.side)
      }
    }
  }

  fun closeSha2aEditor() {
    _editingSha2a.value = null
  }

  fun saveSha2a(item: Sha2aItem) {
    viewModelScope.launch {
      val toSave = if (item.id.isBlank()) {
        val maxOrder = sha2aItems.value.filter { it.side == item.side }.maxOfOrNull { it.sortOrder } ?: 0
        item.copy(
          id = "sha2a-${UUID.randomUUID().toString().substring(0, 8)}",
          sortOrder = maxOrder + 1
        )
      } else {
        item
      }
      repository.saveSha2aItem(toSave)
      closeSha2aEditor()
      persistCurrentBudget()
      showToast("Saved ${toSave.name}")
    }
  }

  fun deleteSha2a(id: String) {
    viewModelScope.launch {
      repository.deleteSha2aItem(id)
      closeSha2aEditor()
      persistCurrentBudget()
      showToast("Deleted item.")
    }
  }

  fun openPhoto(photo: PhotoReceipt) {
    _viewingPhoto.value = photo
  }

  fun closePhoto() {
    _viewingPhoto.value = null
  }

  fun updatePhotoCaption(id: String, newCaption: String) {
    viewModelScope.launch {
      repository.updatePhotoCaption(id, newCaption)
      persistCurrentBudget()
      showToast("Caption saved.")
    }
  }

  fun deletePhoto(id: String) {
    viewModelScope.launch {
      repository.deletePhotoReceipt(id)
      closePhoto()
      persistCurrentBudget()
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
        persistCurrentBudget()
        showToast("Receipt photo added.")
      } catch (e: Exception) {
        showToast("Couldn't add photo. Try again.")
      }
    }
  }

  fun openSettings() {
    _showSettings.value = true
  }

  fun closeSettings() {
    _showSettings.value = false
  }

  fun updateRange(start: String, end: String) {
    viewModelScope.launch {
      repository.updateSettings(start, end)
      persistCurrentBudget()
      showToast("Range updated.")
    }
  }

  fun updateTheme(themeMode: String) {
    viewModelScope.launch {
      repository.updateThemeMode(themeMode)
      persistCurrentBudget()
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

  fun importJson(content: String, onComplete: (Boolean) -> Unit = {}) {
    viewModelScope.launch {
      val res = repository.importFromJson(content)
      if (res.isSuccess) {
        persistCurrentBudget()
        showToast("Backup imported successfully.")
        onComplete(true)
      } else {
        showToast("Invalid backup format.")
        onComplete(false)
      }
    }
  }

  fun resetToDefault() {
    clearAllBudgetData()
  }

  fun clearAllBudgetData() {
    viewModelScope.launch {
      repository.clearAllBudgetData()
      persistCurrentBudget()
      showToast("Cleared all budget data.")
      closeSettings()
    }
  }

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

  fun showToast(msg: String) {
    _toastMessage.value = msg
  }

  fun clearToast() {
    _toastMessage.value = null
  }
}
