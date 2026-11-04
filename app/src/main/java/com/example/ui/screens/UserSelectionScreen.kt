package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSecondaryButton
import com.example.ui.components.AppTextButton
import com.example.ui.theme.AppShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricAuthManager
import com.example.security.DeviceSecurityManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.user.AvatarRegistry
import com.example.data.user.ProfileAvatarView
import com.example.data.user.UserManager
import com.example.data.user.UserProfile

private val NetflixDarkBg = Color(0xFF141414)
private val NetflixCardBg = Color(0xFF1F1F1F)
private val NetflixRed = Color(0xFFE50914)
private val NetflixBorderGray = Color(0xFF808080)
private val NetflixLightGray = Color(0xFFA3A3A3)

@Composable
fun UserSelectionScreen(
  onUserSelected: (UserProfile) -> Unit
) {
  val context = LocalContext.current

  var users by remember { mutableStateOf(UserManager.getUsers(context)) }
  var isManagingProfiles by remember { mutableStateOf(false) }

  // Patrick Password dialog state
  var passwordDialogUser by remember { mutableStateOf<UserProfile?>(null) }
  var enteredPassword by remember { mutableStateOf("") }
  var passwordError by remember { mutableStateOf<String?>(null) }
  var showPasswordResetNotice by remember { mutableStateOf(false) }

  // Profile Editor/Creation state
  var showProfileEditor by remember { mutableStateOf(false) }
  var profileToEdit by remember { mutableStateOf<UserProfile?>(null) } // null = creating new
  var editorName by remember { mutableStateOf("") }
  var editorAvatarKey by remember { mutableStateOf("classic_smile") }
  var editorEmail by remember { mutableStateOf("") }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(NetflixDarkBg)
      .padding(horizontal = 24.dp, vertical = 32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 640.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      // Netflix Brand Accent Bar
      Box(
        modifier = Modifier
          .width(48.dp)
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(NetflixRed)
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Main Netflix Header
      Text(
        text = if (isManagingProfiles) "Manage Profiles" else "Who's Budgeting?",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center
      )

      Text(
        text = if (isManagingProfiles) "Choose a profile to edit avatar or name" else "Select your workspace to continue",
        fontSize = 14.sp,
        color = NetflixLightGray,
        modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
        textAlign = TextAlign.Center
      )

      // Profiles FlowRow (Centered incomplete rows)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 480.dp)
      ) {
        users.forEach { user ->
          NetflixProfileTile(
            user = user,
            isManaging = isManagingProfiles,
            onClick = {
              if (isManagingProfiles) {
                profileToEdit = user
                editorName = user.name
                editorAvatarKey = user.avatarKey
                editorEmail = user.email
                showProfileEditor = true
              } else {
                if (user.isPatrick) {
                  passwordDialogUser = user
                  enteredPassword = ""
                  passwordError = null
                  val act = context as? FragmentActivity
                  if (act != null && DeviceSecurityManager.isAuthorizedPatrickDevice(context)) {
                    BiometricAuthManager.authenticatePatrickFingerprint(
                      activity = act,
                      onSuccess = {
                        passwordDialogUser = null
                        UserManager.setSelectedUserId(context, user.id)
                        onUserSelected(user)
                      },
                      onFallbackToPassword = {
                        // User chose password fallback
                      },
                      onError = { err ->
                        passwordError = err
                      }
                    )
                  }
                } else {
                  UserManager.setSelectedUserId(context, user.id)
                  onUserSelected(user)
                }
              }
            }
          )
        }

        if (users.size < 6) {
          NetflixAddProfileTile(
            onClick = {
              profileToEdit = null
              editorName = "User ${users.size + 1}"
              editorAvatarKey = "classic_smile"
              editorEmail = ""
              showProfileEditor = true
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(36.dp))

      // Bottom Manage Profiles Button - centered horizontally with shared secondary style
      AppSecondaryButton(
        onClick = { isManagingProfiles = !isManagingProfiles },
        text = if (isManagingProfiles) "DONE" else "MANAGE PROFILES",
        containerColor = if (isManagingProfiles) NetflixRed.copy(alpha = 0.15f) else Color.Transparent,
        contentColor = if (isManagingProfiles) Color.White else NetflixLightGray,
        borderColor = if (isManagingProfiles) NetflixRed else NetflixBorderGray,
        modifier = Modifier.testTag("manage_profiles_btn")
      )
    }
  }

  // Profile Editor / Creation Modal
  if (showProfileEditor) {
    NetflixProfileEditorDialog(
      existingProfile = profileToEdit,
      initialName = editorName,
      initialAvatarKey = editorAvatarKey,
      initialEmail = editorEmail,
      onDismiss = { showProfileEditor = false },
      onSave = { name, avatarKey, email ->
        if (profileToEdit != null) {
          UserManager.editUser(context, profileToEdit!!.id, name, avatarKey, email)
        } else {
          UserManager.addUser(context, name, avatarKey, email)
        }
        users = UserManager.getUsers(context)
        showProfileEditor = false
      },
      onDelete = {
        if (profileToEdit != null && !profileToEdit!!.isPatrick) {
          UserManager.deleteUser(context, profileToEdit!!.id)
          users = UserManager.getUsers(context)
          showProfileEditor = false
        }
      }
    )
  }

  // Password & Biometric Prompt for Patrick
  passwordDialogUser?.let { user ->
    val activity = context as? FragmentActivity
    val isAuthorizedDevice = DeviceSecurityManager.isAuthorizedPatrickDevice(context)

    AlertDialog(
      onDismissRequest = { passwordDialogUser = null },
      containerColor = NetflixCardBg,
      shape = RoundedCornerShape(16.dp),
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ProfileAvatarView(avatarKey = user.avatarKey, modifier = Modifier.size(38.dp))
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text("Patrick Profile Access", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text(
              text = if (isAuthorizedDevice) "Samsung Galaxy S25 Ultra • Knox Vault" else "Hardware Locked: S25 Ultra only",
              fontSize = 11.5.sp,
              color = if (isAuthorizedDevice) Color(0xFF4CAF50) else NetflixRed,
              fontWeight = FontWeight.Medium
            )
          }
        }
      },
      text = {
        Column {
          if (isAuthorizedDevice && activity != null) {
            // Hardware-bound Fingerprint Unlock Option
            Button(
              onClick = {
                BiometricAuthManager.authenticatePatrickFingerprint(
                  activity = activity,
                  onSuccess = {
                    passwordDialogUser = null
                    UserManager.setSelectedUserId(context, user.id)
                    onUserSelected(user)
                  },
                  onFallbackToPassword = {
                    // Stay in dialog, focus on password
                  },
                  onError = { err ->
                    passwordError = err
                  }
                )
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF262626),
                contentColor = Color.White
              ),
              border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.6f)),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("patrick_fingerprint_unlock_btn")
            ) {
              Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Unlock with Fingerprint",
                  fontSize = 13.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
                Text(
                  text = "Locked to S25 Ultra (R5CY14B6HZD)",
                  fontSize = 10.5.sp,
                  color = Color(0xFF4CAF50)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Google Sign-In as in P Task App
            AppSecondaryButton(
              onClick = {
                passwordDialogUser = null
                if (isAuthorizedDevice) {
                  DeviceSecurityManager.bindThisDeviceAsPatrickPhone(context)
                }
                UserManager.setSelectedUserId(context, user.id)
                onUserSelected(user)
              },
              text = "Sign in with Google (${UserManager.PATRICK_EMAIL})",
              icon = Icons.Default.Check,
              containerColor = Color(0xFF1E293B),
              contentColor = Color.White,
              borderColor = Color(0xFF3B82F6),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("google_signin_patrick_btn")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF333333)))
              Text(
                text = " OR ENTER PASSWORD ",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = NetflixLightGray,
                modifier = Modifier.padding(horizontal = 8.dp)
              )
              Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF333333)))
            }

            Spacer(modifier = Modifier.height(14.dp))
          } else {
            // Unauthorized device notice
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.Small)
                .background(NetflixRed.copy(alpha = 0.12f))
                .border(1.dp, NetflixRed.copy(alpha = 0.4f), AppShapes.Small)
                .padding(10.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = null,
                  tint = NetflixRed,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Fingerprint access is locked to Patrick's Samsung Galaxy S25 Ultra (Serial: ${DeviceSecurityManager.TARGET_SERIAL}). On this device, password is required.",
                  fontSize = 11.5.sp,
                  color = Color.White,
                  lineHeight = 15.sp
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))
          }

          Text(
            "Enter password to unlock Patrick's budget:",
            fontSize = 13.sp,
            color = NetflixLightGray
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = enteredPassword,
            onValueChange = {
              enteredPassword = it
              passwordError = null
            },
            label = { Text("Password", color = NetflixLightGray) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = AppShapes.Input,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
              focusedBorderColor = NetflixRed,
              unfocusedBorderColor = Color(0xFF404040),
              cursorColor = NetflixRed
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("patrick_password_input")
          )
          passwordError?.let { err ->
            Text(err, color = NetflixRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
          }

          Spacer(modifier = Modifier.height(8.dp))

          TextButton(
            onClick = {
              try {
                val intent = UserManager.sendResetEmailIntent(context)
                context.startActivity(intent)
              } catch (_: Exception) {
                showPasswordResetNotice = true
              }
            },
            contentPadding = PaddingValues(0.dp)
          ) {
            Icon(Icons.Default.Email, contentDescription = null, tint = NetflixLightGray, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text("Forgot password? Send recovery email", color = NetflixLightGray, fontSize = 12.sp)
          }
        }
      },
      confirmButton = {
        AppPrimaryButton(
          onClick = {
            if (UserManager.verifyPatrickPassword(enteredPassword)) {
              passwordDialogUser = null
              if (isAuthorizedDevice) {
                DeviceSecurityManager.bindThisDeviceAsPatrickPhone(context)
              }
              UserManager.setSelectedUserId(context, user.id)
              onUserSelected(user)
            } else {
              passwordError = "Incorrect password. Try again."
            }
          },
          text = "Unlock Profile",
          containerColor = NetflixRed,
          contentColor = Color.White,
          modifier = Modifier.testTag("submit_patrick_password_btn")
        )
      },
      dismissButton = {
        AppSecondaryButton(
          onClick = { passwordDialogUser = null },
          text = "Cancel",
          contentColor = NetflixLightGray,
          borderColor = Color(0xFF404040)
        )
      }
    )
  }

  // Password Recovery Notice
  if (showPasswordResetNotice) {
    AlertDialog(
      onDismissRequest = { showPasswordResetNotice = false },
      containerColor = NetflixCardBg,
      shape = AppShapes.Dialog,
      title = { Text("Password Recovery Notice", color = Color.White, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "Send an email to ${UserManager.PATRICK_EMAIL} to recover the Patrick profile password (${UserManager.PATRICK_PASSWORD}).",
          color = NetflixLightGray,
          fontSize = 13.5.sp
        )
      },
      confirmButton = {
        AppPrimaryButton(
          onClick = { showPasswordResetNotice = false },
          text = "OK",
          containerColor = NetflixRed,
          contentColor = Color.White
        )
      }
    )
  }
}

