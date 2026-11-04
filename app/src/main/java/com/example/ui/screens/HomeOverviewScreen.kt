package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.ui.AppTab
import com.example.ui.components.AppIconButton
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSecondaryButton
import com.example.ui.components.MonthDropdown
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.ScheduleEngine
import kotlin.math.abs

@Composable
fun HomeOverviewScreen(
  rows: List<BudgetRow>,
  settings: AppSettings,
  selectedMonth: String,
  onMonthChange: (String) -> Unit,
  onAddIncome: () -> Unit,
  onAddExpense: () -> Unit,
  onEditRow: (String) -> Unit,
  onEditCell: (String, String) -> Unit,
  onNavigateToTab: (AppTab) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val rangeStart = settings.startMonth
  val rangeEnd = settings.endMonth
  val currentSystemMonth = remember { ScheduleEngine.currentMonth() }

  val selectableMonths = remember(rangeStart, rangeEnd, rows) {
    var minM = rangeStart
    var maxM = rangeEnd
    rows.forEach { r ->
      if (ScheduleEngine.validM(r.start) && ScheduleEngine.mIdx(r.start) < ScheduleEngine.mIdx(minM)) {
        minM = r.start
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

  // 1. Income Items and Total Income
  val allInRows = remember(rows) {
    rows.filter { it.kind == "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }

  val totalIncome = remember(allInRows, selectedMonth, rangeStart, rangeEnd) {
    allInRows.sumOf { ScheduleEngine.amt(it, selectedMonth, rangeStart, rangeEnd) }
  }

  // 2. Expense Items and Total Expenses
  val allOutRows = remember(rows) {
    rows.filter { it.kind != "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }

  val totalExpenses = remember(allOutRows, selectedMonth, rangeStart, rangeEnd) {
    allOutRows.sumOf { ScheduleEngine.amt(it, selectedMonth, rangeStart, rangeEnd) }
  }

  // 3. The Difference (Income - Expenses)
  val difference = totalIncome - totalExpenses
  val savingsRate = if (totalIncome > 0) {
    ((difference / totalIncome) * 100).coerceIn(-999.0, 100.0)
  } else 0.0

  // 4. Loans & Total Remaining from Loans
  val loanRows = remember(rows) {
    rows.filter { it.isLoan }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }

  val loanInfos = remember(loanRows, rangeStart, rangeEnd) {
    loanRows.map { it to ScheduleEngine.loanInfo(it, rangeStart, rangeEnd) }
  }

  val totalLoanDebt = remember(loanInfos) { loanInfos.sumOf { it.second.total } }
  val totalPaidLoans = remember(loanInfos) { loanInfos.sumOf { it.second.paid } }
  val totalRemainingLoans = remember(loanInfos) { loanInfos.sumOf { it.second.remaining } }
  val loanPayoffProgress = if (totalLoanDebt > 0) {
    (totalPaidLoans / totalLoanDebt).toFloat().coerceIn(0f, 1f)
  } else 0f

  val configuration = LocalConfiguration.current
  val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  val isWideScreen = configuration.screenWidthDp >= 800 || isLandscape

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg)
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      contentPadding = PaddingValues(bottom = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = if (isWideScreen) 1100.dp else 680.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            // Month Selector Bar (Sleek One UI Pill Container)
            Surface(
              shape = AppShapes.Card,
              color = colors.surface,
              border = BorderStroke(1.dp, colors.line.copy(alpha = 0.55f)),
              shadowElevation = 1.dp,
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                // Placed ABOVE the date drop list
                if (selectedMonth != currentSystemMonth) {
                  AppSecondaryButton(
                    onClick = { onMonthChange(currentSystemMonth) },
                    text = "Current Month (${ScheduleEngine.mLabel(currentSystemMonth)})",
                    icon = Icons.Default.Today,
                    contentColor = colors.leftover,
                    borderColor = colors.leftover.copy(alpha = 0.5f),
                    modifier = Modifier.testTag("home_jump_current_month_btn")
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .clip(AppShapes.Small)
                      .background(colors.income.copy(alpha = 0.12f))
                      .padding(horizontal = 12.dp, vertical = 6.dp)
                  ) {
                    Text(
                      text = "Current Month (${ScheduleEngine.mLabel(currentSystemMonth)})",
                      color = colors.income,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Date drop list with previous and next buttons
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  AppIconButton(
                    onClick = { if (canPrev) onMonthChange(selectableMonths[currentIndex - 1]) },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous month",
                    enabled = canPrev,
                    tint = if (canPrev) colors.ink else colors.muted.copy(alpha = 0.35f),
                    modifier = Modifier.testTag("home_month_prev_btn")
                  )

                  Spacer(modifier = Modifier.width(6.dp))

                  MonthDropdown(
                    label = "Overview Month",
                    selected = selectedMonth,
                    onSelect = onMonthChange,
                    modifier = Modifier
                      .width(200.dp)
                      .testTag("home_month_dropdown")
                  )

                  Spacer(modifier = Modifier.width(6.dp))

                  AppIconButton(
                    onClick = { if (canNext) onMonthChange(selectableMonths[currentIndex + 1]) },
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next month",
                    enabled = canNext,
                    tint = if (canNext) colors.ink else colors.muted.copy(alpha = 0.35f),
                    modifier = Modifier.testTag("home_month_next_btn")
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Executive Financial KPI Cards
            if (isWideScreen) {
              // 4 columns on desktop/DeX
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                MetricCard(
                  title = "TOTAL INCOME",
                  amount = totalIncome,
                  subtext = "${allInRows.count { ScheduleEngine.amt(it, selectedMonth, rangeStart, rangeEnd) > 0 }} active source(s)",
                  accentColor = colors.income,
                  accentBg = colors.incomeSoft,
                  icon = Icons.Default.TrendingUp,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_total_income_card")
                )
                MetricCard(
                  title = "TOTAL EXPENSES",
                  amount = totalExpenses,
                  subtext = "${allOutRows.count { ScheduleEngine.amt(it, selectedMonth, rangeStart, rangeEnd) > 0 }} active items",
                  accentColor = colors.expense,
                  accentBg = colors.expenseSoft,
                  icon = Icons.Default.TrendingDown,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_total_expenses_card")
                )
                DifferenceCard(
                  difference = difference,
                  savingsRate = savingsRate,
                  colors = colors,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_difference_card")
                )
                LoansRemainingCard(
                  totalRemaining = totalRemainingLoans,
                  totalPaid = totalPaidLoans,
                  totalDebt = totalLoanDebt,
                  progress = loanPayoffProgress,
                  colors = colors,
                  onViewLoans = { onNavigateToTab(AppTab.LOANS) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_total_loans_remaining_card")
                )
              }
            } else {
              // 2x2 grid on mobile / Samsung Galaxy S25 Ultra
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                MetricCard(
                  title = "TOTAL INCOME",
                  amount = totalIncome,
                  subtext = "${allInRows.count { ScheduleEngine.amt(it, selectedMonth, rangeStart, rangeEnd) > 0 }} active source(s)",
                  accentColor = colors.income,
                  accentBg = colors.incomeSoft,
                  icon = Icons.Default.TrendingUp,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_total_income_card")
                )
                MetricCard(
                  title = "TOTAL EXPENSES",
                  amount = totalExpenses,
                  subtext = "${allOutRows.count { ScheduleEngine.amt(it, selectedMonth, rangeStart, rangeEnd) > 0 }} active items",
                  accentColor = colors.expense,
                  accentBg = colors.expenseSoft,
                  icon = Icons.Default.TrendingDown,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_total_expenses_card")
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                DifferenceCard(
                  difference = difference,
                  savingsRate = savingsRate,
                  colors = colors,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_difference_card")
                )
                LoansRemainingCard(
                  totalRemaining = totalRemainingLoans,
                  totalPaid = totalPaidLoans,
                  totalDebt = totalLoanDebt,
                  progress = loanPayoffProgress,
                  colors = colors,
                  onViewLoans = { onNavigateToTab(AppTab.LOANS) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("home_total_loans_remaining_card")
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Income Details Section (Requested: Income details (name + amount))
            IncomeDetailsSection(
              incomeRows = allInRows,
              selectedMonth = selectedMonth,
              rangeStart = rangeStart,
              rangeEnd = rangeEnd,
              onAddIncome = onAddIncome,
              onEditRow = onEditRow,
              onEditCell = onEditCell,
              colors = colors
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Expense Details Section
            ExpenseDetailsSection(
              expenseRows = allOutRows,
              selectedMonth = selectedMonth,
              rangeStart = rangeStart,
              rangeEnd = rangeEnd,
              onAddExpense = onAddExpense,
              onEditRow = onEditRow,
              onEditCell = onEditCell,
              colors = colors
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Actions & Summary Footer
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              AppSecondaryButton(
                onClick = onAddIncome,
                text = "Add Income",
                icon = Icons.Default.Add,
                contentColor = colors.income,
                borderColor = colors.income,
                modifier = Modifier
                  .weight(1f)
                  .testTag("home_footer_add_income_btn")
              )

              AppSecondaryButton(
                onClick = onAddExpense,
                text = "Add Expense",
                icon = Icons.Default.Add,
                contentColor = colors.expense,
                borderColor = colors.expense,
                modifier = Modifier
                  .weight(1f)
                  .testTag("home_footer_add_expense_btn")
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricCard(
  title: String,
  amount: Double,
  subtext: String,
  accentColor: Color,
  accentBg: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier
) {
  val colors = LocalMoneyMapColors.current

  Card(
    shape = AppShapes.Card,
    colors = CardDefaults.cardColors(containerColor = colors.surface),
    border = BorderStroke(1.dp, colors.line.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(AppShapes.Small)
          .background(accentBg)
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            letterSpacing = 0.5.sp
          )
          Icon(
            icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(14.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = ArabicNumberHelper.fmt(amount),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = subtext,
        fontSize = 12.sp,
        color = colors.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun DifferenceCard(
  difference: Double,
  savingsRate: Double,
  colors: com.example.ui.theme.MoneyMapColors,
  modifier: Modifier = Modifier
) {
  val isPositive = difference >= 0
  val accentColor = if (isPositive) colors.leftover else colors.expense
  val accentBg = if (isPositive) colors.leftoverSoft else colors.expenseSoft

  Card(
    shape = AppShapes.Card,
    colors = CardDefaults.cardColors(containerColor = colors.surface),
    border = BorderStroke(1.dp, colors.line.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Column {
        Box(
          modifier = Modifier
            .clip(AppShapes.Small)
            .background(accentBg)
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
        ) {
          Text(
            text = if (isPositive) "Surplus" else "Deficit",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
          )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
          text = "THE DIFFERENCE",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = colors.muted,
          letterSpacing = 0.5.sp
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = (if (isPositive && difference > 0) "+" else "") + ArabicNumberHelper.fmt(difference),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = accentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = if (isPositive) "${String.format("%.1f", savingsRate)}% net saved" else "Expenses exceed income",
        fontSize = 12.sp,
        color = colors.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun LoansRemainingCard(
  totalRemaining: Double,
  totalPaid: Double,
  totalDebt: Double,
  progress: Float,
  colors: com.example.ui.theme.MoneyMapColors,
  onViewLoans: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = AppShapes.Card,
    colors = CardDefaults.cardColors(containerColor = colors.surface),
    border = BorderStroke(1.dp, colors.line.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier.clickable { onViewLoans() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(AppShapes.Small)
          .background(colors.yearSoft)
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = "LOANS REMAINING",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.year,
            letterSpacing = 0.5.sp
          )
          Icon(
            Icons.Default.AccountBalance,
            contentDescription = null,
            tint = colors.year,
            modifier = Modifier.size(14.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = ArabicNumberHelper.fmt(totalRemaining),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        color = colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(6.dp))

      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(AppShapes.Small),
        color = colors.income,
        trackColor = colors.line
      )

      Spacer(modifier = Modifier.height(5.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Paid ${ArabicNumberHelper.fmt(totalPaid)}",
          fontSize = 11.5.sp,
          color = colors.muted
        )
        Text(
          text = "${(progress * 100).toInt()}%",
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
          color = colors.income
        )
      }
    }
  }
}

@Composable
private fun IncomeDetailsSection(
  incomeRows: List<BudgetRow>,
  selectedMonth: String,
  rangeStart: String,
  rangeEnd: String,
  onAddIncome: () -> Unit,
  onEditRow: (String) -> Unit,
  onEditCell: (String, String) -> Unit,
  colors: com.example.ui.theme.MoneyMapColors
) {
  Card(
    shape = AppShapes.Card,
    colors = CardDefaults.cardColors(containerColor = colors.surface),
    border = BorderStroke(1.dp, colors.line.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("home_income_details_section")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(colors.incomeSoft),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Payments,
              contentDescription = null,
              tint = colors.income,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Income Details",
              fontSize = 17.5.sp,
              fontWeight = FontWeight.Bold,
              color = colors.ink
            )
            Text(
              text = "Name and amount for ${ScheduleEngine.mLabel(selectedMonth)}",
              fontSize = 12.sp,
              color = colors.muted
            )
          }
        }

        AppSecondaryButton(
          onClick = onAddIncome,
          text = "Add",
          icon = Icons.Default.Add,
          contentColor = colors.income,
          borderColor = colors.income,
          modifier = Modifier.testTag("home_income_details_add_btn")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (incomeRows.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
            .background(colors.surface2)
            .padding(vertical = 24.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "No income sources found",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = colors.muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppPrimaryButton(
              onClick = onAddIncome,
              text = "Add Your First Income",
              containerColor = colors.income,
              contentColor = Color.White
            )
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          incomeRows.forEach { row ->
            val monthAmt = ScheduleEngine.amt(row, selectedMonth, rangeStart, rangeEnd)
            val hasOverride = row.getOverrides().containsKey(selectedMonth)
            val cadenceText = if (row.every == 1) "Monthly" else "Every ${row.every} months"

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.Medium)
                .background(colors.surface2)
                .clickable { onEditCell(row.id, selectedMonth) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("home_income_row_${row.id}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              // Left: Income Name & Cadence
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (monthAmt > 0) colors.income else colors.muted.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = row.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = cadenceText,
                      fontSize = 11.5.sp,
                      color = colors.muted
                    )
                    if (hasOverride) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Box(
                        modifier = Modifier
                          .clip(AppShapes.Small)
                          .background(colors.leftoverSoft)
                          .padding(horizontal = 5.dp, vertical = 1.dp)
                      ) {
                        Text(
                          text = "Override",
                          fontSize = 9.5.sp,
                          color = colors.leftover,
                          fontWeight = FontWeight.SemiBold
                        )
                      }
                    }
                  }
                }
              }

              // Right: Amount and Edit Action
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = ArabicNumberHelper.fmt(monthAmt),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (monthAmt > 0) colors.income else colors.muted
                  )
                  if (monthAmt == 0.0 && row.amount > 0) {
                    Text(
                      text = "Base: ${ArabicNumberHelper.fmt(row.amount)}",
                      fontSize = 10.5.sp,
                      color = colors.muted
                    )
                  }
                }

                Spacer(modifier = Modifier.width(8.dp))

                AppIconButton(
                  onClick = { onEditRow(row.id) },
                  icon = Icons.Default.Edit,
                  contentDescription = "Edit ${row.name}",
                  tint = colors.muted,
                  modifier = Modifier.testTag("home_income_edit_${row.id}")
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ExpenseDetailsSection(
  expenseRows: List<BudgetRow>,
  selectedMonth: String,
  rangeStart: String,
  rangeEnd: String,
  onAddExpense: () -> Unit,
  onEditRow: (String) -> Unit,
  onEditCell: (String, String) -> Unit,
  colors: com.example.ui.theme.MoneyMapColors
) {
  Card(
    shape = AppShapes.Card,
    colors = CardDefaults.cardColors(containerColor = colors.surface),
    border = BorderStroke(1.dp, colors.line.copy(alpha = 0.55f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("home_expense_details_section")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(colors.expenseSoft),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.TrendingDown,
              contentDescription = null,
              tint = colors.expense,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Expense Details",
              fontSize = 17.5.sp,
              fontWeight = FontWeight.Bold,
              color = colors.ink
            )
            Text(
              text = "Name and amount for ${ScheduleEngine.mLabel(selectedMonth)}",
              fontSize = 12.sp,
              color = colors.muted
            )
          }
        }

        AppSecondaryButton(
          onClick = onAddExpense,
          text = "Add",
          icon = Icons.Default.Add,
          contentColor = colors.expense,
          borderColor = colors.expense,
          modifier = Modifier.testTag("home_expense_details_add_btn")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (expenseRows.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Medium)
            .background(colors.surface2)
            .padding(vertical = 24.dp, horizontal = 16.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "No expenses recorded",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = colors.muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppPrimaryButton(
              onClick = onAddExpense,
              text = "Add Your First Expense",
              containerColor = colors.expense,
              contentColor = Color.White
            )
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          expenseRows.forEach { row ->
            val monthAmt = ScheduleEngine.amt(row, selectedMonth, rangeStart, rangeEnd)
            val hasOverride = row.getOverrides().containsKey(selectedMonth)
            val cadenceText = if (row.isLoan) "Loan" else if (row.every == 1) "Monthly" else "Every ${row.every} months"

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.Medium)
                .background(colors.surface2)
                .clickable { onEditCell(row.id, selectedMonth) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("home_expense_row_${row.id}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              // Left: Expense Name & Details
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (monthAmt > 0) colors.expense else colors.muted.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = row.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = cadenceText,
                      fontSize = 11.5.sp,
                      color = colors.muted
                    )
                    if (row.day != null) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "• Day ${row.day}",
                        fontSize = 11.sp,
                        color = colors.muted
                      )
                    }
                    if (hasOverride) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Box(
                        modifier = Modifier
                          .clip(AppShapes.Small)
                          .background(colors.leftoverSoft)
                          .padding(horizontal = 5.dp, vertical = 1.dp)
                      ) {
                        Text(
                          text = "Override",
                          fontSize = 9.5.sp,
                          color = colors.leftover,
                          fontWeight = FontWeight.SemiBold
                        )
                      }
                    }
                  }
                }
              }

              // Right: Amount and Edit Action
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = ArabicNumberHelper.fmt(monthAmt),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (monthAmt > 0) colors.expense else colors.muted
                  )
                  if (monthAmt == 0.0 && row.amount > 0) {
                    Text(
                      text = "Base: ${ArabicNumberHelper.fmt(row.amount)}",
                      fontSize = 10.5.sp,
                      color = colors.muted
                    )
                  }
                }

                Spacer(modifier = Modifier.width(8.dp))

                AppIconButton(
                  onClick = { onEditRow(row.id) },
                  icon = Icons.Default.Edit,
                  contentDescription = "Edit ${row.name}",
                  tint = colors.muted,
                  modifier = Modifier.testTag("home_expense_edit_${row.id}")
                )
              }
            }
          }
        }
      }
    }
  }
}
