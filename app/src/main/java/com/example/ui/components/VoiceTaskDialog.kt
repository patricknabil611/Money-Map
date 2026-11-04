package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.VoiceNlpEngine
import com.example.util.VoiceParsedTask
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VoiceTaskDialog(
  onDismiss: () -> Unit,
  onConfirmTask: (VoiceParsedTask, setReminder: Boolean) -> Unit
) {
  val context = LocalContext.current
  val colors = LocalMoneyMapColors.current
  val scope = rememberCoroutineScope()

  var isListening by remember { mutableStateOf(false) }
  var spokenText by remember { mutableStateOf("") }
  var isProcessingAi by remember { mutableStateOf(false) }
  var parsedResult by remember { mutableStateOf<VoiceParsedTask?>(null) }
  var setReminder by remember { mutableStateOf(true) }
  var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

  // SpeechRecognizer setup
  DisposableEffect(Unit) {
    if (SpeechRecognizer.isRecognitionAvailable(context)) {
      val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
      recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { isListening = true }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { isListening = false }
        override fun onError(error: Int) {
          isListening = false
        }
        override fun onResults(results: Bundle?) {
          isListening = false
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          if (!matches.isNullOrEmpty()) {
            val recognized = matches[0]
            spokenText = recognized
            // Auto parse with AI
            scope.launch {
              isProcessingAi = true
              val parsed = VoiceNlpEngine.parseVoiceCommand(recognized)
              parsedResult = parsed
              isProcessingAi = false
            }
          }
        }
        override fun onPartialResults(partialResults: Bundle?) {
          val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          if (!matches.isNullOrEmpty()) {
            spokenText = matches[0]
          }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
      })
      speechRecognizer = recognizer
    }

    onDispose {
      try {
        speechRecognizer?.destroy()
      } catch (_: Exception) {}
    }
  }

  fun startListening() {
    parsedResult = null
    spokenText = ""
    try {
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a budget item, expense, or task...")
      }
      speechRecognizer?.startListening(intent)
      isListening = true
    } catch (_: Exception) {
      isListening = false
    }
  }

  fun stopListening() {
    try {
      speechRecognizer?.stopListening()
    } catch (_: Exception) {}
    isListening = false
  }

  // Auto start on dialog launch
  LaunchedEffect(Unit) {
    startListening()
  }

  val infiniteTransition = rememberInfiniteTransition()
  val micScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = if (isListening) 1.25f else 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(700),
      repeatMode = RepeatMode.Reverse
    )
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF1E1E1E),
    shape = AppShapes.Dialog,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Voice & NLP Quick Entry",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        AppIconButton(
          onClick = onDismiss,
          icon = Icons.Default.Close,
          contentDescription = "Close",
          tint = Color(0xFFA3A3A3)
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Glowing Animated Mic Button
        Box(
          modifier = Modifier
            .size(76.dp)
            .scale(micScale)
            .clip(CircleShape)
            .background(if (isListening) Color(0xFFE50914) else Color(0xFF2A2A2A))
            .clickable {
              if (isListening) stopListening() else startListening()
            }
            .testTag("voice_dialog_mic_btn"),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = "Microphone",
            tint = Color.White,
            modifier = Modifier.size(38.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = if (isListening) "Listening... (English or Arabic)" else if (isProcessingAi) "Processing with Gemini AI..." else "Tap mic to speak",
          color = if (isListening) Color(0xFFE50914) else Color(0xFFA3A3A3),
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Text input field showing recognized words or manual typing
        OutlinedTextField(
          value = spokenText,
          onValueChange = {
            spokenText = it
            if (it.isNotBlank()) {
              parsedResult = VoiceNlpEngine.parseOnDeviceRuleBased(it)
            }
          },
          placeholder = { Text("e.g. 'Add expense 350 for groceries on day 5'", color = Color(0xFF737373), fontSize = 13.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFFE50914),
            unfocusedBorderColor = Color(0xFF404040),
            cursorColor = Color(0xFFE50914),
            focusedContainerColor = Color(0xFF141414),
            unfocusedContainerColor = Color(0xFF141414)
          ),
          shape = AppShapes.Input,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_spoken_text_input")
        )

        if (isProcessingAi) {
          Spacer(modifier = Modifier.height(12.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFE50914), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("AI extracting amount, day, and category...", fontSize = 11.5.sp, color = Color(0xFFA3A3A3))
          }
        }

        // Parsed Result Card
        parsedResult?.let { res ->
          Spacer(modifier = Modifier.height(14.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(AppShapes.Card)
              .background(Color(0xFF141414))
              .border(1.dp, Color(0xFF333333), AppShapes.Card)
              .padding(12.dp)
          ) {
            Column {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = when (res.kind) {
                    "in" -> "INCOME"
                    "loan" -> "LOAN / INSTALLMENT"
                    "task" -> "TASK / REMINDER"
                    else -> "EXPENSE"
                  },
                  color = when (res.kind) {
                    "in" -> Color(0xFF10B981)
                    "loan" -> Color(0xFFF59E0B)
                    "task" -> Color(0xFF3B82F6)
                    else -> Color(0xFFEF4444)
                  },
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
                if (res.amount > 0) {
                  Text(
                    text = "${res.amount} EGP",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = res.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
              )

              if (res.day != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "Scheduled: Day ${res.day} of every month",
                  color = Color(0xFF9CA3AF),
                  fontSize = 11.5.sp
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              // Reminder toggle row
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(AppShapes.Small)
                  .background(Color(0xFF222222))
                  .clickable { setReminder = !setReminder }
                  .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.NotificationsActive,
                  contentDescription = null,
                  tint = if (setReminder) Color(0xFF10B981) else Color(0xFF6B7280),
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (setReminder) "Push notification alert enabled" else "Notification alert off",
                  color = if (setReminder) Color(0xFF10B981) else Color(0xFF9CA3AF),
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      AppPrimaryButton(
        text = "Add to P Money",
        enabled = parsedResult != null,
        containerColor = Color(0xFFE50914),
        contentColor = Color.White,
        onClick = {
          parsedResult?.let { task ->
            onConfirmTask(task, setReminder)
            onDismiss()
          }
        },
        modifier = Modifier.testTag("confirm_voice_task_btn")
      )
    },
    dismissButton = {
      AppTextButton(
        text = "Cancel",
        contentColor = Color(0xFFA3A3A3),
        onClick = onDismiss
      )
    }
  )
}
