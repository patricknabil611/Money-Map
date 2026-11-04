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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetRow
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.ScheduleEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellEditorSheet(
  row: BudgetRow,
  month: String,
  rangeStart: String,
  rangeEnd: String,
  onDismiss: () -> Unit,
  onSaveOnly: (Double) -> Unit,
  onSaveFrom: (Double) -> Unit,
  onReset: () -> Unit,
  onEditWholeRow: () -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val currentAmt = remember(row, month) {
    ScheduleEngine.amt(row, month, rangeStart, rangeEnd)
  }
  val planAmt = remember(row, month) {
    ScheduleEngine.planAmt(row, month, rangeStart, rangeEnd)
  }
  val hasOverride = remember(row, month) {
    row.getOverrides().containsKey(month)
  }
  val sc = remember(row) {
    ScheduleEngine.computeSchedule(row, rangeStart, rangeEnd)
  }

  var inputVal by remember {
    mutableStateOf(if (currentAmt > 0) currentAmt.toString().removeSuffix(".0") else "")
  }
  var errorMsg by remember { mutableStateOf<String?>(null) }

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
          Text(
            text = row.name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          val daySuffix = if (row.day != null) ", on the ${ArabicNumberHelper.ord(row.day)}" else ""
          Text(
            text = "${ScheduleEngine.mLabel(month)}$daySuffix",
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
          modifier = Modifier.testTag("close_cell_sheet_btn")
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      val payInfo = remember(row, month) {
        if (sc.finite) {
          val info = ScheduleEngine.payInfo(row, month, rangeStart, rangeEnd)
          ". " + info.replace(Regex("^Payment.*?, "), "")
        } else ""
      }
      val overrideNote = if (hasOverride) ". This month has its own amount." else ""

      Text(
        text = "Planned amount: ${ArabicNumberHelper.fmt(planAmt)}$payInfo$overrideNote",
        fontSize = 13.5.sp,
        color = colors.muted
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = inputVal,
        onValueChange = {
          inputVal = it
          errorMsg = null
        },
        label = { Text("Amount for ${ScheduleEngine.mLabel(month)}") },
        placeholder = { Text("0") },
        shape = AppShapes.Input,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("cell_amount_input"),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
      )

      errorMsg?.let { err ->
        Text(
          text = err,
          color = colors.expense,
          fontSize = 13.5.sp,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        AppPrimaryButton(
          onClick = {
            val num = ArabicNumberHelper.parseNumber(inputVal)
            if (num == null || num < 0) {
              errorMsg = "Enter a number, or 0 to skip this month."
            } else {
              onSaveOnly(num)
            }
          },
          text = "Save for ${ScheduleEngine.mLabel(month)} only",
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_month_only_btn")
        )

        AppSecondaryButton(
          onClick = {
            val num = ArabicNumberHelper.parseNumber(inputVal)
            if (num == null || num < 0) {
              errorMsg = "Enter a number, or 0 to skip this month."
            } else {
              onSaveFrom(num)
            }
          },
          text = "Save for ${ScheduleEngine.mLabel(month)} and after",
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_month_and_after_btn")
        )

        if (hasOverride) {
          AppSecondaryButton(
            onClick = onReset,
            text = "Go back to the planned amount",
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reset_planned_btn")
          )
        }

        AppSecondaryButton(
          onClick = onEditWholeRow,
          text = "Edit the whole row",
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_whole_row_btn")
        )
      }
    }
  }
}
