package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.example.ui.components.AppPrimaryButton
import com.example.ui.components.AppSecondaryButton
import com.example.ui.components.AppTextButton
import com.example.ui.theme.AppShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.ScheduleEngine
import kotlinx.coroutines.launch

sealed class GridColumn {
  data class Month(val month: String) : GridColumn()
  data class Year(val year: Int, val upto: String, val monthsInYear: List<String>) : GridColumn()
}

@Composable
fun HomeScreen(
  rows: List<BudgetRow>,
  settings: AppSettings,
  onAddIncome: () -> Unit,
  onAddExpense: () -> Unit,
  onEditRow: (String) -> Unit,
  onEditCell: (String, String) -> Unit,
  onExportMariam: () -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val scope = rememberCoroutineScope()
  val rangeStart = settings.startMonth
  val rangeEnd = settings.endMonth

  val allMonths = remember(rangeStart, rangeEnd) {
    ScheduleEngine.mRange(rangeStart, rangeEnd)
  }

  val columns = remember(allMonths) {
    val cols = mutableListOf<GridColumn>()
    allMonths.forEachIndexed { i, m ->
      cols.add(GridColumn.Month(m))
      val next = allMonths.getOrNull(i + 1)
      if (next == null || ScheduleEngine.yearOf(next) != ScheduleEngine.yearOf(m)) {
        val y = ScheduleEngine.yearOf(m)
        cols.add(GridColumn.Year(y, m, allMonths.filter { ScheduleEngine.yearOf(it) == y }))
      }
    }
    cols
  }

  val inRows = remember(rows) {
    rows.filter { it.kind == "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }
  val outRows = remember(rows) {
    rows.filter { it.kind != "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }

  val currentM = remember { ScheduleEngine.currentMonth() }

  // Monthly totals
  val incByMonth: (String) -> Double = remember(inRows, rangeStart, rangeEnd) {
    { m -> inRows.sumOf { ScheduleEngine.amt(it, m, rangeStart, rangeEnd) } }
  }
  val expByMonth: (String) -> Double = remember(outRows, rangeStart, rangeEnd) {
    { m -> outRows.sumOf { ScheduleEngine.amt(it, m, rangeStart, rangeEnd) } }
  }
  val leftByMonth: (String) -> Double = remember(incByMonth, expByMonth) {
    { m -> incByMonth(m) - expByMonth(m) }
  }

  // Unified horizontal scroll state shared across ALL rows for synchronized horizontal scrolling
  val sharedHorizontalScrollState = rememberScrollState()

  // Auto-scroll near current month on first load
  LaunchedEffect(currentM, columns) {
    val curIndex = columns.indexOfFirst { it is GridColumn.Month && it.month == currentM }
    if (curIndex > 0) {
      val targetScroll = (curIndex * 90 - 80).coerceAtLeast(0)
      sharedHorizontalScrollState.animateScrollTo(targetScroll)
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 4.dp, vertical = 2.dp)
  ) {
    val isWideScreen = maxWidth >= 800.dp
    val isDesktopDeX = maxWidth >= 1050.dp

    val stickyWidth = if (isDesktopDeX) 230.dp else if (isWideScreen) 195.dp else 160.dp
    val monthColWidth = if (isDesktopDeX) 100.dp else if (isWideScreen) 90.dp else 84.dp
    val yearColWidth = if (isDesktopDeX) 124.dp else if (isWideScreen) 112.dp else 105.dp

    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      if (isWideScreen) {
        // Desktop / Samsung DeX Action & Pan Controls
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Left: 3 Action Buttons
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AppPrimaryButton(
              onClick = onExportMariam,
              text = "Export to Mariam",
              icon = Icons.Default.Share,
              modifier = Modifier.testTag("export_to_mariam_btn")
            )

            AppSecondaryButton(
              onClick = onAddIncome,
              text = "Add Income",
              icon = Icons.Default.Add,
              contentColor = colors.income,
              borderColor = colors.income.copy(alpha = 0.5f),
              modifier = Modifier.testTag("add_income_btn")
            )

            AppSecondaryButton(
              onClick = onAddExpense,
              text = "Add Expense",
              icon = Icons.Default.Add,
              contentColor = colors.expense,
              borderColor = colors.expense.copy(alpha = 0.5f),
              modifier = Modifier.testTag("add_expense_btn")
            )
          }

          // Right: Desktop Timeline Navigation & Pan Controls
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AppSecondaryButton(
              onClick = {
                scope.launch {
                  val step = (monthColWidth.value * 12).toInt()
                  val target = (sharedHorizontalScrollState.value - step).coerceAtLeast(0)
                  sharedHorizontalScrollState.animateScrollTo(target)
                }
              },
              icon = Icons.Default.ChevronLeft,
              text = "Prev Year"
            )

            AppSecondaryButton(
              onClick = {
                scope.launch {
                  val curIndex = columns.indexOfFirst { it is GridColumn.Month && it.month == currentM }
                  if (curIndex >= 0) {
                    val target = (curIndex * monthColWidth.value - 80).toInt().coerceAtLeast(0)
                    sharedHorizontalScrollState.animateScrollTo(target)
                  }
                }
              },
              icon = Icons.Default.Today,
              text = "Today (${ScheduleEngine.mLabel(currentM)})",
              containerColor = colors.head.copy(alpha = 0.15f),
              contentColor = colors.head,
              borderColor = colors.head.copy(alpha = 0.4f)
            )

            AppSecondaryButton(
              onClick = {
                scope.launch {
                  val step = (monthColWidth.value * 12).toInt()
                  val target = (sharedHorizontalScrollState.value + step).coerceAtMost(sharedHorizontalScrollState.maxValue)
                  sharedHorizontalScrollState.animateScrollTo(target)
                }
              },
              text = "Next Year"
            )
          }
        }
      } else {
        // Phone (Samsung Galaxy S25 Ultra) Top 3 Buttons
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          AppPrimaryButton(
            onClick = onExportMariam,
            text = "Export",
            icon = Icons.Default.Share,
            modifier = Modifier
              .weight(1f)
              .testTag("export_to_mariam_btn")
          )

          AppSecondaryButton(
            onClick = onAddIncome,
            text = "Income",
            icon = Icons.Default.Add,
            contentColor = colors.income,
            borderColor = colors.income.copy(alpha = 0.5f),
            modifier = Modifier
              .weight(1f)
              .testTag("add_income_btn")
          )

          AppSecondaryButton(
            onClick = onAddExpense,
            text = "Expense",
            icon = Icons.Default.Add,
            contentColor = colors.expense,
            borderColor = colors.expense.copy(alpha = 0.5f),
            modifier = Modifier
              .weight(1f)
              .testTag("add_expense_btn")
          )
        }
      }

    // Unified Full-Screen Scrollable Table: Single LazyColumn for vertical scroll, synchronized horizontal scroll
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .border(1.dp, colors.line, com.example.ui.theme.AppShapes.Medium)
        .clip(com.example.ui.theme.AppShapes.Medium)
        .background(colors.surface)
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize()
      ) {
        // Row 1: Income Totals
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp)
          ) {
            // Sticky Left Header
            Box(
              modifier = Modifier
                .width(stickyWidth)
                .fillMaxHeight()
                .background(colors.incomeSoft)
                .border(0.5.dp, colors.line)
                .padding(horizontal = 8.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Text("Income", color = colors.income, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Horizontally scrolled month columns
            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(sharedHorizontalScrollState)
            ) {
              Row {
                columns.forEach { col ->
                  val isYear = col is GridColumn.Year
                  val v = when (col) {
                    is GridColumn.Month -> incByMonth(col.month)
                    is GridColumn.Year -> col.monthsInYear.sumOf { incByMonth(it) }
                  }
                  GridCellBox(
                    width = if (isYear) yearColWidth else monthColWidth,
                    text = ArabicNumberHelper.fmt(v),
                    bgColor = if (isYear) colors.yearSoft else colors.incomeSoft,
                    fgColor = if (isYear) colors.year else colors.income,
                    isBold = true
                  )
                }
              }
            }
          }
        }

        // Row 2: Month / Year Headers
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp)
          ) {
            Row(
              modifier = Modifier
                .width(stickyWidth)
                .fillMaxHeight()
                .background(colors.head)
                .border(0.5.dp, colors.line)
                .padding(horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Item", color = colors.headInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
              Text("Day", color = colors.headInk, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(sharedHorizontalScrollState)
            ) {
              Row {
                columns.forEach { col ->
                  val isYear = col is GridColumn.Year
                  val label = when (col) {
                    is GridColumn.Month -> ScheduleEngine.mLabel(col.month)
                    is GridColumn.Year -> "End of ${col.year}"
                  }
                  val isCurrent = col is GridColumn.Month && col.month == currentM
                  Box(
                    modifier = Modifier
                      .width(if (isYear) yearColWidth else monthColWidth)
                      .fillMaxHeight()
                      .background(if (isYear) colors.year else colors.head)
                      .border(0.5.dp, colors.line)
                      .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = label,
                      color = Color.White,
                      fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                      fontSize = 12.sp,
                      textAlign = TextAlign.Center
                    )
                  }
                }
              }
            }
          }
        }

        // Row 3: Left Over Totals
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp)
          ) {
            Box(
              modifier = Modifier
                .width(stickyWidth)
                .fillMaxHeight()
                .background(colors.leftoverSoft)
                .border(0.5.dp, colors.line)
                .padding(horizontal = 8.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Text("Left over", color = colors.leftover, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(sharedHorizontalScrollState)
            ) {
              Row {
                columns.forEach { col ->
                  val isYear = col is GridColumn.Year
                  val v = when (col) {
                    is GridColumn.Month -> leftByMonth(col.month)
                    is GridColumn.Year -> col.monthsInYear.sumOf { leftByMonth(it) }
                  }
                  GridCellBox(
                    width = if (isYear) yearColWidth else monthColWidth,
                    text = ArabicNumberHelper.fmt(v),
                    bgColor = if (isYear) colors.yearSoft else colors.leftoverSoft,
                    fgColor = if (v < 0) colors.expense else if (isYear) colors.year else colors.leftover,
                    isBold = true
                  )
                }
              }
            }
          }
        }

        // Row 4: Expenses Totals
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(36.dp)
          ) {
            Box(
              modifier = Modifier
                .width(stickyWidth)
                .fillMaxHeight()
                .background(colors.expenseSoft)
                .border(0.5.dp, colors.line)
                .padding(horizontal = 8.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              Text("Expenses", color = colors.expense, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(sharedHorizontalScrollState)
            ) {
              Row {
                columns.forEach { col ->
                  val isYear = col is GridColumn.Year
                  val v = when (col) {
                    is GridColumn.Month -> expByMonth(col.month)
                    is GridColumn.Year -> col.monthsInYear.sumOf { expByMonth(it) }
                  }
                  GridCellBox(
                    width = if (isYear) yearColWidth else monthColWidth,
                    text = ArabicNumberHelper.fmt(v),
                    bgColor = if (isYear) colors.yearSoft else colors.expenseSoft,
                    fgColor = if (isYear) colors.year else colors.expense,
                    isBold = true
                  )
                }
              }
            }
          }
        }

        // Section: Money In
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(32.dp)
              .background(colors.surface2)
              .border(0.5.dp, colors.line)
              .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Money in", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 13.sp)
          }
        }

        // Money In Rows
        items(inRows, key = { it.id }) { row ->
          UnifiedGridRow(
            row = row,
            stickyWidth = stickyWidth,
            columns = columns,
            monthColWidth = monthColWidth,
            yearColWidth = yearColWidth,
            currentMonth = currentM,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            horizontalScrollState = sharedHorizontalScrollState,
            onEditRow = onEditRow,
            onEditCell = onEditCell
          )
        }

        // Section: Money Out
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(32.dp)
              .background(colors.surface2)
              .border(0.5.dp, colors.line)
              .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Money out", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 13.sp)
          }
        }

        // Money Out Rows
        items(outRows, key = { it.id }) { row ->
          UnifiedGridRow(
            row = row,
            stickyWidth = stickyWidth,
            columns = columns,
            monthColWidth = monthColWidth,
            yearColWidth = yearColWidth,
            currentMonth = currentM,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            horizontalScrollState = sharedHorizontalScrollState,
            onEditRow = onEditRow,
            onEditCell = onEditCell
          )
        }
      }
    }

    // Bottom caption
    Text(
      text = "The End of year column shows year totals and remaining balances. Tap any row name to edit, or tap any amount to change one month.",
      color = colors.muted,
      fontSize = 11.5.sp,
      lineHeight = 15.sp,
      modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
  }
}
}