@Composable
private fun NetflixProfileTile(
  user: UserProfile,
  isManaging: Boolean,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .width(108.dp)
      .clickable { onClick() }
      .testTag("netflix_profile_tile_${user.id}")
  ) {
    Box(
      modifier = Modifier
        .size(108.dp)
        .clip(AppShapes.Large)
    ) {
      // Avatar image
      ProfileAvatarView(
        avatarKey = user.avatarKey,
        modifier = Modifier.fillMaxSize(),
        shape = AppShapes.Large
      )

      // Managing overlay (Pencil icon)
      if (isManaging) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .border(BorderStroke(2.dp, Color.White), AppShapes.Large),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.7f))
              .border(BorderStroke(1.5.dp, Color.White), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit Profile",
              tint = Color.White,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      } else if (user.isPatrick) {
        // Small lock badge on bottom corner for Patrick
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(5.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.7f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Protected",
            tint = Color(0xFFFBBF24),
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = user.name,
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      color = if (isManaging) Color.White else Color(0xFFE5E5E5),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.width(108.dp)
    )
  }
}

@Composable
private fun NetflixAddProfileTile(
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .width(108.dp)
      .clickable { onClick() }
      .testTag("netflix_add_profile_tile")
  ) {
    Box(
      modifier = Modifier
        .size(108.dp)
        .clip(AppShapes.Large)
        .background(Color(0xFF262626))
        .border(BorderStroke(1.5.dp, Color(0xFF404040)), AppShapes.Large),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Add,
        contentDescription = "Add Profile",
        tint = Color(0xFFA3A3A3),
        modifier = Modifier.size(46.dp)
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "Add Profile",
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      color = NetflixLightGray,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      modifier = Modifier.width(108.dp)
    )
  }
}

