package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.security.BiometricAuthManager
import com.example.security.BiometricAvailability
import com.example.ui.theme.LocalMoneyMapColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BiometricLockScreen(
  onUnlocked: () -> Unit
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  val colors = LocalMoneyMapColors.current

  var isAssigned by remember { mutableStateOf(BiometricAuthManager.isFingerprintAssigned(context)) }
  var isEnabled by remember { mutableStateOf(BiometricAuthManager.isFingerprintEnabled(context)) }
  var hasPin by remember { mutableStateOf(BiometricAuthManager.hasBackupPin(context)) }
  val availability = remember { BiometricAuthManager.checkBiometricAvailability(context) }

  var statusMessage by remember { mutableStateOf<String?>(null) }
  var isError by remember { mutableStateOf(false) }
  var showPinDialog by remember { mutableStateOf(false) }
  var showSetPinDialog by remember { mutableStateOf(false) }

  // Pulsing animation for the fingerprint scanner icon
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulseScale"
  )

  fun triggerFingerprintAuth() {
    if (activity == null) {
      statusMessage = "Cannot initialize biometric scanner."
      isError = true
      return
    }

    statusMessage = "Place your finger on the sensor..."
    isError = false

    BiometricAuthManager.authenticateWithFingerprint(
      activity = activity,
      onSuccess = {
        statusMessage = "Fingerprint verified!"
        isError = false
        onUnlocked()
      },
      onError = { err ->
        statusMessage = err
        isError = true
      },
      onUseBackupPin = {
        showPinDialog = true
      }
    )
  }

  fun triggerAssignFingerprint() {
    if (activity == null) {
      statusMessage = "Cannot initialize biometric scanner."
      isError = true
      return
    }

    statusMessage = "Touch sensor to assign your fingerprint..."
    isError = false

    BiometricAuthManager.assignSingleFingerprint(
      activity = activity,
      onSuccess = {
        isAssigned = true
        isEnabled = true
        statusMessage = "Fingerprint successfully bound to Money Map!"
        isError = false
        // Automatically unlock once assigned
        onUnlocked()
      },
      onError = { err ->
        statusMessage = err
        isError = true
      }
    )
  }

  // Auto trigger fingerprint prompt when screen appears if fingerprint is assigned
  LaunchedEffect(isAssigned, availability) {
    if (isAssigned && availability == BiometricAvailability.READY) {
      triggerFingerprintAuth()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg)
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 24.dp, vertical = 32.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp)
    ) {
      // App Logo & Header
      Box(
        modifier = Modifier
          .size(92.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(colors.head)
          .border(2.dp, colors.year.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_launcher_fg),
          contentDescription = "Money Map Logo",
          modifier = Modifier.size(76.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Money Map",
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = colors.ink
      )

      Text(
        text = "Patrick’s Financial Planner",
        fontSize = 14.sp,
        color = colors.muted
      )

      Spacer(modifier = Modifier.height(28.dp))

      // Status indicator card
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          // Fingerprint Circle Button / Graphic
          Box(
            modifier = Modifier
              .size(110.dp)
              .scale(if (isAssigned && availability == BiometricAvailability.READY) pulseScale else 1f)
              .clip(CircleShape)
              .background(if (isError) colors.expense.copy(alpha = 0.15f) else colors.head.copy(alpha = 0.12f))
              .border(
                width = 2.dp,
                color = if (isError) colors.expense else colors.head,
                shape = CircleShape
              )
              .clickable {
                if (isAssigned) {
                  triggerFingerprintAuth()
                } else if (availability == BiometricAvailability.READY) {
                  triggerAssignFingerprint()
                } else {
                  showPinDialog = true
                }
              }
              .testTag("fingerprint_sensor_touch"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isAssigned) Icons.Default.Fingerprint else Icons.Default.Security,
              contentDescription = "Fingerprint Sensor",
              tint = if (isError) colors.expense else colors.head,
              modifier = Modifier.size(62.dp)
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          if (isAssigned) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.income,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "1 Assigned Fingerprint",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.income
              )
            }

            val assignedTime = BiometricAuthManager.getAssignedTimestamp(context)
            if (assignedTime > 0) {
              val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(assignedTime))
              Text(
                text = "Bound cryptographically on $dateStr",
                fontSize = 11.5.sp,
                color = colors.muted
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = { triggerFingerprintAuth() },
              colors = ButtonDefaults.buttonColors(
                containerColor = colors.head,
                contentColor = colors.headInk
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("scan_fingerprint_btn")
            ) {
              Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Unlock with Fingerprint", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
          } else {
            // Not yet assigned
            Text(
              text = "Assign Your Fingerprint",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = colors.ink
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Bind exactly one fingerprint to protect your budget data. If any new fingerprint is registered on this device, the key will automatically invalidate for security.",
              fontSize = 13.sp,
              color = colors.muted,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (availability == BiometricAvailability.READY) {
              Button(
                onClick = { triggerAssignFingerprint() },
                colors = ButtonDefaults.buttonColors(
                  containerColor = colors.head,
                  contentColor = colors.headInk
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("assign_fingerprint_btn")
              ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Assign My Fingerprint", fontWeight = FontWeight.Bold, fontSize = 15.sp)
              }
            } else {
              // Biometric sensor not ready on this hardware or emulator
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(colors.year.copy(alpha = 0.12f))
                  .padding(10.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = colors.year, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = when (availability) {
                      BiometricAvailability.NOT_ENROLLED -> "No fingerprint enrolled in Android Settings."
                      BiometricAvailability.NO_HARDWARE -> "Biometric hardware not detected on this device/emulator."
                      else -> "Biometric sensor currently unavailable."
                    },
                    fontSize = 12.sp,
                    color = colors.ink
                  )
                }
              }
            }
          }

          // Error or Status feedback
          AnimatedVisibility(
            visible = statusMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            statusMessage?.let { msg ->
              Spacer(modifier = Modifier.height(14.dp))
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .background(if (isError) colors.expense.copy(alpha = 0.12f) else colors.income.copy(alpha = 0.12f))
                  .padding(10.dp)
              ) {
                Text(
                  text = msg,
                  fontSize = 12.5.sp,
                  color = if (isError) colors.expense else colors.income,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Secondary Actions: PIN / Setup
      Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        if (hasPin) {
          OutlinedButton(
            onClick = { showPinDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("use_pin_btn")
          ) {
            Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.ink)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Use Backup PIN", color = colors.ink, fontWeight = FontWeight.SemiBold)
          }
        } else {
          OutlinedButton(
            onClick = { showSetPinDialog = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("set_pin_btn")
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.ink)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Set Backup PIN", color = colors.ink, fontWeight = FontWeight.SemiBold)
          }
        }

        // For emulator environments where no hardware fingerprint sensor exists, provide an entry option
        if (availability != BiometricAvailability.READY && !isAssigned) {
          Spacer(modifier = Modifier.width(10.dp))
          Button(
            onClick = { onUnlocked() },
            colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.ink),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("skip_unlock_btn")
          ) {
            Text("Continue to App", fontSize = 13.sp, fontWeight = FontWeight.Medium)
          }
        }
      }
    }
  }

  // Backup PIN Entry Dialog
  if (showPinDialog) {
    PinEntryDialog(
      title = "Enter Backup PIN",
      subtitle = "Unlock Money Map with your 4-digit PIN",
      onDismiss = { showPinDialog = false },
      onSubmit = { enteredPin ->
        if (BiometricAuthManager.verifyBackupPin(context, enteredPin)) {
          showPinDialog = false
          onUnlocked()
          true
        } else {
          false
        }
      }
    )
  }

  // Set Backup PIN Dialog
  if (showSetPinDialog) {
    PinEntryDialog(
      title = "Set Backup PIN",
      subtitle = "Enter a 4-digit PIN in case fingerprint is unavailable",
      onDismiss = { showSetPinDialog = false },
      onSubmit = { enteredPin ->
        if (enteredPin.length == 4) {
          BiometricAuthManager.setBackupPin(context, enteredPin)
          hasPin = true
          showSetPinDialog = false
          statusMessage = "Backup PIN successfully set!"
          isError = false
          true
        } else {
          false
        }
      }
    )
  }
}

@Composable
fun PinEntryDialog(
  title: String,
  subtitle: String,
  onDismiss: () -> Unit,
  onSubmit: (String) -> Boolean // returns true if success
) {
  val colors = LocalMoneyMapColors.current
  var pin by remember { mutableStateOf("") }
  var hasError by remember { mutableStateOf(false) }

  androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
    Card(
      shape = RoundedCornerShape(20.dp),
      colors = CardDefaults.cardColors(containerColor = colors.surface),
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .testTag("pin_entry_dialog")
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
      ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.ink)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = colors.muted, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(24.dp))

        // PIN dot indicators (4 digits)
        Row(
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()
        ) {
          for (i in 0 until 4) {
            val filled = i < pin.length
            Box(
              modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(
                  when {
                    hasError -> colors.expense
                    filled -> colors.head
                    else -> colors.line
                  }
                )
            )
          }
        }

        if (hasError) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Incorrect PIN. Try again.",
            fontSize = 12.sp,
            color = colors.expense
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Numeric Keypad
        val keys = listOf(
          listOf("1", "2", "3"),
          listOf("4", "5", "6"),
          listOf("7", "8", "9"),
          listOf("C", "0", "DEL")
        )

        keys.forEach { rowKeys ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            rowKeys.forEach { key ->
              Box(
                modifier = Modifier
                  .size(60.dp)
                  .clip(CircleShape)
                  .clickable {
                    hasError = false
                    when (key) {
                      "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                      "C" -> pin = ""
                      else -> {
                        if (pin.length < 4) {
                          pin += key
                          if (pin.length == 4) {
                            val success = onSubmit(pin)
                            if (!success) {
                              hasError = true
                              pin = ""
                            }
                          }
                        }
                      }
                    }
                  }
                  .testTag("pin_key_$key"),
                contentAlignment = Alignment.Center
              ) {
                if (key == "DEL") {
                  Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = colors.ink, modifier = Modifier.size(20.dp))
                } else {
                  Text(
                    text = key,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink
                  )
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().testTag("pin_cancel_btn")
        ) {
          Text("Cancel", color = colors.muted)
        }
      }
    }
  }
}
