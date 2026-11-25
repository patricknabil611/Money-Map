package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.model.BudgetRow
import com.example.data.model.MonthEntry
import com.example.data.model.ProjectEntity
import com.example.data.model.Sha2aItem
import com.example.ui.components.AppPrimaryButton
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.RecordOrder
import com.example.util.ScheduleEngine

enum class LegacySectionType {
  MONTH_IN,
  MONTH_OUT,
  HOME_INCOME,
  HOME_EXPENSE,
  LOANS,
  PROJECT_IN,
  PROJECT_OUT
}

data class LegacyArrangeItem(
  val id: String,
  val name: String,
  val amount: Double,
  val day: Int?,
  val isIncome: Boolean
)

data class LegacyArrangeSection(
  val id: String,
  val title: String,
  val type: LegacySectionType,
  val initialExpanded: Boolean,
  val items: List<LegacyArrangeItem>
)

fun buildLegacyArrangeSections(
  monthEntries: List<MonthEntry>,
  budgetRows: List<BudgetRow>,
  projects: List<ProjectEntity>,
  sha2aItems: List<Sha2aItem>
): List<LegacyArrangeSection> {
  val sections = mutableListOf<LegacyArrangeSection>()

  // 1. Month tab sections: most recent month first (expanded), older months collapsed
  val legacyMonthEntries = monthEntries.filter { it.createdAt == null }
  val monthsDesc = legacyMonthEntries
    .map { it.month }
    .distinct()
    .sortedByDescending { if (ScheduleEngine.validM(it)) ScheduleEngine.mIdx(it) else Int.MIN_VALUE }

  val mostRecentMonth = monthsDesc.firstOrNull()
  for (m in monthsDesc) {
    val label = if (ScheduleEngine.validM(m)) ScheduleEngine.mLabel(m) else m
    val isExpanded = (m == mostRecentMonth)

    val monthIn = RecordOrder.sort(legacyMonthEntries.filter { it.month == m && it.kind == "in" })
    if (monthIn.isNotEmpty()) {
      sections.add(
        LegacyArrangeSection(
          id = "month_in_$m",
          title = "$label – Money in",
          type = LegacySectionType.MONTH_IN,
          initialExpanded = isExpanded,
          items = monthIn.map {
            LegacyArrangeItem(id = it.id, name = it.name, amount = it.amount, day = null, isIncome = true)
          }
        )
      )
    }

    val monthOut = RecordOrder.sort(legacyMonthEntries.filter { it.month == m && it.kind != "in" })
    if (monthOut.isNotEmpty()) {
      sections.add(
        LegacyArrangeSection(
          id = "month_out_$m",
          title = "$label – Money out",
          type = LegacySectionType.MONTH_OUT,
          initialExpanded = isExpanded,
          items = monthOut.map {
            LegacyArrangeItem(id = it.id, name = it.name, amount = it.amount, day = it.day, isIncome = false)
          }
        )
      )
    }
  }

  // 2. Home / All Income
  val legacyBudgetRows = budgetRows.filter { it.createdAt == null }
  val homeIn = RecordOrder.sort(legacyBudgetRows.filter { it.kind == "in" })
  if (homeIn.isNotEmpty()) {
    sections.add(
      LegacyArrangeSection(
        id = "home_income",
        title = "Home – Income",
        type = LegacySectionType.HOME_INCOME,
        initialExpanded = monthsDesc.isEmpty(),
        items = homeIn.map {
          LegacyArrangeItem(id = it.id, name = it.name, amount = it.amount, day = null, isIncome = true)
        }
      )
    )
  }

  // 3. Home / All Expenses (non-loan)
  val homeExp = RecordOrder.sort(legacyBudgetRows.filter { it.kind != "in" && !it.isLoan })
  if (homeExp.isNotEmpty()) {
    sections.add(
      LegacyArrangeSection(
        id = "home_expenses",
        title = "Home – Expenses",
        type = LegacySectionType.HOME_EXPENSE,
        initialExpanded = monthsDesc.isEmpty(),
        items = homeExp.map {
          LegacyArrangeItem(id = it.id, name = it.name, amount = it.amount, day = it.day, isIncome = false)
        }
      )
    )
  }

  // 4. Loans
  val loans = RecordOrder.sort(legacyBudgetRows.filter { it.isLoan })
  if (loans.isNotEmpty()) {
    sections.add(
      LegacyArrangeSection(
        id = "loans",
        title = "Loans",
        type = LegacySectionType.LOANS,
        initialExpanded = monthsDesc.isEmpty(),
        items = loans.map {
          LegacyArrangeItem(id = it.id, name = it.name, amount = it.total ?: it.amount, day = it.day, isIncome = false)
        }
      )
    )
  }

  // 5. Projects Money in / Money out
  val legacySha2a = sha2aItems.filter { it.createdAt == null }
  val projectNameById = projects.associate { it.id to it.name }
  val projectIds = (projects.map { it.id } + legacySha2a.map { it.projectId }).distinct()

  for (projId in projectIds) {
    val projName = projectNameById[projId] ?: projId
    val projIn = RecordOrder.sort(legacySha2a.filter { it.projectId == projId && it.side == "in" })
    if (projIn.isNotEmpty()) {
      sections.add(
        LegacyArrangeSection(
          id = "project_in_$projId",
          title = "$projName – Money in",
          type = LegacySectionType.PROJECT_IN,
          initialExpanded = monthsDesc.isEmpty(),
          items = projIn.map {
            LegacyArrangeItem(id = it.id, name = it.name, amount = it.amount, day = null, isIncome = true)
          }
        )
      )
    }

    val projOut = RecordOrder.sort(legacySha2a.filter { it.projectId == projId && it.side != "in" })
    if (projOut.isNotEmpty()) {
      sections.add(
        LegacyArrangeSection(
          id = "project_out_$projId",
          title = "$projName – Money out",
          type = LegacySectionType.PROJECT_OUT,
          initialExpanded = monthsDesc.isEmpty(),
          items = projOut.map {
            LegacyArrangeItem(id = it.id, name = it.name, amount = it.amount, day = null, isIncome = false)
          }
        )
      )
    }
  }

  return sections
}

