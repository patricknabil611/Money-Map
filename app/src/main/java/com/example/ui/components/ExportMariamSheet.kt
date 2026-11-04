package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetRow
import com.example.ui.theme.AppShapes
import com.example.ui.theme.LocalMoneyMapColors
import com.example.util.ArabicNumberHelper
import com.example.util.ExportConfig
import com.example.util.ImageExporter
import com.example.util.ScheduleEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportMariamSheet(
  allRows: List<BudgetRow>,
  rangeStart: String,
  rangeEnd: String,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val colors = LocalMoneyMapColors.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var fromMonth by remember { mutableStateOf(rangeStart) }
  var toMonth by remember { mutableStateOf(rangeEnd) }

  var incChecked by remember { mutableStateOf(true) }
  var leftChecked by remember { mutableStateOf(true) }
  var expChecked by remember { mutableStateOf(true) }
  var totalsAll by remember { mutableStateOf(true) }

  val inRows = remember(allRows) { allRows.filter { it.kind == "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name })) }
  val outRows = remember(allRows) { allRows.filter { it.kind != "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name })) }

  var selectedRows by remember(allRows) { mutableStateOf(allRows.map { it.id }.toSet()) }

  var colsChecked by remember { mutableStateOf(true) }
  var yearChecked by remember { mutableStateOf(true) }

  var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var isGenerating by remember { mutableStateOf(false) }

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
        .padding(bottom = 24.dp)
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Export to Mariam",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Pick the months and what to share. You’ll get an image titled “To Mariam”.",
            fontSize = 13.sp,
            color = colors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
        AppIconButton(
          onClick = onDismiss,
          icon = Icons.Default.Close,
          contentDescription = "Close",
          tint = colors.muted,
          modifier = Modifier.testTag("close_export_mariam_btn")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Month Range
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MonthDropdown(
          label = "From",
          selected = fromMonth,
          onSelect = { fromMonth = it; generatedBitmap = null },
          modifier = Modifier.weight(1f)
        )
        MonthDropdown(
          label = "To",
          selected = toMonth,
          onSelect = { toMonth = it; generatedBitmap = null },
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Totals section
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(AppShapes.Card)
          .border(1.dp, colors.line, AppShapes.Card)
          .padding(14.dp)
      ) {
        Text("Totals", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = incChecked,
            onCheckedChange = { incChecked = it; generatedBitmap = null },
            colors = CheckboxDefaults.colors(checkedColor = colors.income)
          )
          Text("Income", color = colors.ink, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = leftChecked,
            onCheckedChange = { leftChecked = it; generatedBitmap = null },
            colors = CheckboxDefaults.colors(checkedColor = colors.leftover)
          )
          Text("Left over", color = colors.ink, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = expChecked,
            onCheckedChange = { expChecked = it; generatedBitmap = null },
            colors = CheckboxDefaults.colors(checkedColor = colors.expense)
          )
          Text("Expenses", color = colors.ink, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("Totals add up", color = colors.muted, fontSize = 12.5.sp)
        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.Small)
            .background(colors.surface2)
            .padding(3.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(AppShapes.Small)
              .background(if (totalsAll) colors.surface else Color.Transparent)
              .clickable { totalsAll = true; generatedBitmap = null }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("All rows", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (totalsAll) colors.ink else colors.muted)
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(AppShapes.Small)
              .background(if (!totalsAll) colors.surface else Color.Transparent)
              .clickable { totalsAll = false; generatedBitmap = null }
              .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("Only shared rows", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (!totalsAll) colors.ink else colors.muted)
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Money in selection
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(AppShapes.Card)
          .border(1.dp, colors.line, AppShapes.Card)
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Money in", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AppSecondaryButton(
              text = "Select all",
              onClick = {
                val newSet = selectedRows.toMutableSet()
                inRows.forEach { newSet.add(it.id) }
                selectedRows = newSet
                generatedBitmap = null
              }
            )
            AppSecondaryButton(
              text = "Clear",
              onClick = {
                val newSet = selectedRows.toMutableSet()
                inRows.forEach { newSet.remove(it.id) }
                selectedRows = newSet
                generatedBitmap = null
              }
            )
          }
        }

        inRows.forEach { r ->
          val checked = selectedRows.contains(r.id)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedRows = if (checked) selectedRows - r.id else selectedRows + r.id
                generatedBitmap = null
              }
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = checked,
              onCheckedChange = {
                selectedRows = if (it) selectedRows + r.id else selectedRows - r.id
                generatedBitmap = null
              },
              colors = CheckboxDefaults.colors(checkedColor = colors.income)
            )
            Text(r.name, color = colors.ink, fontSize = 14.sp)
            if (r.day != null) {
              Text(
                " on the ${ArabicNumberHelper.ord(r.day)}",
                color = colors.muted,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 4.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Money out selection
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(AppShapes.Card)
          .border(1.dp, colors.line, AppShapes.Card)
          .padding(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Money out", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            AppSecondaryButton(
              text = "Select all",
              onClick = {
                val newSet = selectedRows.toMutableSet()
                outRows.forEach { newSet.add(it.id) }
                selectedRows = newSet
                generatedBitmap = null
              }
            )
            AppSecondaryButton(
              text = "Clear",
              onClick = {
                val newSet = selectedRows.toMutableSet()
                outRows.forEach { newSet.remove(it.id) }
                selectedRows = newSet
                generatedBitmap = null
              }
            )
          }
        }

        outRows.forEach { r ->
          val checked = selectedRows.contains(r.id)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                selectedRows = if (checked) selectedRows - r.id else selectedRows + r.id
                generatedBitmap = null
              }
              .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Checkbox(
              checked = checked,
              onCheckedChange = {
                selectedRows = if (it) selectedRows + r.id else selectedRows - r.id
                generatedBitmap = null
              },
              colors = CheckboxDefaults.colors(checkedColor = colors.expense)
            )
            Text(r.name, color = colors.ink, fontSize = 14.sp)
            if (r.day != null) {
              Text(
                " on the ${ArabicNumberHelper.ord(r.day)}",
                color = colors.muted,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(start = 4.dp)
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Columns options
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(AppShapes.Card)
          .border(1.dp, colors.line, AppShapes.Card)
          .padding(14.dp)
      ) {
        Text("Columns", fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = colsChecked,
            onCheckedChange = { colsChecked = it; generatedBitmap = null },
            colors = CheckboxDefaults.colors(checkedColor = colors.head)
          )
          Text("Day and amount", color = colors.ink, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(
            checked = yearChecked,
            onCheckedChange = { yearChecked = it; generatedBitmap = null },
            colors = CheckboxDefaults.colors(checkedColor = colors.head)
          )
          Text("End of year totals", color = colors.ink, fontSize = 14.sp)
        }
      }

      errorMsg?.let { err ->
        Text(
          text = err,
          color = colors.expense,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(top = 10.dp)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Create Image button
      AppPrimaryButton(
        text = if (isGenerating) "Creating the image…" else "Create image",
        onClick = {
          if (ScheduleEngine.mIdx(toMonth) < ScheduleEngine.mIdx(fromMonth)) {
            errorMsg = "“To” must be the same month as “From” or later."
            return@AppPrimaryButton
          }
          if (ScheduleEngine.mIdx(toMonth) - ScheduleEngine.mIdx(fromMonth) > 35) {
            errorMsg = "Pick up to 36 months."
            return@AppPrimaryButton
          }
          if (selectedRows.isEmpty() && !incChecked && !leftChecked && !expChecked) {
            errorMsg = "Choose at least one total or row to share."
            return@AppPrimaryButton
          }
          errorMsg = null
          isGenerating = true

          val config = ExportConfig(
            fromMonth = fromMonth,
            toMonth = toMonth,
            includeIncome = incChecked,
            includeLeftover = leftChecked,
            includeExpenses = expChecked,
            totalsAddUpAll = totalsAll,
            selectedRowIds = selectedRows,
            includeDayAndAmountCols = colsChecked,
            includeYearTotals = yearChecked
          )
          val bmp = ImageExporter.generateBitmap(config, allRows, rangeStart, rangeEnd)
          generatedBitmap = bmp
          isGenerating = false
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("create_mariam_image_btn")
      )

      // Generated Image Preview & Share
      generatedBitmap?.let { bmp ->
        Spacer(modifier = Modifier.height(20.dp))
        Text(
          text = "Preview",
          fontWeight = FontWeight.Bold,
          color = colors.ink,
          fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 340.dp)
            .clip(AppShapes.Card)
            .border(1.dp, colors.line, AppShapes.Card)
            .background(colors.surface2)
        ) {
          Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "To Mariam preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AppPrimaryButton(
          text = "Share image with Mariam",
          icon = Icons.Default.Share,
          containerColor = colors.income,
          contentColor = Color.White,
          onClick = {
            val uri = ImageExporter.saveBitmapAndGetUri(context, bmp)
            ImageExporter.shareImage(context, uri)
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("share_mariam_image_btn")
        )
      }
    }
  }
}
