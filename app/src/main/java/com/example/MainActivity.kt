package com.example

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.AppShapes
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Logout
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseUser
import com.example.ui.AppTab
import com.example.ui.BudgetViewModel
import com.example.ui.components.CellEditorSheet
import com.example.ui.components.ExportMariamSheet
import com.example.ui.components.PhotoLightboxSheet
import com.example.ui.components.RowEditorSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.Sha2aEditorSheet
import com.example.ui.screens.HomeOverviewScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoansScreen
import com.example.ui.screens.MonthScreen
import com.example.ui.screens.Sha2aScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.LocalMoneyMapColors
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ScheduleEngine
import kotlinx.coroutines.delay

import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.ui.graphics.Color
import com.example.reminder.UpdateReminderManager
import com.example.ui.components.AboutDialog
import com.example.ui.components.NotificationSetupSheet
import com.example.ui.components.SamsungEdgeLightingOverlay
import com.example.ui.components.VoiceTaskDialog
import com.example.util.AppCheckHelper

class MainActivity : FragmentActivity() {
  private lateinit var budgetViewModel: BudgetViewModel
  private val openNotificationSetup = androidx.compose.runtime.mutableStateOf(false)
  private val triggerEdgeLighting = androidx.compose.runtime.mutableStateOf(false)

