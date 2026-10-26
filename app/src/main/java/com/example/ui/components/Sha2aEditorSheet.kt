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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Sha2aItem
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
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isNew) "New item" else "Edit item",
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = colors.ink
        )
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sha2a_sheet_btn")) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Side Segmented Button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(colors.surface2)
          .padding(4.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (side == "in") colors.surface else Color.Transparent)
            .clickable { side = "in" }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "In",
            fontWeight = FontWeight.SemiBold,
            color = if (side == "in") colors.ink else colors.muted
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (side != "in") colors.surface else Color.Transparent)
            .clickable { side = "out" }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Out",
            fontWeight = FontWeight.SemiBold,
            color = if (side != "in") colors.ink else colors.muted
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = name,
        onValueChange = { name = it; errorMsg = null },
        label = { Text("Description") },
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

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (!isNew && item != null) {
          Button(
            onClick = {
              if (deleteArmed) {
                onDelete(item.id)
              } else {
                deleteArmed = true
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (deleteArmed) colors.expense else Color.Transparent,
              contentColor = if (deleteArmed) Color.White else colors.expense
            ),
            border = if (!deleteArmed) ButtonDefaults.outlinedButtonBorder else null,
            modifier = Modifier.testTag("delete_sha2a_btn")
          ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (deleteArmed) "Tap again" else "Delete")
          }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
          onClick = {
            if (name.isBlank()) {
              errorMsg = "Add a description."
              return@Button
            }
            val a = ArabicNumberHelper.parseNumber(amountStr)
            if (a == null || a < 0) {
              errorMsg = "Enter the amount."
              return@Button
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
          colors = ButtonDefaults.buttonColors(
            containerColor = colors.head,
            contentColor = colors.headInk
          ),
          modifier = Modifier.testTag("save_sha2a_btn")
        ) {
          Text(if (isNew) "Add" else "Save changes", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