@Composable
fun ArrangeLegacyRecordsScreen(
  monthEntries: List<MonthEntry>,
  budgetRows: List<BudgetRow>,
  projects: List<ProjectEntity>,
  sha2aItems: List<Sha2aItem>,
  onSave: (
    monthEntryOrders: Map<String, Int>,
    budgetRowOrders: Map<String, Int>,
    sha2aOrders: Map<String, Int>
  ) -> Unit
) {
  val colors = LocalMoneyMapColors.current
  val density = LocalDensity.current

  val initialSections = remember(monthEntries, budgetRows, projects, sha2aItems) {
    buildLegacyArrangeSections(monthEntries, budgetRows, projects, sha2aItems)
  }

  val sectionItems = remember(initialSections) {
    mutableStateMapOf<String, List<LegacyArrangeItem>>().apply {
      initialSections.forEach { put(it.id, it.items) }
    }
  }

  val expandedSections = remember(initialSections) {
    mutableStateMapOf<String, Boolean>().apply {
      initialSections.forEach { put(it.id, it.initialExpanded) }
    }
  }

  var draggingSectionId by remember { mutableStateOf<String?>(null) }
  var draggingItemId by remember { mutableStateOf<String?>(null) }
  var dragOffsetY by remember { mutableFloatStateOf(0f) }

  Scaffold(
    containerColor = colors.bg,
    contentWindowInsets = WindowInsets.statusBars,
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(colors.bg)
          .windowInsetsPadding(WindowInsets.statusBars)
          .padding(horizontal = 16.dp, vertical = 14.dp)
      ) {
        Text(
          text = "Arrange old records",
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = colors.ink,
          modifier = Modifier.testTag("arrange_legacy_title")
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Drag handles to order your existing records within each section. New records will automatically sort by creation date.",
          fontSize = 13.sp,
          color = colors.muted,
          lineHeight = 18.sp
        )
      }
    },
    bottomBar = {
      Surface(
        color = colors.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          AppPrimaryButton(
            text = "Save",
            icon = Icons.Default.Check,
            onClick = {
              val monthOrders = mutableMapOf<String, Int>()
              val budgetOrders = mutableMapOf<String, Int>()
              val sha2aOrders = mutableMapOf<String, Int>()

              initialSections.forEach { sec ->
                val list = sectionItems[sec.id] ?: sec.items
                when (sec.type) {
                  LegacySectionType.MONTH_IN, LegacySectionType.MONTH_OUT -> {
                    list.forEachIndexed { idx, item -> monthOrders[item.id] = idx }
                  }
                  LegacySectionType.HOME_INCOME, LegacySectionType.HOME_EXPENSE -> {
                    list.forEachIndexed { idx, item -> budgetOrders[item.id] = idx }
                  }
                  LegacySectionType.LOANS -> {
                    list.forEachIndexed { idx, item -> budgetOrders[item.id] = 10000 + idx }
                  }
                  LegacySectionType.PROJECT_IN, LegacySectionType.PROJECT_OUT -> {
                    list.forEachIndexed { idx, item -> sha2aOrders[item.id] = idx }
                  }
                }
              }

              onSave(monthOrders, budgetOrders, sha2aOrders)
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("arrange_legacy_save_btn")
          )
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .testTag("arrange_legacy_list"),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      userScrollEnabled = draggingItemId == null
    ) {
      initialSections.forEach { section ->
        item(key = section.id) {
          val isExpanded = expandedSections[section.id] ?: section.initialExpanded
          val currentList = sectionItems[section.id] ?: section.items

          Card(
            shape = AppShapes.Card,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.line),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("arrange_section_${section.id}")
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              // Collapsible Section Header
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { expandedSections[section.id] = !isExpanded }
                  .padding(horizontal = 14.dp, vertical = 12.dp)
                  .testTag("arrange_section_header_${section.id}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = section.title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${currentList.size} ${if (currentList.size == 1) "record" else "records"}",
                    fontSize = 12.sp,
                    color = colors.muted
                  )
                }

                Icon(
                  imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                  contentDescription = if (isExpanded) "Collapse ${section.title}" else "Expand ${section.title}",
                  tint = colors.muted
                )
              }

              AnimatedVisibility(visible = isExpanded) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  val defaultRowHeightPx = with(density) { 56.dp.toPx() }
                  var measuredRowHeightPx by remember(section.id) { mutableFloatStateOf(defaultRowHeightPx) }

                  currentList.forEachIndexed { idx, item ->
                    val isDragging = draggingSectionId == section.id && draggingItemId == item.id

                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                          translationY = if (isDragging) dragOffsetY else 0f
                          scaleX = if (isDragging) 1.015f else 1f
                          scaleY = if (isDragging) 1.015f else 1f
                        }
                        .onGloballyPositioned { coords ->
                          if (coords.size.height > 0) {
                            measuredRowHeightPx = coords.size.height.toFloat()
                          }
                        }
                        .clip(AppShapes.Medium)
                        .background(if (isDragging) colors.surface else colors.surface2)
                        .border(
                          width = if (isDragging) 1.5.dp else 0.5.dp,
                          color = if (isDragging) colors.head else colors.line,
                          shape = AppShapes.Medium
                        )
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("arrange_row_${section.id}_${item.id}"),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      // Drag handle — strictly scoped to THIS section only
                      Box(
                        modifier = Modifier
                          .size(40.dp)
                          .pointerInput(section.id, item.id) {
                            detectDragGestures(
                              onDragStart = {
                                draggingSectionId = section.id
                                draggingItemId = item.id
                                dragOffsetY = 0f
                              },
                              onDragEnd = {
                                draggingSectionId = null
                                draggingItemId = null
                                dragOffsetY = 0f
                              },
                              onDragCancel = {
                                draggingSectionId = null
                                draggingItemId = null
                                dragOffsetY = 0f
                              },
                              onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggingSectionId != section.id || draggingItemId != item.id) return@detectDragGestures
                                dragOffsetY += dragAmount.y
                                val list = (sectionItems[section.id] ?: return@detectDragGestures).toMutableList()
                                val curIdx = list.indexOfFirst { it.id == item.id }
                                if (curIdx == -1) return@detectDragGestures
                                val stepPx = (measuredRowHeightPx * 0.6f).coerceAtLeast(24f)

                                if (dragOffsetY > stepPx && curIdx < list.lastIndex) {
                                  val moved = list.removeAt(curIdx)
                                  list.add(curIdx + 1, moved)
                                  sectionItems[section.id] = list
                                  dragOffsetY -= measuredRowHeightPx
                                } else if (dragOffsetY < -stepPx && curIdx > 0) {
                                  val moved = list.removeAt(curIdx)
                                  list.add(curIdx - 1, moved)
                                  sectionItems[section.id] = list
                                  dragOffsetY += measuredRowHeightPx
                                }
                              }
                            )
                          }
                          .testTag("drag_handle_${section.id}_${item.id}"),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          imageVector = Icons.Default.DragHandle,
                          contentDescription = "Drag to reorder ${item.name}",
                          tint = if (isDragging) colors.head else colors.muted,
                          modifier = Modifier.size(22.dp)
                        )
                      }

                      Spacer(modifier = Modifier.width(6.dp))

                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = item.name,
                          fontSize = 14.sp,
                          fontWeight = FontWeight.SemiBold,
                          color = colors.ink,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis
                        )
                        if (item.day != null) {
                          Text(
                            text = "Day ${item.day}",
                            fontSize = 11.5.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                          )
                        }
                      }

                      Spacer(modifier = Modifier.width(8.dp))

                      Text(
                        text = ArabicNumberHelper.fmt(item.amount),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isIncome) colors.income else colors.ink
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
  }
}
