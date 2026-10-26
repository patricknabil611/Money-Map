package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.ScheduleEngine

@Composable
fun LoansScreen(
  rows: List<BudgetRow>,
  settings: AppSettings,
  openLoanIds: Set<String>,
  onToggleExpanded: (String) -> Unit,
  onTogglePaid: (String, String, Boolean) -> Unit,
  onAddLoan: () -> Unit,
  onEditLoan: (String) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val rangeStart = settings.startMonth
  val rangeEnd = settings.endMonth

  val loanRows = remember(rows) {
    rows.filter { it.isLoan }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
  }

  val loanInfos = remember(loanRows, rangeStart, rangeEnd) {
    loanRows.map { it to ScheduleEngine.loanInfo(it, rangeStart, rangeEnd) }
  }

  val totalAmount = remember(loanInfos) { loanInfos.sumOf { it.second.total } }
  val totalRemaining = remember(loanInfos) { loanInfos.sumOf { it.second.remaining } }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 14.dp, vertical = 6.dp)
  ) {
    // Top banner
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        Column {
          Text("Total amount", color = colors.muted, fontSize = 13.sp)
          Text(
            ArabicNumberHelper.fmt(totalAmount),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            lineHeight = 42.sp
          )
        }

        Button(
          onClick = onAddLoan,
          colors = ButtonDefaults.buttonColors(
            containerColor = colors.head,
            contentColor = colors.headInk
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.testTag("add_loan_btn")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Add loan", fontWeight = FontWeight.SemiBold)
        }
      }
    }

    if (loanRows.isEmpty()) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = colors.surface),
          border = CardDefaults.outlinedCardBorder()
        ) {
          Text(
            "No loans yet. Add one here, or tick “Show in Loans” on any expense.",
            color = colors.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(20.dp)
          )
        }
      }
    } else {
      items(loanInfos, key = { it.first.id }) { (row, info) ->
        val isOpen = openLoanIds.contains(row.id)
        val rotationAngle by animateFloatAsState(targetValue = if (isOpen) 90f else 0f, label = "caret")
        val pct = if (info.total > 0) ((info.paid / info.total) * 100).coerceIn(0.0, 100.0) else 0.0

        val sc = info.sc
        val rate = if (sc.list.isNotEmpty()) sc.map[sc.list[0]] ?: 0.0 else 0.0
        val sub = if (sc.list.isNotEmpty()) {
          "${ArabicNumberHelper.fmt(rate)} ${ScheduleEngine.everyText(sc.every)}, ${ScheduleEngine.mLabel(sc.first ?: "")} to ${ScheduleEngine.mLabel(sc.last ?: "")}"
        } else {
          "No payments scheduled"
        }

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = colors.surface),
          border = CardDefaults.outlinedCardBorder()
        ) {
          Column {
            // Loan Header Clickable
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded(row.id) }
                .padding(16.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier
                      .size(20.dp)
                      .rotate(rotationAngle)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = row.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.ink
                  )
                }

                Text(
                  text = ArabicNumberHelper.fmt(info.remaining),
                  fontWeight = FontWeight.Bold,
                  fontSize = 18.sp,
                  color = colors.ink
                )
              }

              Spacer(modifier = Modifier.height(4.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(sub, color = colors.muted, fontSize = 12.5.sp)
                Text("left of ${ArabicNumberHelper.fmt(info.total)}", color = colors.muted, fontSize = 12.5.sp)
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Progress bar
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(CircleShape)
                  .background(colors.surface2)
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth((pct / 100f).toFloat())
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(colors.income)
                )
              }
            }

            // Loan Body Accordion
            AnimatedVisibility(visible = isOpen) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(colors.surface2.copy(alpha = 0.5f))
                  .padding(horizontal = 16.dp, vertical = 10.dp)
              ) {
                val paidMap = row.getPaid()
                val currentM = ScheduleEngine.currentMonth()

                sc.list.forEach { m ->
                  val isPaid = paidMap[m] == true
                  val monthAmt = sc.map[m] ?: 0.0

                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { onTogglePaid(row.id, m, !isPaid) }
                      .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Checkbox(
                      checked = isPaid,
                      onCheckedChange = { onTogglePaid(row.id, m, it) },
                      colors = CheckboxDefaults.colors(checkedColor = colors.income)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                      text = "${ScheduleEngine.mLabel(m)}${if (m == currentM) " (this month)" else ""}",
                      fontSize = 14.sp,
                      color = if (isPaid) colors.muted else colors.ink,
                      textDecoration = if (isPaid) TextDecoration.LineThrough else TextDecoration.None,
                      modifier = Modifier.weight(1f)
                    )

                    Text(
                      text = ArabicNumberHelper.fmt(monthAmt),
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 14.sp,
                      color = if (isPaid) colors.muted else colors.ink,
                      textDecoration = if (isPaid) TextDecoration.LineThrough else TextDecoration.None
                    )
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Paid so far: ${ArabicNumberHelper.fmt(info.paid)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = colors.ink
                  )

                  OutlinedButton(
                    onClick = { onEditLoan(row.id) },
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text("Edit loan", fontSize = 12.5.sp, color = colors.ink)
                  }
                }
              }
            }
          }
        }
      }

      // Total Remaining Footer Card
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = colors.head)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Total remaining", color = colors.headInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
              ArabicNumberHelper.fmt(totalRemaining),
              color = colors.headInk,
              fontSize = 26.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      item {
        Text(
          "Loans also show up as expenses on Home and in the Month view.",
          color = colors.muted,
          fontSize = 12.5.sp,
          modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 24.dp)
        )
      }
    }
  }
}
