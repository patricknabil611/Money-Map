package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Sha2aItem
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sha2aEditorSheet(
  item: Sha2aItem?,
  defaultSide: String,
  projectId: String = "sha2a",
  onDismiss: () -> Unit,
  onSave: (Sha2aItem) -> Unit,
  onDelete: (String) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val isNew = item == null

  var side by remember { mutableStateOf(item?.side ?: defaultSide) }
  var name by remember { mutableStateOf(item?.name ?: "") }
  var amountStr by remember { mutableStateOf(if (item != null && item.amount > 0) item.amount.toString().removeSuffix(".0") else "") }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var deleteArmed by remember { mutableStateOf(false) }

  LaunchedEffect(deleteArmed) {
    if (deleteArmed) {
      delay(3500)
      deleteArmed = false
    }
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
        .navigationBarsPadding()
        .padding(bottom = 24.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isNew) "New item" else "Edit item",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = colors.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        AppIconButton(
          onClick = onDismiss,
          icon = Icons.Default.Close,
          contentDescription = "Close",
          tint = colors.muted,
          modifier = Modifier.testTag("close_sha2a_sheet_btn")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(AppShapes.Medium)
          .background(colors.surface2)
          .padding(4.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(AppShapes.Small)
            .background(if (side == "mario") colors.surface else Color.Transparent)
            .clickable { side = "mario" }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Mario",
            fontWeight = FontWeight.SemiBold,
            color = if (side == "mario") colors.ink else colors.muted
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(AppShapes.Small)
            .background(if (side == "mariam") colors.surface else Color.Transparent)
            .clickable { side = "mariam" }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Mariam",
            fontWeight = FontWeight.SemiBold,
            color = if (side == "mariam") colors.ink else colors.muted
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(AppShapes.Small)
            .background(if (side == "shared") colors.surface else Color.Transparent)
            .clickable { side = "shared" }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Together (50/50)",
            fontWeight = FontWeight.SemiBold,
            color = if (side == "shared") colors.ink else colors.muted
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = name,
        onValueChange = { name = it; errorMsg = null },
        label = { Text("Description") },
        shape = AppShapes.Input,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sha2a_name_input"),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(10.dp))

      OutlinedTextField(
        value = amountStr,
        onValueChange = { amountStr = it; errorMsg = null },
        label = { Text("Amount") },
        shape = AppShapes.Input,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sha2a_amount_input"),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
      )

      errorMsg?.let { err ->
        Text(
          text = err,
          color = colors.expense,
          fontSize = 13.5.sp,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      if (isNew) {
        AppPrimaryButton(
          onClick = {
            if (name.isBlank()) {
              errorMsg = "Add a description."
              return@AppPrimaryButton
            }
            val a = ArabicNumberHelper.parseNumber(amountStr)
            if (a == null || a < 0) {
              errorMsg = "Enter the amount."
              return@AppPrimaryButton
            }
            val out = item?.copy(name = name.trim(), amount = a, side = side)
              ?: Sha2aItem(
                id = "sha2a-${System.currentTimeMillis()}",
                name = name.trim(),
                amount = a,
                side = side,
                sortOrder = (System.currentTimeMillis() % 100000).toInt(),
                projectId = projectId
              )
            onSave(out)
          },
          text = "Add",
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_sha2a_btn")
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          AppSecondaryButton(
            onClick = {
              if (deleteArmed) {
                if (item != null) onDelete(item.id)
              } else {
                deleteArmed = true
              }
            },
            text = if (deleteArmed) "Confirm delete" else "Delete",
            icon = Icons.Default.Delete,
            contentColor = colors.expense,
            borderColor = colors.expense,
            containerColor = if (deleteArmed) colors.expense.copy(alpha = 0.15f) else Color.Transparent,
            modifier = Modifier
              .weight(1f)
              .testTag("delete_sha2a_btn")
          )

          AppPrimaryButton(
            onClick = {
              if (name.isBlank()) {
                errorMsg = "Add a description."
                return@AppPrimaryButton
              }
              val a = ArabicNumberHelper.parseNumber(amountStr)
              if (a == null || a < 0) {
                errorMsg = "Enter the amount."
                return@AppPrimaryButton
              }
              val out = item?.copy(name = name.trim(), amount = a, side = side)
                ?: Sha2aItem(
                  id = "sha2a-${System.currentTimeMillis()}",
                  name = name.trim(),
                  amount = a,
                  side = side,
                  sortOrder = (System.currentTimeMillis() % 100000).toInt(),
                  projectId = projectId
                )
              onSave(out)
            },
            text = "Save changes",
            modifier = Modifier
              .weight(1f)
              .testTag("save_sha2a_btn")
          )
        }
      }
    }
  }
}
