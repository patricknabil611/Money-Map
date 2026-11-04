package com.example.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSecondaryButton
import com.example.ui.components.AppIconButton
import com.example.ui.theme.AppShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.BudgetRow
import com.example.data.model.Step
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.ScheduleEngine
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowEditorSheet(
  row: BudgetRow,
  isNew: Boolean,
  rangeStart: String,
  rangeEnd: String,
  onDismiss: () -> Unit,
  onSave: (BudgetRow) -> Unit,
  onDelete: (String) -> Unit,
  onMoveUp: (String) -> Unit,
  onMoveDown: (String) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var kind by remember { mutableStateOf(row.kind) }
  var name by remember { mutableStateOf(row.name) }
  var amountStr by remember { mutableStateOf(if (row.amount > 0) row.amount.toString().removeSuffix(".0") else "") }
  var dayStr by remember { mutableStateOf(row.day?.toString() ?: "") }
  var startMonth by remember { mutableStateOf(if (ScheduleEngine.validM(row.start)) row.start else rangeStart) }
  var every by remember { mutableStateOf(row.every) }
  var monthsStr by remember { mutableStateOf(row.months?.toString() ?: "") }
  var totalStr by remember { mutableStateOf(row.total?.toString()?.removeSuffix(".0") ?: "") }
  var isLoan by remember { mutableStateOf(row.isLoan) }
  var steps by remember { mutableStateOf(row.getSteps()) }

  var errorMessage by remember { mutableStateOf<String?>(null) }
  var deleteArmed by remember { mutableStateOf(false) }

  LaunchedEffect(deleteArmed) {
    if (deleteArmed) {
      delay(3500)
      deleteArmed = false
    }
  }

  // Calculate live plan preview
  val tempRow = remember(kind, name, amountStr, dayStr, startMonth, every, monthsStr, totalStr, steps, isLoan) {
    val a = ArabicNumberHelper.parseNumber(amountStr) ?: 0.0
    val d = ArabicNumberHelper.parseInt(dayStr)
    val m = ArabicNumberHelper.parseInt(monthsStr)
    val t = ArabicNumberHelper.parseNumber(totalStr)
    row.copy(
      kind = kind,
      name = name,
      amount = a,
      day = d,
      start = startMonth,
      every = every,
      months = m,
      total = t,
      isLoan = isLoan,
      stepsJson = BudgetRow.stepsToJson(steps)
    )
  }

  val planText = remember(tempRow) {
    val sc = ScheduleEngine.computeSchedule(tempRow, rangeStart, rangeEnd)
    if (sc.finite) {
      if (sc.list.isEmpty()) {
        "No payments scheduled."
      } else {
        val s = if (sc.list.size > 1) "s" else ""
        "${sc.list.size} payment$s from ${ScheduleEngine.mLabel(sc.first ?: "")} to ${ScheduleEngine.mLabel(sc.last ?: "")}, ${ArabicNumberHelper.fmt(sc.planned)} in all."
      }
    } else {
      val st = if (ScheduleEngine.validM(tempRow.start)) tempRow.start else rangeStart
      "Ongoing from ${ScheduleEngine.mLabel(st)}, ${ArabicNumberHelper.fmt(ScheduleEngine.rateAt(tempRow, st))} ${ScheduleEngine.everyText(sc.every)}."
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
        .verticalScroll(rememberScrollState())
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val title = if (isNew) {
          if (isLoan) "New loan" else if (kind == "in") "New income" else "New expense"
        } else {
          "Edit ${row.name.ifBlank { "row" }}"
        }
        Text(
          text = title,
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
          modifier = Modifier.testTag("close_sheet_btn")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Type Selector Segment
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
            .background(if (kind == "in") colors.surface else Color.Transparent)
            .clickable { kind = "in"; isLoan = false }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Money in",
            fontWeight = FontWeight.SemiBold,
            color = if (kind == "in") colors.ink else colors.muted
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (kind != "in") colors.surface else Color.Transparent)
            .clickable { kind = "out" }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Money out",
            fontWeight = FontWeight.SemiBold,
            color = if (kind != "in") colors.ink else colors.muted
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Name field
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        placeholder = { Text(if (kind == "in") "Salary, Rent…" else "Car installment, Internet…") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("row_name_input"),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Amount & Day row
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = amountStr,
          onValueChange = { amountStr = it },
          label = { Text("Amount each time") },
          placeholder = { Text("0") },
          modifier = Modifier
            .weight(1f)
            .testTag("row_amount_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        OutlinedTextField(
          value = dayStr,
          onValueChange = { dayStr = it },
          label = { Text("Day of month") },
          placeholder = { Text("Any day") },
          modifier = Modifier
            .weight(1f)
            .testTag("row_day_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Starts & Repeats
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthDropdown(
          label = "Starts",
          selected = startMonth,
          onSelect = { startMonth = it },
          modifier = Modifier.weight(1f)
        )

        EveryDropdown(
          selected = every,
          onSelect = { every = it },
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Number of payments & Total
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = monthsStr,
          onValueChange = { monthsStr = it },
          label = { Text("Number of payments") },
          placeholder = { Text("Ongoing") },
          modifier = Modifier
            .weight(1f)
            .testTag("row_months_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
          value = totalStr,
          onValueChange = { totalStr = it },
          label = { Text("Total to pay off") },
          placeholder = { Text("No total") },
          modifier = Modifier
            .weight(1f)
            .testTag("row_total_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Live Plan Preview text
      Text(
        text = planText,
        color = colors.leftover,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        modifier = Modifier.padding(vertical = 4.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Amount changes (steps)
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .border(1.dp, colors.line, RoundedCornerShape(12.dp))
          .padding(12.dp)
      ) {
        Text("Amount changes", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 14.sp)
        Text(
          "For a raise or a new rent, add the new amount and the month it starts.",
          color = colors.muted,
          fontSize = 12.5.sp,
          modifier = Modifier.padding(bottom = 8.dp)
        )

        steps.forEachIndexed { i, step ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            MonthDropdown(
              label = "From",
              selected = step.from,
              onSelect = { newFrom ->
                val newSteps = steps.toMutableList()
                newSteps[i] = step.copy(from = newFrom)
                steps = newSteps
              },
              modifier = Modifier.weight(1f)
            )

            var stepAmtStr by remember(step.amount) { mutableStateOf(if (step.amount > 0) step.amount.toString().removeSuffix(".0") else "") }
            OutlinedTextField(
              value = stepAmtStr,
              onValueChange = {
                stepAmtStr = it
                val parsed = ArabicNumberHelper.parseNumber(it) ?: 0.0
                val newSteps = steps.toMutableList()
                newSteps[i] = step.copy(amount = parsed)
                steps = newSteps
              },
              label = { Text("New amount") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            IconButton(
              onClick = {
                val newSteps = steps.toMutableList()
                newSteps.removeAt(i)
                steps = newSteps
              }
            ) {
              Icon(Icons.Default.Close, contentDescription = "Remove change", tint = colors.muted)
            }
          }
        }

        AppSecondaryButton(
          onClick = {
            steps = steps + Step(from = ScheduleEngine.currentMonth(), amount = ArabicNumberHelper.parseNumber(amountStr) ?: 0.0)
          },
          text = "Add a change",
          icon = Icons.Default.Add,
          modifier = Modifier.padding(top = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Show in loans checkbox (if Money out)
      if (kind != "in") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Small)
            .clickable { isLoan = !isLoan }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = isLoan,
            onCheckedChange = { isLoan = it },
            colors = CheckboxDefaults.colors(checkedColor = colors.income)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Show in Loans with a paid checklist", color = colors.ink, fontSize = 14.sp)
        }
      }

      errorMessage?.let { err ->
        Text(
          text = err,
          color = colors.expense,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(vertical = 8.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Reordering row (if editing existing row)
      if (!isNew) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
            .background(colors.surface2.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Reorder position in list",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
            modifier = Modifier.weight(1f)
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AppSecondaryButton(
              onClick = { onMoveUp(row.id) },
              text = "Up",
              icon = Icons.Default.ArrowUpward,
              modifier = Modifier.testTag("move_up_btn")
            )

            AppSecondaryButton(
              onClick = { onMoveDown(row.id) },
              text = "Down",
              icon = Icons.Default.ArrowDownward,
              modifier = Modifier.testTag("move_down_btn")
            )
          }
        }
        Spacer(modifier = Modifier.height(14.dp))
      }

      // Enhanced Action buttons: "Save changes" and "Delete"
      if (isNew) {
        AppPrimaryButton(
          onClick = {
            if (name.isBlank()) {
              errorMessage = "Give this row a name."
              return@AppPrimaryButton
            }
            val a = ArabicNumberHelper.parseNumber(amountStr)
            if (a == null || a < 0) {
              errorMessage = "Enter the amount for each payment."
              return@AppPrimaryButton
            }
            val d = ArabicNumberHelper.parseInt(dayStr)
            if (d != null && (d < 1 || d > 31)) {
              errorMessage = "Day of month should be between 1 and 31."
              return@AppPrimaryButton
            }
            val m = ArabicNumberHelper.parseInt(monthsStr)
            if (m != null && m < 1) {
              errorMessage = "Number of payments should be a positive number, or leave empty for ongoing."
              return@AppPrimaryButton
            }
            val t = ArabicNumberHelper.parseNumber(totalStr)
            if (t != null && t < 0) {
              errorMessage = "Total should be a valid number, or leave empty."
              return@AppPrimaryButton
            }
            if (isLoan && m == null && t == null) {
              errorMessage = "A loan needs a total or a number of payments."
              return@AppPrimaryButton
            }

            val out = tempRow.copy(
              name = name.trim(),
              kind = kind,
              amount = a,
              day = d,
              start = startMonth,
              every = every,
              months = m,
              total = t,
              isLoan = if (kind == "in") false else isLoan,
              stepsJson = BudgetRow.stepsToJson(steps)
            )
            onSave(out)
          },
          text = if (isLoan) "Add Loan" else if (kind == "in") "Add Income" else "Add Expense",
          icon = Icons.Default.Add,
          containerColor = if (kind == "in") colors.income else colors.head,
          contentColor = if (kind == "in") Color.White else colors.headInk,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_row_btn")
        )
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Secondary on start side (Delete)
          AppSecondaryButton(
            onClick = {
              if (deleteArmed) {
                onDelete(row.id)
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
              .testTag("delete_row_btn")
          )

          // Primary on end side (Save)
          AppPrimaryButton(
            onClick = {
              if (name.isBlank()) {
                errorMessage = "Give this row a name."
                return@AppPrimaryButton
              }
              val a = ArabicNumberHelper.parseNumber(amountStr)
              if (a == null || a < 0) {
                errorMessage = "Enter the amount for each payment."
                return@AppPrimaryButton
              }
              val d = ArabicNumberHelper.parseInt(dayStr)
              if (d != null && (d < 1 || d > 31)) {
                errorMessage = "Day of month should be between 1 and 31."
                return@AppPrimaryButton
              }
              val m = ArabicNumberHelper.parseInt(monthsStr)
              if (m != null && m < 1) {
                errorMessage = "Number of payments should be a positive number, or leave empty for ongoing."
                return@AppPrimaryButton
              }
              val t = ArabicNumberHelper.parseNumber(totalStr)
              if (t != null && t < 0) {
                errorMessage = "Total should be a valid number, or leave empty."
                return@AppPrimaryButton
              }
              if (isLoan && m == null && t == null) {
                errorMessage = "A loan needs a total or a number of payments."
                return@AppPrimaryButton
              }

              val out = tempRow.copy(
                name = name.trim(),
                kind = kind,
                amount = a,
                day = d,
                start = startMonth,
                every = every,
                months = m,
                total = t,
                isLoan = if (kind == "in") false else isLoan,
                stepsJson = BudgetRow.stepsToJson(steps)
              )
              onSave(out)
            },
            text = "Save",
            icon = Icons.Default.Check,
            containerColor = colors.head,
            contentColor = colors.headInk,
            modifier = Modifier
              .weight(1f)
              .testTag("save_row_btn")
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthDropdown(
  label: String,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val months = remember { ScheduleEngine.allSelectableMonths() }
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = ScheduleEngine.mLabel(selected),
      onValueChange = {},
      readOnly = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor()
        .fillMaxWidth(),
      singleLine = true
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      months.forEach { m ->
        DropdownMenuItem(
          text = { Text(ScheduleEngine.mLabel(m)) },
          onClick = {
            onSelect(m)
            expanded = false
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EveryDropdown(
  selected: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val options = remember {
    listOf(
      1 to "Every month",
      2 to "Every 2 months",
      3 to "Every 3 months",
      6 to "Every 6 months",
      12 to "Once a year"
    )
  }
  var expanded by remember { mutableStateOf(false) }
  val currentLabel = options.find { it.first == selected }?.second ?: "Every $selected months"

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = currentLabel,
      onValueChange = {},
      readOnly = true,
      label = { Text("Repeats") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor()
        .fillMaxWidth(),
      singleLine = true
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false }
    ) {
      options.forEach { (v, text) ->
        DropdownMenuItem(
          text = { Text(text) },
          onClick = {
            onSelect(v)
            expanded = false
          }
        )
      }
    }
  }
}
