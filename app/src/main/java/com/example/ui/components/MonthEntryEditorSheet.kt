package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MonthEntry
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.ScheduleEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthEntryEditorSheet(
  entry: MonthEntry,
  isNew: Boolean,
  onDismiss: () -> Unit,
  onSave: (MonthEntry) -> Unit,
  onDelete: (String) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var name by remember(entry.id) { mutableStateOf(entry.name) }
  var amountStr by remember(entry.id) {
    mutableStateOf(if (entry.amount > 0) entry.amount.toString().removeSuffix(".0") else "")
  }
  var dayStr by remember(entry.id) { mutableStateOf(entry.day?.toString() ?: "") }
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
        Column(modifier = Modifier.weight(1f)) {
          val title = if (isNew) {
            if (entry.kind == "in") "New income" else "New expense"
          } else {
            "Edit ${entry.name.ifBlank { "item" }}"
          }
          Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${ScheduleEngine.mLabel(entry.month)} only",
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
          modifier = Modifier.testTag("close_month_entry_sheet_btn")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = name,
        onValueChange = { name = it; errorMsg = null },
        label = { Text("Name") },
        placeholder = { Text(if (entry.kind == "in") "Salary, Bonus…" else "Groceries, Internet…") },
        shape = AppShapes.Input,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("month_entry_name_input"),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(10.dp))

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = amountStr,
          onValueChange = { amountStr = it; errorMsg = null },
          label = { Text("Amount") },
          placeholder = { Text("0") },
          shape = AppShapes.Input,
          modifier = Modifier
            .weight(1f)
            .testTag("month_entry_amount_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        OutlinedTextField(
          value = dayStr,
          onValueChange = { dayStr = it; errorMsg = null },
          label = { Text("Day of month") },
          placeholder = { Text("Any day") },
          shape = AppShapes.Input,
          modifier = Modifier
            .weight(1f)
            .testTag("month_entry_day_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
      }

      errorMsg?.let { err ->
        Text(
          text = err,
          color = colors.expense,
          fontSize = 13.5.sp,
          modifier = Modifier.padding(top = 8.dp)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      fun validateAndSave() {
        if (name.isBlank()) {
          errorMsg = "Give this item a name."
          return
        }
        val a = ArabicNumberHelper.parseNumber(amountStr)
        if (a == null || a < 0) {
          errorMsg = "Enter a valid amount."
          return
        }
        val d = ArabicNumberHelper.parseInt(dayStr)
        if (d != null && (d < 1 || d > 31)) {
          errorMsg = "Day of month should be between 1 and 31."
          return
        }
        onSave(
          entry.copy(
            name = name.trim(),
            amount = a,
            day = d
          )
        )
      }

      if (isNew) {
        AppPrimaryButton(
          onClick = { validateAndSave() },
          text = if (entry.kind == "in") "Add Income" else "Add Expense",
          icon = Icons.Default.Add,
          containerColor = if (entry.kind == "in") colors.income else colors.head,
          contentColor = if (entry.kind == "in") Color.White else colors.headInk,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_month_entry_btn")
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
                onDelete(entry.id)
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
              .testTag("delete_month_entry_btn")
          )

          AppPrimaryButton(
            onClick = { validateAndSave() },
            text = "Save",
            icon = Icons.Default.Check,
            containerColor = colors.head,
            contentColor = colors.headInk,
            modifier = Modifier
              .weight(1f)
              .testTag("save_month_entry_btn")
          )
        }
      }
    }
  }
}
