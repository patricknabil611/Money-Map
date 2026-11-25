package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.MonthEntry
import com.example.ui.components.AppIconButton
import com.example.ui.components.MonthDropdown
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.RecordOrder
import com.example.util.ScheduleEngine

@Composable
fun MonthScreen(
  rows: List<BudgetRow>,
  monthEntries: List<MonthEntry>,
  settings: AppSettings,
  selectedMonth: String,
  onMonthChange: (String) -> Unit,
  onEntryClick: (String) -> Unit,
  onAddEntry: (String) -> Unit,
  onToggleEntryPaid: (String, Boolean) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val rangeStart = settings.startMonth
  val rangeEnd = settings.endMonth

  val selectableMonths = remember(rangeStart, rangeEnd, rows, monthEntries) {
    var minM = rangeStart
    var maxM = rangeEnd
    rows.forEach { r ->
      if (ScheduleEngine.validM(r.start) && ScheduleEngine.mIdx(r.start) < ScheduleEngine.mIdx(minM)) {
        minM = r.start
      }
    }
    monthEntries.forEach { me ->
      if (ScheduleEngine.validM(me.month)) {
        if (ScheduleEngine.mIdx(me.month) < ScheduleEngine.mIdx(minM)) minM = me.month
        if (ScheduleEngine.mIdx(me.month) > ScheduleEngine.mIdx(maxM)) maxM = me.month
      }
    }
    val cur = ScheduleEngine.currentMonth()
    if (ScheduleEngine.mIdx(cur) < ScheduleEngine.mIdx(minM)) minM = cur
    if (ScheduleEngine.mIdx(cur) > ScheduleEngine.mIdx(maxM)) maxM = cur
    ScheduleEngine.mRange(minM, maxM)
  }

  val currentIndex = selectableMonths.indexOf(selectedMonth)
  val canPrev = currentIndex > 0
  val canNext = currentIndex >= 0 && currentIndex < selectableMonths.size - 1

  val inItems = remember(monthEntries, selectedMonth) {
    RecordOrder.sort(monthEntries.filter { it.month == selectedMonth && it.kind == "in" && it.amount > 0 })
  }

  val outItems = remember(monthEntries, selectedMonth) {
    RecordOrder.sort(monthEntries.filter { it.month == selectedMonth && it.kind != "in" && it.amount > 0 })
  }

  val incTotal = remember(inItems) {
    inItems.sumOf { it.amount }
  }
  val expTotal = remember(outItems) {
    outItems.sumOf { it.amount }
  }
  val leftover = incTotal - expTotal
  val pctSpent = if (incTotal > 0) ((expTotal / incTotal) * 100).coerceIn(0.0, 100.0) else if (expTotal > 0) 100.0 else 0.0

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 6.dp)
      .testTag("month_screen_list")
  ) {
    // Month picker header
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        AppIconButton(
          onClick = { if (canPrev) onMonthChange(selectableMonths[currentIndex - 1]) },
          enabled = canPrev,
          icon = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Previous month",
          modifier = Modifier.testTag("month_prev_btn")
        )

        MonthDropdown(
          label = "Month",
          selected = selectedMonth,
          onSelect = onMonthChange,
          modifier = Modifier.weight(1f)
        )

        AppIconButton(
          onClick = { if (canNext) onMonthChange(selectableMonths[currentIndex + 1]) },
          enabled = canNext,
          icon = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Next month",
          modifier = Modifier.testTag("month_next_btn")
        )
      }
    }

    // Summary Slip Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = CardDefaults.outlinedCardBorder()
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Left over in ${ScheduleEngine.mLabel(selectedMonth)}",
              color = colors.muted,
              fontSize = 13.5.sp,
              fontWeight = FontWeight.Medium
            )
            Box(
              modifier = Modifier
                .clip(AppShapes.Small)
                .background(if (leftover >= 0) colors.incomeSoft else colors.expenseSoft)
                .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = if (leftover >= 0) "Surplus" else "Deficit",
                color = if (leftover >= 0) colors.income else colors.expense,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = ArabicNumberHelper.fmt(leftover),
              color = if (leftover < 0) colors.expense else colors.leftover,
              fontWeight = FontWeight.Bold,
              fontSize = 36.sp,
              lineHeight = 40.sp,
              maxLines = 1
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "EGP",
              color = colors.muted,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(bottom = 5.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Meter Bar
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(CircleShape)
              .background(colors.incomeSoft)
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth((pctSpent / 100f).toFloat())
                .height(10.dp)
                .clip(CircleShape)
                .background(colors.expense)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Balanced 3-column stats with concise wording that displays properly on Samsung Galaxy S25 Ultra
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Column(
              modifier = Modifier
                .weight(1f)
                .clip(AppShapes.Small)
                .background(colors.surface2.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
              Text("Income", color = colors.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = ArabicNumberHelper.fmt(incTotal),
                color = colors.income,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1
              )
            }

            Column(
              modifier = Modifier
                .weight(1f)
                .clip(AppShapes.Small)
                .background(colors.surface2.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
              Text("Expenses", color = colors.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = ArabicNumberHelper.fmt(expTotal),
                color = colors.expense,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1
              )
            }

            Column(
              modifier = Modifier
                .weight(0.9f)
                .clip(AppShapes.Small)
                .background(colors.surface2.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
              Text("Spent", color = colors.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = if (incTotal > 0) "${pctSpent.toInt()}%" else "–",
                color = if (pctSpent > 100) colors.expense else colors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1
              )
            }
          }
        }
      }
    }

    // Income vs Expenses Bar Chart (Recharts-style native Compose chart)
    item {
      MonthBarChartCard(
        incTotal = incTotal,
        expTotal = expTotal,
        month = selectedMonth
      )
    }

    // Money In Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = CardDefaults.outlinedCardBorder()
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Money in", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(ArabicNumberHelper.fmt(incTotal), color = colors.income, fontWeight = FontWeight.Bold, fontSize = 16.sp)
              AppIconButton(
                onClick = { onAddEntry("in") },
                icon = Icons.Default.Add,
                contentDescription = "Add income to ${ScheduleEngine.mLabel(selectedMonth)}",
                tint = colors.income,
                modifier = Modifier.testTag("month_add_in_btn")
              )
            }
          }

          if (inItems.isEmpty()) {
            Text(
              "No income this month.",
              color = colors.muted,
              fontSize = 14.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp)
            )
          } else {
            inItems.forEachIndexed { idx, entry ->
              if (idx > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line))
              }
              MonthEntryItem(
                entry = entry,
                isIncome = true,
                onEntryClick = onEntryClick,
                onTogglePaid = onToggleEntryPaid
              )
            }
          }
        }
      }
    }

    // Money Out Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 24.dp),
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = CardDefaults.outlinedCardBorder()
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Money out", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(ArabicNumberHelper.fmt(expTotal), color = colors.expense, fontWeight = FontWeight.Bold, fontSize = 16.sp)
              AppIconButton(
                onClick = { onAddEntry("out") },
                icon = Icons.Default.Add,
                contentDescription = "Add expense to ${ScheduleEngine.mLabel(selectedMonth)}",
                tint = colors.expense,
                modifier = Modifier.testTag("month_add_out_btn")
              )
            }
          }

          if (outItems.isEmpty()) {
            Text(
              "Nothing to pay this month.",
              color = colors.muted,
              fontSize = 14.sp,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp)
            )
          } else {
            outItems.forEachIndexed { idx, entry ->
              if (idx > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line))
              }
              MonthEntryItem(
                entry = entry,
                isIncome = false,
                onEntryClick = onEntryClick,
                onTogglePaid = onToggleEntryPaid
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MonthEntryItem(
  entry: MonthEntry,
  isIncome: Boolean,
  onEntryClick: (String) -> Unit,
  onTogglePaid: (String, Boolean) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val createdDate = remember(entry.createdAt) { RecordOrder.formatCreatedDate(entry.createdAt) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onEntryClick(entry.id) }
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag("month_entry_${entry.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Day badge (kept exactly as before)
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(AppShapes.Medium)
        .background(colors.surface2),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = entry.day?.toString() ?: "–",
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = colors.muted
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    // Title, created date (for non-legacy items), and pay info
    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = entry.name,
          fontWeight = FontWeight.Medium,
          fontSize = 14.sp,
          color = colors.ink,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        if (entry.isLoan) {
          Box(
            modifier = Modifier
              .padding(start = 4.dp)
              .clip(CircleShape)
              .background(colors.yearSoft)
              .padding(horizontal = 5.dp, vertical = 1.dp)
          ) {
            Text("Loan", color = colors.year, fontSize = 9.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
      if (createdDate != null) {
        Text(
          text = createdDate,
          color = colors.muted,
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("month_entry_date_${entry.id}")
        )
      }
      if (entry.payInfoText.isNotBlank()) {
        Text(
          text = entry.payInfoText,
          color = colors.muted,
          fontSize = 12.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    // Amount
    Text(
      text = ArabicNumberHelper.fmt(entry.amount),
      fontWeight = FontWeight.Bold,
      fontSize = 15.sp,
      color = if (isIncome) colors.income else colors.ink
    )

    // Paid checkbox if isLoan
    if (entry.isLoan) {
      Spacer(modifier = Modifier.width(8.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onTogglePaid(entry.id, !entry.isPaid) }
      ) {
        Checkbox(
          checked = entry.isPaid,
          onCheckedChange = { onTogglePaid(entry.id, it) },
          colors = CheckboxDefaults.colors(checkedColor = colors.income)
        )
        Text("Paid", fontSize = 12.5.sp, color = colors.muted)
      }
    }
  }
}

@Composable
fun MonthBarChartCard(
  incTotal: Double,
  expTotal: Double,
  month: String
) {
  val colors = LocalMoneyMapColors.current
  val maxVal = maxOf(incTotal, expTotal, 1.0)
  val incPct = (incTotal / maxVal).toFloat().coerceIn(0.04f, 1f)
  val expPct = (expTotal / maxVal).toFloat().coerceIn(0.04f, 1f)
  val diff = incTotal - expTotal

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 16.dp),
    shape = AppShapes.Card,
    colors = CardDefaults.cardColors(containerColor = colors.surface),
    border = CardDefaults.outlinedCardBorder()
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Income vs Expenses",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
          )
          Text(
            text = "Comparison for ${ScheduleEngine.mLabel(month)}",
            fontSize = 12.5.sp,
            color = colors.muted
          )
        }

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.income)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("In", fontSize = 11.5.sp, color = colors.muted, fontWeight = FontWeight.Medium)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.expense)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Out", fontSize = 11.5.sp, color = colors.muted, fontWeight = FontWeight.Medium)
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Chart Area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(160.dp)
          .background(colors.surface2.copy(alpha = 0.5f), AppShapes.Medium)
          .padding(horizontal = 24.dp, vertical = 12.dp)
      ) {
        // Grid reference lines
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line.copy(alpha = 0.5f)))
          Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line.copy(alpha = 0.5f)))
          Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.line.copy(alpha = 0.5f)))
        }

        // The Two Comparison Bars (Recharts style)
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.Bottom
        ) {
          // Income Bar Column
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
          ) {
            Text(
              text = ArabicNumberHelper.fmt(incTotal),
              color = colors.income,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
              modifier = Modifier
                .width(56.dp)
                .fillMaxHeight(incPct)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(colors.income)
            )
            Text(
              text = "Income",
              color = colors.ink,
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp,
              modifier = Modifier.padding(top = 6.dp)
            )
          }

          // Expenses Bar Column
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
          ) {
            Text(
              text = ArabicNumberHelper.fmt(expTotal),
              color = colors.expense,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
              modifier = Modifier
                .width(56.dp)
                .fillMaxHeight(expPct)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(colors.expense)
            )
            Text(
              text = "Expenses",
              color = colors.ink,
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp,
              modifier = Modifier.padding(top = 6.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Bottom Comparison summary badge
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(AppShapes.Small)
          .background(if (diff >= 0) colors.incomeSoft else colors.expenseSoft)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        val sign = if (diff >= 0) "+" else "−"
        val label = if (diff >= 0) "Net Savings" else "Monthly Deficit"
        Text(
          text = "$label: $sign${ArabicNumberHelper.fmt(kotlin.math.abs(diff))} EGP",
          color = if (diff >= 0) colors.income else colors.expense,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp
        )
      }
    }
  }
}
