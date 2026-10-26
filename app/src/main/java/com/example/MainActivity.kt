package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.BudgetViewModel
import com.example.ui.components.CellEditorSheet
import com.example.ui.components.ExportMariamSheet
import com.example.ui.components.PhotoLightboxSheet
import com.example.ui.components.RowEditorSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.Sha2aEditorSheet
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoansScreen
import com.example.ui.screens.MonthScreen
import com.example.ui.screens.Sha2aScreen
import com.example.ui.theme.LocalMoneyMapColors
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ScheduleEngine
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: BudgetViewModel = viewModel()
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()

      val systemDark = isSystemInDarkTheme()
      val isDark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
      }

      MyApplicationTheme(darkTheme = isDark) {
        if (!isAppUnlocked) {
          BiometricLockScreen(
            onUnlocked = { viewModel.unlockApp() }
          )
        } else {
          MoneyMapApp(viewModel)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyMapApp(viewModel: BudgetViewModel) {
  val colors = LocalMoneyMapColors.current
  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val budgetRows by viewModel.budgetRows.collectAsStateWithLifecycle()
  val projects by viewModel.projects.collectAsStateWithLifecycle()
  val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
  val sha2aItems by viewModel.sha2aItems.collectAsStateWithLifecycle()
  val photoReceipts by viewModel.photoReceipts.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
  val openLoanIds by viewModel.openLoanIds.collectAsStateWithLifecycle()

  val editingRow by viewModel.editingRow.collectAsStateWithLifecycle()
  val isNewRow by viewModel.isNewRow.collectAsStateWithLifecycle()
  val editingCell by viewModel.editingCell.collectAsStateWithLifecycle()
  val editingSha2a by viewModel.editingSha2a.collectAsStateWithLifecycle()
  val viewingPhoto by viewModel.viewingPhoto.collectAsStateWithLifecycle()
  val showExportMariam by viewModel.showExportMariam.collectAsStateWithLifecycle()
  val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

  // Handle Android back button
  BackHandler(enabled = currentTab != AppTab.HOME || (currentTab == AppTab.PROJECTS && selectedProjectId != null)) {
    if (currentTab == AppTab.PROJECTS && selectedProjectId != null) {
      viewModel.selectProject(null)
    } else {
      viewModel.setTab(AppTab.HOME)
    }
  }

  // Auto-dismiss toast
  LaunchedEffect(toastMessage) {
    if (toastMessage != null) {
      delay(3800)
      viewModel.clearToast()
    }
  }

  if (isLandscape) {
    // Landscape Layout: NavigationRail on the left, full content space on the right
    Row(
      modifier = Modifier
        .fillMaxSize()
        .background(colors.bg)
    ) {
      NavigationRail(
        containerColor = colors.surface,
        header = {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
              onClick = { viewModel.lockApp() },
              modifier = Modifier
                .padding(top = 8.dp)
                .testTag("lock_app_nav_rail_btn")
            ) {
              Icon(Icons.Default.Lock, contentDescription = "Lock App", tint = colors.muted)
            }
            IconButton(
              onClick = { viewModel.openSettings() },
              modifier = Modifier
                .padding(top = 4.dp)
                .testTag("settings_top_btn")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.muted)
            }
          }
        },
        modifier = Modifier.fillMaxHeight()
      ) {
        Spacer(modifier = Modifier.height(8.dp))

        NavigationRailItem(
          selected = currentTab == AppTab.HOME,
          onClick = { viewModel.setTab(AppTab.HOME) },
          icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
          label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.headInk,
            selectedTextColor = colors.ink,
            indicatorColor = colors.head,
            unselectedIconColor = colors.muted,
            unselectedTextColor = colors.muted
          ),
          modifier = Modifier.testTag("tab_home")
        )

        NavigationRailItem(
          selected = currentTab == AppTab.MONTH,
          onClick = { viewModel.setTab(AppTab.MONTH) },
          icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Month") },
          label = { Text("Month", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.headInk,
            selectedTextColor = colors.ink,
            indicatorColor = colors.head,
            unselectedIconColor = colors.muted,
            unselectedTextColor = colors.muted
          ),
          modifier = Modifier.testTag("tab_month")
        )

        NavigationRailItem(
          selected = currentTab == AppTab.PROJECTS,
          onClick = { viewModel.setTab(AppTab.PROJECTS) },
          icon = { Icon(Icons.Default.Apartment, contentDescription = "Projects") },
          label = { Text("Projects", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.headInk,
            selectedTextColor = colors.ink,
            indicatorColor = colors.head,
            unselectedIconColor = colors.muted,
            unselectedTextColor = colors.muted
          ),
          modifier = Modifier.testTag("tab_projects")
        )

        NavigationRailItem(
          selected = currentTab == AppTab.LOANS,
          onClick = { viewModel.setTab(AppTab.LOANS) },
          icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Loans") },
          label = { Text("Loans", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.headInk,
            selectedTextColor = colors.ink,
            indicatorColor = colors.head,
            unselectedIconColor = colors.muted,
            unselectedTextColor = colors.muted
          ),
          modifier = Modifier.testTag("tab_loans")
        )
      }

      // Main Landscape Content
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        // Compact Landscape Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Money map",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
          )
          Spacer(modifier = Modifier.size(10.dp))
          Text(
            text = "Patrick’s budget, ${ScheduleEngine.mLabel(settings.startMonth)} to ${ScheduleEngine.mLabel(settings.endMonth)}",
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.weight(1f)
          )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          MainContentTab(currentTab, viewModel, budgetRows, projects, selectedProjectId, sha2aItems, photoReceipts, settings, selectedMonth, openLoanIds)

          // Toast banner
          androidx.compose.animation.AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 12.dp)
          ) {
            toastMessage?.let { msg ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(12.dp))
                  .background(colors.head)
                  .padding(horizontal = 16.dp, vertical = 10.dp)
              ) {
                Text(
                  text = msg,
                  color = colors.headInk,
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }
      }
    }
  } else {
    // Portrait Layout with TopAppBar and Bottom NavigationBar
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text(
                text = "Money map",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
              )
              Text(
                text = "Patrick’s budget, ${ScheduleEngine.mLabel(settings.startMonth)} to ${ScheduleEngine.mLabel(settings.endMonth)}",
                fontSize = 12.5.sp,
                color = colors.muted
              )
            }
          },
          actions = {
            IconButton(
              onClick = { viewModel.lockApp() },
              modifier = Modifier.testTag("lock_app_top_btn")
            ) {
              Icon(Icons.Default.Lock, contentDescription = "Lock App", tint = colors.muted)
            }
            IconButton(
              onClick = { viewModel.openSettings() },
              modifier = Modifier.testTag("settings_top_btn")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.muted)
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.bg
          )
        )
      },
      bottomBar = {
        NavigationBar(
          containerColor = colors.surface,
          windowInsets = WindowInsets.navigationBars,
          modifier = Modifier.testTag("bottom_nav_bar")
        ) {
          NavigationBarItem(
            selected = currentTab == AppTab.HOME,
            onClick = { viewModel.setTab(AppTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = colors.headInk,
              selectedTextColor = colors.ink,
              indicatorColor = colors.head,
              unselectedIconColor = colors.muted,
              unselectedTextColor = colors.muted
            ),
            modifier = Modifier.testTag("tab_home")
          )

          NavigationBarItem(
            selected = currentTab == AppTab.MONTH,
            onClick = { viewModel.setTab(AppTab.MONTH) },
            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Month") },
            label = { Text("Month", fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = colors.headInk,
              selectedTextColor = colors.ink,
              indicatorColor = colors.head,
              unselectedIconColor = colors.muted,
              unselectedTextColor = colors.muted
            ),
            modifier = Modifier.testTag("tab_month")
          )

          NavigationBarItem(
            selected = currentTab == AppTab.PROJECTS,
            onClick = { viewModel.setTab(AppTab.PROJECTS) },
            icon = { Icon(Icons.Default.Apartment, contentDescription = "Projects") },
            label = { Text("Projects", fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = colors.headInk,
              selectedTextColor = colors.ink,
              indicatorColor = colors.head,
              unselectedIconColor = colors.muted,
              unselectedTextColor = colors.muted
            ),
            modifier = Modifier.testTag("tab_projects")
          )

          NavigationBarItem(
            selected = currentTab == AppTab.LOANS,
            onClick = { viewModel.setTab(AppTab.LOANS) },
            icon = { Icon(Icons.Default.AccountBalance, contentDescription = "Loans") },
            label = { Text("Loans", fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = colors.headInk,
              selectedTextColor = colors.ink,
              indicatorColor = colors.head,
              unselectedIconColor = colors.muted,
              unselectedTextColor = colors.muted
            ),
            modifier = Modifier.testTag("tab_loans")
          )
        }
      },
      containerColor = colors.bg
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
      ) {
        MainContentTab(currentTab, viewModel, budgetRows, projects, selectedProjectId, sha2aItems, photoReceipts, settings, selectedMonth, openLoanIds)

        // Toast notification banner
        androidx.compose.animation.AnimatedVisibility(
          visible = toastMessage != null,
          enter = fadeIn(),
          exit = fadeOut(),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
        ) {
          toastMessage?.let { msg ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.head)
                .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
              Text(
                text = msg,
                color = colors.headInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }
    }
  }

  // Modals & BottomSheets
  editingRow?.let { row ->
    RowEditorSheet(
      row = row,
      isNew = isNewRow,
      rangeStart = settings.startMonth,
      rangeEnd = settings.endMonth,
      onDismiss = { viewModel.closeRowEditor() },
      onSave = { viewModel.saveRow(it) },
      onDelete = { viewModel.deleteRow(it) },
      onMoveUp = { viewModel.moveRow(it, -1) },
      onMoveDown = { viewModel.moveRow(it, 1) }
    )
  }

  editingCell?.let { (rowId, month) ->
    val row = budgetRows.find { it.id == rowId }
    if (row != null) {
      CellEditorSheet(
        row = row,
        month = month,
        rangeStart = settings.startMonth,
        rangeEnd = settings.endMonth,
        onDismiss = { viewModel.closeCell() },
        onSaveOnly = { amt -> viewModel.saveCellOverride(rowId, month, amt, forThisMonthOnly = true) },
        onSaveFrom = { amt -> viewModel.saveCellOverride(rowId, month, amt, forThisMonthOnly = false) },
        onReset = { viewModel.resetCellOverride(rowId, month) },
        onEditWholeRow = {
          viewModel.closeCell()
          viewModel.openEditRow(rowId)
        }
      )
    }
  }

  editingSha2a?.let { (id, side) ->
    val item = if (id != null) sha2aItems.find { it.id == id } else null
    Sha2aEditorSheet(
      item = item,
      defaultSide = side,
      projectId = selectedProjectId ?: "sha2a",
      onDismiss = { viewModel.closeSha2aEditor() },
      onSave = { viewModel.saveSha2a(it) },
      onDelete = { viewModel.deleteSha2a(it) }
    )
  }

  viewingPhoto?.let { photo ->
    PhotoLightboxSheet(
      photo = photo,
      onDismiss = { viewModel.closePhoto() },
      onSaveCaption = { caption -> viewModel.updatePhotoCaption(photo.id, caption) },
      onDelete = { viewModel.deletePhoto(photo.id) }
    )
  }

  if (showExportMariam) {
    ExportMariamSheet(
      allRows = budgetRows,
      rangeStart = settings.startMonth,
      rangeEnd = settings.endMonth,
      onDismiss = { viewModel.closeExportMariam() }
    )
  }

  if (showSettings) {
    SettingsSheet(
      settings = settings,
      onDismiss = { viewModel.closeSettings() },
      onUpdateRange = { start, end -> viewModel.updateRange(start, end) },
      onUpdateTheme = { mode -> viewModel.updateTheme(mode) },
      onExportJson = { viewModel.exportJson() },
      onExportJsonSuspend = { viewModel.exportJsonSuspend() },
      onDriveBackupDone = { viewModel.recordDriveBackupDone() },
      onImportJson = { json, cb -> viewModel.importJson(json, cb) },
      onResetDefault = { viewModel.resetToDefault() },
      onLockApp = {
        viewModel.closeSettings()
        viewModel.lockApp()
      }
    )
  }
}

@Composable
fun MainContentTab(
  currentTab: AppTab,
  viewModel: BudgetViewModel,
  budgetRows: List<com.example.data.model.BudgetRow>,
  projects: List<com.example.data.model.ProjectEntity>,
  selectedProjectId: String?,
  sha2aItems: List<com.example.data.model.Sha2aItem>,
  photoReceipts: List<com.example.data.model.PhotoReceipt>,
  settings: com.example.data.model.AppSettings,
  selectedMonth: String,
  openLoanIds: Set<String>
) {
  when (currentTab) {
    AppTab.HOME -> {
      HomeScreen(
        rows = budgetRows,
        settings = settings,
        onAddIncome = { viewModel.openAddRow("in") },
        onAddExpense = { viewModel.openAddRow("out") },
        onEditRow = { viewModel.openEditRow(it) },
        onEditCell = { rowId, month -> viewModel.openCell(rowId, month) },
        onExportMariam = { viewModel.openExportMariam() }
      )
    }
    AppTab.MONTH -> {
      MonthScreen(
        rows = budgetRows,
        settings = settings,
        selectedMonth = selectedMonth,
        onMonthChange = { viewModel.setSelectedMonth(it) },
        onItemClick = { rowId, month -> viewModel.openCell(rowId, month) },
        onTogglePaid = { rowId, month, isPaid -> viewModel.togglePaid(rowId, month, isPaid) }
      )
    }
    AppTab.PROJECTS -> {
      com.example.ui.screens.ProjectsScreen(
        projects = projects,
        allItems = sha2aItems,
        allPhotos = photoReceipts,
        selectedProjectId = selectedProjectId,
        onSelectProject = { viewModel.selectProject(it) },
        onAddProject = { name, desc -> viewModel.addProject(name, desc) },
        onAddItem = { side -> viewModel.openAddSha2a(side) },
        onEditItem = { id -> viewModel.openEditSha2a(id) },
        onOpenPhoto = { photo -> viewModel.openPhoto(photo) },
        onAddPhotos = { uris ->
          val targetProj = selectedProjectId ?: "sha2a"
          uris.forEach { uri -> viewModel.addPhotoFromUri(uri, projectId = targetProj) }
        }
      )
    }
    AppTab.LOANS -> {
      LoansScreen(
        rows = budgetRows,
        settings = settings,
        openLoanIds = openLoanIds,
        onToggleExpanded = { viewModel.toggleLoanExpanded(it) },
        onTogglePaid = { rowId, month, isPaid -> viewModel.togglePaid(rowId, month, isPaid) },
        onAddLoan = { viewModel.openAddRow("out", isLoan = true) },
        onEditLoan = { viewModel.openEditRow(it) }
      )
    }
  }
}