@Composable
fun UnifiedGridRow(
  row: BudgetRow,
  stickyWidth: androidx.compose.ui.unit.Dp,
  columns: List<GridColumn>,
  monthColWidth: androidx.compose.ui.unit.Dp = 84.dp,
  yearColWidth: androidx.compose.ui.unit.Dp = 105.dp,
  currentMonth: String,
  rangeStart: String,
  rangeEnd: String,
  horizontalScrollState: androidx.compose.foundation.ScrollState,
  onEditRow: (String) -> Unit,
  onEditCell: (String, String) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val sc = remember(row, rangeStart, rangeEnd) {
    ScheduleEngine.computeSchedule(row, rangeStart, rangeEnd)
  }

  // Always compute clear details under the row name
  val subText = remember(row, sc) {
    if (sc.finite && sc.last != null) {
      "${ArabicNumberHelper.fmt(ScheduleEngine.rateAt(row, rangeStart))} ${ScheduleEngine.everyText(sc.every)}, ends ${ScheduleEngine.mLabel(sc.last)}"
    } else if (sc.finite) {
      "Finished"
    } else if (sc.every > 1) {
      "${ArabicNumberHelper.fmt(ScheduleEngine.rateAt(row, rangeStart))} ${ScheduleEngine.everyText(sc.every)}"
    } else {
      "${ArabicNumberHelper.fmt(ScheduleEngine.rateAt(row, rangeStart))} a month"
    }
  }

  // Check if cell background is white (e.g. Light theme surface is #FFFFFF)
  val isWhiteCell = colors.surface == Color.White || colors.surface == Color(0xFFFFFFFF)
  val cellNameColor = if (isWhiteCell) Color.Black else colors.ink
  val cellSubColor = if (isWhiteCell) Color(0xFF222222) else colors.muted
  val cellDayColor = if (isWhiteCell) Color.Black else colors.muted

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(60.dp)
  ) {
    // Left Sticky Column Cell
    Row(
      modifier = Modifier
        .width(stickyWidth)
        .fillMaxHeight()
        .background(colors.surface)
        .border(0.5.dp, colors.line)
        .clickable { onEditRow(row.id) }
        .padding(start = 6.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .width(3.5.dp)
          .fillMaxHeight(0.75f)
          .clip(RoundedCornerShape(2.dp))
          .background(if (row.kind == "in") colors.income else colors.expense)
      )

      Spacer(modifier = Modifier.width(6.dp))

      Column(
        modifier = Modifier
          .weight(1f)
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = row.name,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = cellNameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          if (row.isLoan) {
            Box(
              modifier = Modifier
                .padding(start = 3.dp)
                .clip(CircleShape)
                .background(colors.yearSoft)
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
              Text("Loan", color = colors.year, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
        if (subText.isNotBlank()) {
          Text(
            text = subText,
            fontSize = 10.5.sp,
            lineHeight = 13.5.sp,
            color = cellSubColor,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Text(
        text = row.day?.toString() ?: "",
        color = cellDayColor,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier.width(26.dp),
        textAlign = TextAlign.Center
      )
    }

    // Horizontally Scrolled Cells synchronized with header
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .horizontalScroll(horizontalScrollState)
    ) {
      Row {
        columns.forEach { col ->
          val isYear = col is GridColumn.Year
          val cellWidth = if (isYear) yearColWidth else monthColWidth

          when (col) {
            is GridColumn.Year -> {
              val rem = remember(row, col) {
                ScheduleEngine.remainingAfter(row, col.upto, rangeStart, rangeEnd)
              }
              Box(
                modifier = Modifier
                  .width(cellWidth)
                  .fillMaxHeight()
                  .background(colors.yearSoft)
                  .border(0.5.dp, colors.line)
                  .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterEnd
              ) {
                if (rem != null) {
                  Text(
                    text = ArabicNumberHelper.fmt(rem),
                    color = colors.year,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                  )
                }
              }
            }
            is GridColumn.Month -> {
              val m = col.month
              val v = remember(row, m) { ScheduleEngine.amt(row, m, rangeStart, rangeEnd) }
              val isOv = remember(row, m) { row.getOverrides().containsKey(m) }
              val isCurrent = m == currentMonth
              val cellBg = if (isCurrent) colors.leftoverSoft.copy(alpha = 0.35f) else colors.surface
              val cellTextCol = if (isOv) colors.leftover else if (isWhiteCell && !isCurrent) Color.Black else colors.ink

              Box(
                modifier = Modifier
                  .width(cellWidth)
                  .fillMaxHeight()
                  .background(cellBg)
                  .border(0.5.dp, colors.line)
                  .clickable { onEditCell(row.id, m) }
                  .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterEnd
              ) {
                if (v > 0) {
                  Text(
                    text = ArabicNumberHelper.fmt(v),
                    color = cellTextCol,
                    fontWeight = if (isOv) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    textDecoration = if (isOv) TextDecoration.Underline else TextDecoration.None
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun GridCellBox(
  width: androidx.compose.ui.unit.Dp,
  text: String,
  bgColor: Color,
  fgColor: Color,
  isBold: Boolean = false
) {
  val colors = LocalMoneyMapColors.current
  Box(
    modifier = Modifier
      .width(width)
      .fillMaxHeight()
      .background(bgColor)
      .border(0.5.dp, colors.line)
      .padding(horizontal = 6.dp),
    contentAlignment = Alignment.CenterEnd
  ) {
    Text(
      text = text,
      color = fgColor,
      fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
      fontSize = 12.sp
    )
  }
}