@Composable
private fun NetflixProfileEditorDialog(
  existingProfile: UserProfile?,
  initialName: String,
  initialAvatarKey: String,
  initialEmail: String = "",
  onDismiss: () -> Unit,
  onSave: (name: String, avatarKey: String, email: String) -> Unit,
  onDelete: () -> Unit
) {
  var name by remember { mutableStateOf(initialName) }
  var selectedAvatarKey by remember { mutableStateOf(initialAvatarKey) }
  var email by remember { mutableStateOf(if (existingProfile?.isPatrick == true) UserManager.PATRICK_EMAIL else initialEmail) }
  val isCreating = existingProfile == null

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = NetflixCardBg,
    shape = RoundedCornerShape(20.dp),
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isCreating) "Create Profile" else "Edit Profile",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 20.sp
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = NetflixLightGray)
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        // Selected Avatar Preview
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141414))
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          ProfileAvatarView(
            avatarKey = selectedAvatarKey,
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(10.dp)
          )
          Spacer(modifier = Modifier.width(14.dp))
          Column {
            Text(
              text = AvatarRegistry.getAvatar(selectedAvatarKey).title,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
            Text(
              text = "Tap any avatar below to switch",
              color = NetflixLightGray,
              fontSize = 12.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Name Field
        Text(
          text = "Profile Name",
          color = Color.White,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          placeholder = { Text("Enter profile name", color = NetflixLightGray) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = NetflixRed,
            unfocusedBorderColor = Color(0xFF404040),
            cursorColor = NetflixRed,
            focusedContainerColor = Color(0xFF141414),
            unfocusedContainerColor = Color(0xFF141414)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_name_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Google Drive / Gmail Sync Account
        Text(
          text = "Google Drive Sync Account (Gmail)",
          color = Color.White,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = email,
          onValueChange = { if (existingProfile?.isPatrick != true) email = it },
          readOnly = existingProfile?.isPatrick == true,
          placeholder = { Text("user@gmail.com", color = NetflixLightGray) },
          singleLine = true,
          leadingIcon = {
            Icon(Icons.Default.Email, contentDescription = null, tint = if (existingProfile?.isPatrick == true) Color(0xFF4CAF50) else NetflixLightGray, modifier = Modifier.size(18.dp))
          },
          supportingText = {
            Text(
              text = if (existingProfile?.isPatrick == true) "Patrick's primary cloud sync account" else "Continuous cloud sync across devices",
              color = NetflixLightGray,
              fontSize = 11.sp
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = NetflixRed,
            unfocusedBorderColor = Color(0xFF404040),
            cursorColor = NetflixRed,
            focusedContainerColor = Color(0xFF141414),
            unfocusedContainerColor = Color(0xFF141414)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_email_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Choose Netflix Avatar section
        Text(
          text = "Choose Netflix Avatar",
          color = Color.White,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.5.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Avatar Options Grid
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          AvatarRegistry.ALL_AVATARS.take(4).forEach { avatar ->
            val isSelected = avatar.key == selectedAvatarKey
            Box(
              modifier = Modifier
                .weight(1f)
                .clickable { selectedAvatarKey = avatar.key }
                .border(
                  BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color(0xFF333333)),
                  RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
            ) {
              ProfileAvatarView(avatarKey = avatar.key, modifier = Modifier.size(54.dp), shape = RoundedCornerShape(8.dp))
              if (isSelected) {
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(NetflixRed),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          AvatarRegistry.ALL_AVATARS.drop(4).take(4).forEach { avatar ->
            val isSelected = avatar.key == selectedAvatarKey
            Box(
              modifier = Modifier
                .weight(1f)
                .clickable { selectedAvatarKey = avatar.key }
                .border(
                  BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color(0xFF333333)),
                  RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
            ) {
              ProfileAvatarView(avatarKey = avatar.key, modifier = Modifier.size(54.dp), shape = RoundedCornerShape(8.dp))
              if (isSelected) {
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(NetflixRed),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
              }
            }
          }
        }

        // Delete button for non-Patrick users
        if (!isCreating && existingProfile != null && !existingProfile.isPatrick) {
          Spacer(modifier = Modifier.height(20.dp))
          AppSecondaryButton(
            onClick = onDelete,
            text = "Delete Profile",
            icon = Icons.Default.Delete,
            contentColor = Color(0xFFEF4444),
            borderColor = Color(0xFFEF4444).copy(alpha = 0.7f),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("delete_profile_btn")
          )
        }
      }
    },
    confirmButton = {
      AppPrimaryButton(
        onClick = { onSave(name, selectedAvatarKey, email) },
        enabled = name.isNotBlank(),
        text = "Save",
        containerColor = NetflixRed,
        contentColor = Color.White,
        modifier = Modifier.testTag("save_profile_btn")
      )
    },
    dismissButton = {
      AppSecondaryButton(
        onClick = onDismiss,
        text = "Cancel",
        contentColor = NetflixLightGray,
        borderColor = Color(0xFF404040)
      )
    }
  )
}