  private val permissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { _ -> }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppCheckHelper.configureAppCheck(this, intent)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissionsLauncher.launch(
        arrayOf(
          Manifest.permission.POST_NOTIFICATIONS,
          Manifest.permission.RECORD_AUDIO
        )
      )
    } else {
      permissionsLauncher.launch(
        arrayOf(
          Manifest.permission.RECORD_AUDIO
        )
      )
    }

    UpdateReminderManager.scheduleWeeklyUpdateReminder(this)
    handleIncomingNotificationIntent(intent)

    budgetViewModel = androidx.lifecycle.ViewModelProvider(this)[BudgetViewModel::class.java]

    setContent {
      val viewModel = budgetViewModel
      val settings by viewModel.settings.collectAsStateWithLifecycle()

      val systemDark = isSystemInDarkTheme()
      val isDark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
      }

      MyApplicationTheme(darkTheme = isDark) {
        val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

        Box(modifier = Modifier.fillMaxSize()) {
          if (currentUser == null) {
            SignInScreen(
              onSignInSuccess = { user -> viewModel.onUserSignedIn(user) }
            )
          } else {
            MoneyMapApp(
              viewModel = viewModel,
              externalOpenNotificationSetup = openNotificationSetup.value,
              externalTriggerEdgeLighting = triggerEdgeLighting.value,
              onConsumeNotificationIntent = {
                openNotificationSetup.value = false
                triggerEdgeLighting.value = false
              }
            )
          }

          // Top-level Notification Setup screen triggered directly from notification intent
          if (openNotificationSetup.value) {
            NotificationSetupSheet(
              onDismiss = { openNotificationSetup.value = false },
              onTriggerEdgeLightingTest = { triggerEdgeLighting.value = true }
            )
          }

          // Top-level Orange Edge Lighting Overlay triggered by notification
          if (triggerEdgeLighting.value) {
            SamsungEdgeLightingOverlay(
              isActive = true,
              title = "P Money Update Reminder",
              message = "Weekly update reminder! Tap to open update setup.",
              onDismiss = { triggerEdgeLighting.value = false },
              onClick = {
                triggerEdgeLighting.value = false
                openNotificationSetup.value = true
              }
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingNotificationIntent(intent)
  }

  private fun handleIncomingNotificationIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action
    val openSection = intent.getStringExtra(UpdateReminderManager.EXTRA_OPEN_SECTION)
    val triggerGlow = intent.getBooleanExtra(UpdateReminderManager.EXTRA_TRIGGER_EDGE_LIGHTING, false)

    if (action == UpdateReminderManager.ACTION_OPEN_UPDATE_CONFIG ||
      openSection == UpdateReminderManager.SECTION_NOTIFICATION_SETUP
    ) {
      openNotificationSetup.value = true
    }
    if (triggerGlow) {
      triggerEdgeLighting.value = true
    }
  }

  override fun onStop() {
    super.onStop()
  }
}

@Composable
fun UserAccountAvatarButton(
  user: FirebaseUser?,
  colors: com.example.ui.theme.MoneyMapColors,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String = "switch_profile_top_btn"
) {
  var showAccountMenu by remember { mutableStateOf(false) }
  var showConfirmSignOut by remember { mutableStateOf(false) }

  Box(modifier = modifier) {
    IconButton(
      onClick = { showAccountMenu = true },
      modifier = Modifier.testTag(testTag)
    ) {
      if (user?.photoUrl != null) {
        AsyncImage(
          model = user.photoUrl,
          contentDescription = "Google Account Menu",
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
        )
      } else {
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(colors.head),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = (user?.displayName?.firstOrNull() ?: user?.email?.firstOrNull() ?: 'U').uppercase(),
            color = colors.headInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    DropdownMenu(
      expanded = showAccountMenu,
      onDismissRequest = { showAccountMenu = false },
      modifier = Modifier.background(colors.surface)
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
          text = user?.displayName ?: "Google User",
          fontWeight = FontWeight.Bold,
          color = colors.ink,
          fontSize = 14.sp
        )
        Text(
          text = user?.email ?: "",
          color = colors.muted,
          fontSize = 12.sp
        )
      }
      androidx.compose.material3.Divider(color = colors.line)
      DropdownMenuItem(
        text = {
          Text(
            text = "Sign out",
            color = colors.expense,
            fontWeight = FontWeight.SemiBold
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = "Sign out",
            tint = colors.expense
          )
        },
        onClick = {
          showAccountMenu = false
          showConfirmSignOut = true
        }
      )
    }
  }

  if (showConfirmSignOut) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showConfirmSignOut = false },
      title = { Text("Sign Out", fontWeight = FontWeight.Bold, color = colors.ink) },
      text = {
        Text(
          "Are you sure you want to sign out? Your budget data remains safely saved in your Google account in the cloud.",
          color = colors.ink
        )
      },
      confirmButton = {
        com.example.ui.components.AppPrimaryButton(
          text = "Sign Out",
          containerColor = colors.expense,
          contentColor = Color.White,
          onClick = {
            showConfirmSignOut = false
            onSignOut()
          }
        )
      },
      dismissButton = {
        com.example.ui.components.AppSecondaryButton(
          text = "Cancel",
          onClick = { showConfirmSignOut = false }
        )
      },
      containerColor = colors.surface
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyMapApp(
  viewModel: BudgetViewModel,
  externalOpenNotificationSetup: Boolean = false,
  externalTriggerEdgeLighting: Boolean = false,
  onConsumeNotificationIntent: () -> Unit = {}
) {
  val colors = LocalMoneyMapColors.current
  val context = androidx.compose.ui.platform.LocalContext.current
  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val isExpandedScreen = configuration.screenWidthDp >= 800 || isLandscape

  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val budgetRows by viewModel.budgetRows.collectAsStateWithLifecycle()
  val projects by viewModel.projects.collectAsStateWithLifecycle()
  val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
  val sha2aItems by viewModel.sha2aItems.collectAsStateWithLifecycle()
  val photoReceipts by viewModel.photoReceipts.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
  val openLoanIds by viewModel.openLoanIds.collectAsStateWithLifecycle()
  val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

  val userFirstName = currentUser?.displayName?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
    ?: currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
    ?: "My"
  val budgetSubtitle = "$userFirstName’s budget, ${ScheduleEngine.mLabel(settings.startMonth)} to ${ScheduleEngine.mLabel(settings.endMonth)}"

  val isSystemDark = isSystemInDarkTheme()
  val isCurrentDark = when (settings.themeMode) {
    "dark" -> true
    "light" -> false
    else -> isSystemDark
  }

  val editingRow by viewModel.editingRow.collectAsStateWithLifecycle()
  val isNewRow by viewModel.isNewRow.collectAsStateWithLifecycle()
  val editingCell by viewModel.editingCell.collectAsStateWithLifecycle()
  val editingSha2a by viewModel.editingSha2a.collectAsStateWithLifecycle()
  val viewingPhoto by viewModel.viewingPhoto.collectAsStateWithLifecycle()
  val showExportMariam by viewModel.showExportMariam.collectAsStateWithLifecycle()
  val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()
  val showVoiceDialog by viewModel.showVoiceDialog.collectAsStateWithLifecycle()
  var showAboutDialog by remember { mutableStateOf(false) }
  var showNotificationSetup by remember { mutableStateOf(false) }
  var showEdgeLighting by remember { mutableStateOf(false) }
  var showTopMenu by remember { mutableStateOf(false) }
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

  LaunchedEffect(externalOpenNotificationSetup, externalTriggerEdgeLighting) {
    if (externalOpenNotificationSetup) {
      showNotificationSetup = true
    }
    if (externalTriggerEdgeLighting) {
      showEdgeLighting = true
    }
    if (externalOpenNotificationSetup || externalTriggerEdgeLighting) {
      onConsumeNotificationIntent()
    }
  }

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

  if (isExpandedScreen) {
    // Desktop / Samsung DeX / Wide Screen Layout: NavigationRail on the left, full content space on the right
    Row(
      modifier = Modifier
        .fillMaxSize()
        .background(colors.bg)
    ) {
      NavigationRail(
        containerColor = colors.surface,
        header = {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            UserAccountAvatarButton(
              user = currentUser,
              colors = colors,
              onSignOut = { viewModel.signOut(context) },
              modifier = Modifier.padding(top = 8.dp),
              testTag = "switch_user_nav_rail_btn"
            )
            IconButton(
              onClick = { viewModel.openSettings() },
              modifier = Modifier
                .padding(top = 4.dp)
                .testTag("settings_top_btn")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Settings", tint = colors.muted)
            }
            IconButton(
              onClick = {
                val nextMode = if (isCurrentDark) "light" else "dark"
                viewModel.updateTheme(nextMode)
              },
              modifier = Modifier
                .padding(top = 4.dp)
                .testTag("theme_toggle_nav_rail_btn")
            ) {
              Icon(
                imageVector = if (isCurrentDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = if (isCurrentDark) "Switch to Light Mode" else "Switch to Dark Mode",
                tint = colors.muted
              )
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
          selected = currentTab == AppTab.ALL,
          onClick = { viewModel.setTab(AppTab.ALL) },
          icon = { Icon(Icons.Default.TableChart, contentDescription = "All") },
          label = { Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
          colors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.headInk,
            selectedTextColor = colors.ink,
            indicatorColor = colors.head,
            unselectedIconColor = colors.muted,
            unselectedTextColor = colors.muted
          ),
          modifier = Modifier.testTag("tab_all")
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
        // Compact Desktop / DeX Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "P Money",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
          )
          Spacer(modifier = Modifier.size(10.dp))
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(colors.head.copy(alpha = 0.15f))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(
              text = "Desktop View",
              color = colors.head,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
          Spacer(modifier = Modifier.size(12.dp))
          Text(
            text = budgetSubtitle,
            fontSize = 12.5.sp,
            color = colors.muted,
            modifier = Modifier.weight(1f)
          )
          IconButton(
            onClick = { viewModel.openVoiceDialog() },
            modifier = Modifier.testTag("voice_entry_dex_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Mic,
              contentDescription = "Voice Task Entry",
              tint = colors.head
            )
          }
          UserAccountAvatarButton(
            user = currentUser,
            colors = colors,
            onSignOut = { viewModel.signOut(context) },
            testTag = "switch_profile_dex_btn"
          )
          IconButton(
            onClick = { showNotificationSetup = true },
            modifier = Modifier.testTag("notification_setup_dex_btn")
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = "Notification & Update Setup",
              tint = Color(0xFFFF6D00)
            )
          }
          IconButton(
            onClick = { showAboutDialog = true },
            modifier = Modifier.testTag("about_app_dex_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = "About P Money",
              tint = colors.muted
            )
          }
          IconButton(
            onClick = {
              val nextMode = if (isCurrentDark) "light" else "dark"
              viewModel.updateTheme(nextMode)
            },
            modifier = Modifier.testTag("theme_toggle_dex_btn")
          ) {
            Icon(
              imageVector = if (isCurrentDark) Icons.Default.LightMode else Icons.Default.DarkMode,
              contentDescription = if (isCurrentDark) "Switch to Light Mode" else "Switch to Dark Mode",
              tint = colors.muted
            )
          }
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
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "P Money",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = budgetSubtitle,
                fontSize = 12.sp,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          },
          actions = {
            IconButton(
              onClick = { viewModel.openVoiceDialog() },
              modifier = Modifier.testTag("voice_entry_top_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Task Entry",
                tint = colors.head
              )
            }
            UserAccountAvatarButton(
              user = currentUser,
              colors = colors,
              onSignOut = { viewModel.signOut(context) },
              testTag = "switch_profile_top_btn"
            )
            Box {
              IconButton(
                onClick = { showTopMenu = true },
                modifier = Modifier.testTag("overflow_menu_top_btn")
              ) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "More options",
                  tint = colors.muted
                )
              }
              DropdownMenu(
                expanded = showTopMenu,
                onDismissRequest = { showTopMenu = false },
                modifier = Modifier.background(colors.surface)
              ) {
                DropdownMenuItem(
                  text = { Text("Notifications & Reminders", color = colors.ink, fontWeight = FontWeight.Medium) },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Default.NotificationsActive,
                      contentDescription = null,
                      tint = Color(0xFFFF6D00)
                    )
                  },
                  onClick = {
                    showTopMenu = false
                    showNotificationSetup = true
                  },
                  modifier = Modifier.testTag("notification_setup_top_btn")
                )
                DropdownMenuItem(
                  text = { Text("About P Money", color = colors.ink, fontWeight = FontWeight.Medium) },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Default.Info,
                      contentDescription = null,
                      tint = colors.muted
                    )
                  },
                  onClick = {
                    showTopMenu = false
                    showAboutDialog = true
                  },
                  modifier = Modifier.testTag("about_app_top_btn")
                )
                DropdownMenuItem(
                  text = { Text("Settings", color = colors.ink, fontWeight = FontWeight.Medium) },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Default.Settings,
                      contentDescription = null,
                      tint = colors.muted
                    )
                  },
                  onClick = {
                    showTopMenu = false
                    viewModel.openSettings()
                  },
                  modifier = Modifier.testTag("settings_top_btn")
                )
                DropdownMenuItem(
                  text = {
                    Text(
                      text = if (isCurrentDark) "Light Mode" else "Dark Mode",
                      color = colors.ink,
                      fontWeight = FontWeight.Medium
                    )
                  },
                  leadingIcon = {
                    Icon(
                      imageVector = if (isCurrentDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                      contentDescription = null,
                      tint = colors.muted
                    )
                  },
                  onClick = {
                    showTopMenu = false
                    val nextMode = if (isCurrentDark) "light" else "dark"
                    viewModel.updateTheme(nextMode)
                  },
                  modifier = Modifier.testTag("theme_toggle_top_btn")
                )
              }
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
            selected = currentTab == AppTab.ALL,
            onClick = { viewModel.setTab(AppTab.ALL) },
            icon = { Icon(Icons.Default.TableChart, contentDescription = "All") },
            label = { Text("All", fontWeight = FontWeight.SemiBold) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = colors.headInk,
              selectedTextColor = colors.ink,
              indicatorColor = colors.head,
              unselectedIconColor = colors.muted,
              unselectedTextColor = colors.muted
            ),
            modifier = Modifier.testTag("tab_all")
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
      currentUser = currentUser,
      onDismiss = { viewModel.closeSettings() },
      onUpdateRange = { start, end -> viewModel.updateRange(start, end) },
      onUpdateTheme = { mode -> viewModel.updateTheme(mode) },
      onExportJson = { viewModel.exportJson() },
      onExportJsonSuspend = { viewModel.exportJsonSuspend() },
      onDriveBackupDone = { viewModel.recordDriveBackupDone() },
      onImportJson = { json, cb -> viewModel.importJson(json, cb) },
      onResetDefault = { viewModel.resetToDefault() },
      onToggleAutoSync = { viewModel.toggleAutoSync(it) },
      onSyncNow = { viewModel.syncNow() },
      onLockApp = {
        viewModel.closeSettings()
        viewModel.lockApp()
      },
      onSignOut = {
        viewModel.closeSettings()
        viewModel.signOut(context)
      },
      onOpenNotificationSetup = {
        viewModel.closeSettings()
        showNotificationSetup = true
      }
    )
  }

  if (showVoiceDialog) {
    VoiceTaskDialog(
      onDismiss = { viewModel.closeVoiceDialog() },
      onConfirmTask = { task, setReminder ->
        viewModel.handleVoiceTask(task, setReminder)
      }
    )
  }

  if (showAboutDialog) {
    AboutDialog(
      onDismiss = { showAboutDialog = false }
    )
  }

  if (showNotificationSetup) {
    NotificationSetupSheet(
      onDismiss = { showNotificationSetup = false },
      onTriggerEdgeLightingTest = { showEdgeLighting = true }
    )
  }

  SamsungEdgeLightingOverlay(
    isActive = showEdgeLighting,
    onDismiss = { showEdgeLighting = false },
    onClick = {
      showEdgeLighting = false
      showNotificationSetup = true
    }
  )
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
      HomeOverviewScreen(
        rows = budgetRows,
        settings = settings,
        selectedMonth = selectedMonth,
        onMonthChange = { viewModel.setSelectedMonth(it) },
        onAddIncome = { viewModel.openAddRow("in") },
        onAddExpense = { viewModel.openAddRow("out") },
        onEditRow = { viewModel.openEditRow(it) },
        onEditCell = { rowId, month -> viewModel.openCell(rowId, month) },
        onNavigateToTab = { viewModel.setTab(it) }
      )
    }
    AppTab.ALL -> {
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
