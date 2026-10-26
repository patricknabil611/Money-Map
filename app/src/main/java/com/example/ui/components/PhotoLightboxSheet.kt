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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PhotoReceipt
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
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp)
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = photo.caption.ifBlank { "Receipt" },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
          )
          Text(
            text = "Added $dateStr",
            fontSize = 13.sp,
            color = colors.muted
          )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_lightbox_btn")) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Receipt Image
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 200.dp, max = 380.dp)
          .clip(RoundedCornerShape(12.dp))
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
        modifier = Modifier
          .fillMaxWidth()
          .testTag("photo_caption_input"),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Button(
          onClick = {
            if (deleteArmed) {
              onDelete()
            } else {
              deleteArmed = true
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (deleteArmed) colors.expense else Color.Transparent,
            contentColor = if (deleteArmed) Color.White else colors.expense
          ),
          border = if (!deleteArmed) ButtonDefaults.outlinedButtonBorder else null,
          modifier = Modifier.testTag("delete_photo_btn")
        ) {
          Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (deleteArmed) "Tap again" else "Delete photo")
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
          onClick = { onSaveCaption(caption.trim()) },
          colors = ButtonDefaults.buttonColors(
            containerColor = colors.head,
            contentColor = colors.headInk
          ),
          modifier = Modifier.testTag("save_caption_btn")
        ) {
          Text("Save caption", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
