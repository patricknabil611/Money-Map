package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetRow
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
        Column {
          Text(
            text = row.name,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
          )
          val daySuffix = if (row.day != null) ", on the ${ArabicNumberHelper.ord(row.day)}" else ""
          Text(
            text = "${ScheduleEngine.mLabel(month)}$daySuffix",
            fontSize = 14.sp,
            color = colors.muted
          )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_cell_sheet_btn")) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
        }
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

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = {
            val num = ArabicNumberHelper.parseNumber(inputVal)
            if (num == null || num < 0) {
              errorMsg = "Enter a number, or 0 to skip this month."
            } else {
              onSaveOnly(num)
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = colors.head,
            contentColor = colors.headInk
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_month_only_btn")
        ) {
          Text("Save for ${ScheduleEngine.mLabel(month)} only", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
          onClick = {
            val num = ArabicNumberHelper.parseNumber(inputVal)
            if (num == null || num < 0) {
              errorMsg = "Enter a number, or 0 to skip this month."
            } else {
              onSaveFrom(num)
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_month_and_after_btn")
        ) {
          Text("Save for ${ScheduleEngine.mLabel(month)} and after", color = colors.ink)
        }

        if (hasOverride) {
          OutlinedButton(
            onClick = onReset,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reset_planned_btn")
          ) {
            Text("Go back to the planned amount", color = colors.ink)
          }
        }

        OutlinedButton(
          onClick = onEditWholeRow,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_whole_row_btn")
        ) {
          Text("Edit the whole row", color = colors.ink)
        }
      }
    }
  }
}
