package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PhotoReceipt
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoLightboxSheet(
  photo: PhotoReceipt,
  onDismiss: () -> Unit,
  onSaveCaption: (String) -> Unit,
  onDelete: () -> Unit
) {
  val context = LocalContext.current
  val colors = LocalMoneyMapColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var caption by remember { mutableStateOf(photo.caption) }
  var deleteArmed by remember { mutableStateOf(false) }

  LaunchedEffect(deleteArmed) {
    if (deleteArmed) {
      delay(3500)
      deleteArmed = false
    }
  }

  val file = remember(photo.localFileName) {
    File(context.filesDir, "receipts/${photo.localFileName}")
  }

  val dateStr = remember(photo.createdAt) {
    val df = SimpleDateFormat("d MMM yyyy", Locale.US)
    df.format(Date(photo.createdAt))
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = colors.surface,
    shape = AppShapes.Sheet
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = photo.caption.ifBlank { "Receipt" },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Added $dateStr",
            fontSize = 13.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        AppIconButton(
          onClick = onDismiss,
          icon = Icons.Default.Close,
          contentDescription = "Close",
          tint = colors.muted,
          modifier = Modifier.testTag("close_lightbox_btn")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Receipt Image
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 200.dp, max = 380.dp)
          .clip(AppShapes.Card)
          .background(colors.surface2),
        contentAlignment = Alignment.Center
      ) {
        AsyncImage(
          model = file,
          contentDescription = photo.caption.ifBlank { "Receipt photo" },
          contentScale = ContentScale.Fit,
          modifier = Modifier.fillMaxWidth()
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = caption,
        onValueChange = { caption = it },
        label = { Text("Caption") },
        placeholder = { Text("What is this receipt for?") },
        shape = AppShapes.Input,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = colors.head,
          unfocusedBorderColor = colors.line
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("photo_caption_input"),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(20.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        AppSecondaryButton(
          text = if (deleteArmed) "Tap again" else "Delete photo",
          icon = Icons.Default.Delete,
          onClick = {
            if (deleteArmed) {
              onDelete()
            } else {
              deleteArmed = true
            }
          },
          containerColor = if (deleteArmed) colors.expense else Color.Transparent,
          contentColor = if (deleteArmed) Color.White else colors.expense,
          borderColor = if (deleteArmed) colors.expense else colors.expense.copy(alpha = 0.5f),
          modifier = Modifier
            .weight(1f)
            .testTag("delete_photo_btn")
        )

        AppPrimaryButton(
          text = "Save caption",
          onClick = { onSaveCaption(caption.trim()) },
          modifier = Modifier
            .weight(1f)
            .testTag("save_caption_btn")
        )
      }
    }
  }
}
