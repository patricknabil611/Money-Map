package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.BudgetRow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportConfig(
  val fromMonth: String,
  val toMonth: String,
  val includeIncome: Boolean,
  val includeLeftover: Boolean,
  val includeExpenses: Boolean,
  val totalsAddUpAll: Boolean, // true = all rows, false = only shared rows
  val selectedRowIds: Set<String>,
  val includeDayAndAmountCols: Boolean,
  val includeYearTotals: Boolean
)

sealed class ExportImageCol {
  data class MonthCol(val m: String) : ExportImageCol()
  data class YearCol(val y: Int, val upto: String, val ms: List<String>) : ExportImageCol()
}

sealed class ExportImageLine {
  object MonthHeader : ExportImageLine()
  data class TotalLine(val label: String, val kind: String, val f: (String) -> Double) : ExportImageLine()
  data class SectionHeader(val label: String) : ExportImageLine()
  data class RowLine(val row: BudgetRow) : ExportImageLine()
}

object ImageExporter {

  private val C_BG = Color.parseColor("#FFFFFF")
  private val C_INK = Color.parseColor("#172033")
  private val C_MUTED = Color.parseColor("#5B6576")
  private val C_LINE = Color.parseColor("#D7DCE4")
  private val C_IN = Color.parseColor("#157552")
  private val C_IN_S = Color.parseColor("#DDF0E6")
  private val C_OUT = Color.parseColor("#B9372F")
  private val C_OUT_S = Color.parseColor("#F9E1DE")
  private val C_LEFT = Color.parseColor("#2A50CC")
  private val C_LEFT_S = Color.parseColor("#E1E8FB")
  private val C_YR = Color.parseColor("#9A5B00")
  private val C_YR_S = Color.parseColor("#FFEFCF")
  private val C_HEAD = Color.parseColor("#172033")
  private val C_SEC = Color.parseColor("#F4F6F9")

  fun generateBitmap(
    config: ExportConfig,
    allRows: List<BudgetRow>,
    rangeStart: String,
    rangeEnd: String
  ): Bitmap {
    val months = ScheduleEngine.mRange(config.fromMonth, config.toMonth)
    val cols = mutableListOf<ExportImageCol>()

    months.forEachIndexed { i, m ->
      cols.add(ExportImageCol.MonthCol(m))
      val next = months.getOrNull(i + 1)
      if (config.includeYearTotals && (next == null || ScheduleEngine.yearOf(next) != ScheduleEngine.yearOf(m))) {
        val y = ScheduleEngine.yearOf(m)
        cols.add(ExportImageCol.YearCol(y, m, months.filter { ScheduleEngine.yearOf(it) == y }))
      }
    }

    val ins = allRows.filter { it.kind == "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))
    val outs = allRows.filter { it.kind != "in" }.sortedWith(compareBy({ it.sortOrder }, { it.name }))

    val sharedIns = ins.filter { config.selectedRowIds.contains(it.id) }
    val sharedOuts = outs.filter { config.selectedRowIds.contains(it.id) }

    val poolIns = if (config.totalsAddUpAll) ins else sharedIns
    val poolOuts = if (config.totalsAddUpAll) outs else sharedOuts

    val incFunc: (String) -> Double = { m -> poolIns.sumOf { ScheduleEngine.amt(it, m, rangeStart, rangeEnd) } }
    val expFunc: (String) -> Double = { m -> poolOuts.sumOf { ScheduleEngine.amt(it, m, rangeStart, rangeEnd) } }
    val leftFunc: (String) -> Double = { m -> incFunc(m) - expFunc(m) }

    val colWidths = mutableListOf<Int>()
    colWidths.add(250) // item name
    if (config.includeDayAndAmountCols) {
      colWidths.add(60)
      colWidths.add(110)
    }
    cols.forEach { c ->
      when (c) {
        is ExportImageCol.MonthCol -> colWidths.add(100)
        is ExportImageCol.YearCol -> colWidths.add(128)
      }
    }

    val totalTableWidth = colWidths.sum()

    val lines = mutableListOf<ExportImageLine>()
    lines.add(ExportImageLine.MonthHeader)
    if (config.includeIncome) lines.add(ExportImageLine.TotalLine("Income", "in", incFunc))
    if (config.includeLeftover) lines.add(ExportImageLine.TotalLine("Left over", "left", leftFunc))
    if (config.includeExpenses) lines.add(ExportImageLine.TotalLine("Expenses", "out", expFunc))

    if (sharedIns.isNotEmpty()) {
      lines.add(ExportImageLine.SectionHeader("Money in"))
      sharedIns.forEach { lines.add(ExportImageLine.RowLine(it)) }
    }
    if (sharedOuts.isNotEmpty()) {
      lines.add(ExportImageLine.SectionHeader("Money out"))
      sharedOuts.forEach { lines.add(ExportImageLine.RowLine(it)) }
    }

    val pad = 36
    val rowH = 40
    val topH = pad + 86
    val width = totalTableWidth + pad * 2
    val height = topH + lines.size * rowH + pad + 28

    val scale = 2
    val bitmap = Bitmap.createBitmap(width * scale, height * scale, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(scale.toFloat(), scale.toFloat())

    // Background
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = C_BG
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    // Title
    paint.color = C_INK
    paint.textSize = 34f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("To Mariam", pad.toFloat(), (pad + 30).toFloat(), paint)

    // Subtitle
    paint.color = C_MUTED
    paint.textSize = 15f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    val subtitle = "${ScheduleEngine.mLabel(config.fromMonth)} to ${ScheduleEngine.mLabel(config.toMonth)}"
    canvas.drawText(subtitle, pad.toFloat(), (pad + 58).toFloat(), paint)

    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    linePaint.color = C_LINE
    linePaint.strokeWidth = 1f
    linePaint.style = Paint.Style.STROKE

    val textBounds = Rect()

    lines.forEachIndexed { li, line ->
      val y = topH + li * rowH

      // Row background
      when (line) {
        is ExportImageLine.MonthHeader -> {
          paint.color = C_HEAD
          canvas.drawRect(pad.toFloat(), y.toFloat(), (pad + totalTableWidth).toFloat(), (y + rowH).toFloat(), paint)
        }
        is ExportImageLine.TotalLine -> {
          paint.color = when (line.kind) {
            "in" -> C_IN_S
            "out" -> C_OUT_S
            else -> C_LEFT_S
          }
          canvas.drawRect(pad.toFloat(), y.toFloat(), (pad + totalTableWidth).toFloat(), (y + rowH).toFloat(), paint)
        }
        is ExportImageLine.SectionHeader -> {
          paint.color = C_SEC
          canvas.drawRect(pad.toFloat(), y.toFloat(), (pad + totalTableWidth).toFloat(), (y + rowH).toFloat(), paint)
          paint.color = C_INK
          paint.textSize = 15f
          paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
          paint.textAlign = Paint.Align.LEFT
          canvas.drawText(line.label, (pad + 12).toFloat(), (y + rowH / 2 + 5).toFloat(), paint)
        }
        is ExportImageLine.RowLine -> {
          // Left indicator bar
          paint.color = if (line.row.kind == "in") C_IN else C_OUT
          canvas.drawRect(pad.toFloat(), y.toFloat(), (pad + 3).toFloat(), (y + rowH).toFloat(), paint)
        }
      }

      if (line !is ExportImageLine.SectionHeader) {
        // Collect cells
        data class Cell(val text: String, val isLeft: Boolean, val isYear: Boolean = false, val isMuted: Boolean = false, val isNeg: Boolean = false)
        val cells = mutableListOf<Cell>()

        when (line) {
          is ExportImageLine.MonthHeader -> {
            cells.add(Cell("Item", isLeft = true))
            if (config.includeDayAndAmountCols) {
              cells.add(Cell("Day", isLeft = false))
              cells.add(Cell("Amount", isLeft = false))
            }
            cols.forEach { c ->
              when (c) {
                is ExportImageCol.MonthCol -> cells.add(Cell(ScheduleEngine.mLabel(c.m), isLeft = false))
                is ExportImageCol.YearCol -> cells.add(Cell("End of ${c.y}", isLeft = false, isYear = true))
              }
            }
          }
          is ExportImageLine.TotalLine -> {
            cells.add(Cell(line.label, isLeft = true))
            if (config.includeDayAndAmountCols) {
              cells.add(Cell("", isLeft = false))
              cells.add(Cell("", isLeft = false))
            }
            cols.forEach { c ->
              when (c) {
                is ExportImageCol.MonthCol -> {
                  val v = line.f(c.m)
                  cells.add(Cell(ArabicNumberHelper.fmt(v), isLeft = false, isNeg = v < 0))
                }
                is ExportImageCol.YearCol -> {
                  val v = c.ms.sumOf { line.f(it) }
                  cells.add(Cell(ArabicNumberHelper.fmt(v), isLeft = false, isYear = true, isNeg = v < 0))
                }
              }
            }
          }
          is ExportImageLine.RowLine -> {
            val r = line.row
            val sc = ScheduleEngine.computeSchedule(r, rangeStart, rangeEnd)
            cells.add(Cell(r.name, isLeft = true))
            if (config.includeDayAndAmountCols) {
              cells.add(Cell(r.day?.toString() ?: "", isLeft = false, isMuted = true))
              val amtVal = if (sc.finite) (sc.total ?: sc.planned) else ScheduleEngine.rateAt(r, config.fromMonth)
              cells.add(Cell(ArabicNumberHelper.fmt(amtVal), isLeft = false))
            }
            cols.forEach { c ->
              when (c) {
                is ExportImageCol.MonthCol -> {
                  val v = ScheduleEngine.amt(r, c.m, rangeStart, rangeEnd)
                  cells.add(Cell(if (v > 0) ArabicNumberHelper.fmt(v) else "", isLeft = false))
                }
                is ExportImageCol.YearCol -> {
                  val rem = ScheduleEngine.remainingAfter(r, c.upto, rangeStart, rangeEnd)
                  cells.add(Cell(if (rem != null) ArabicNumberHelper.fmt(rem) else "", isLeft = false, isYear = true))
                }
              }
            }
          }
          else -> {}
        }

        var cellX = pad
        cells.forEachIndexed { ci, cell ->
          val w = colWidths.getOrElse(ci) { 100 }
          if (cell.isYear) {
            paint.color = if (line is ExportImageLine.MonthHeader) C_YR else C_YR_S
            canvas.drawRect(cellX.toFloat(), y.toFloat(), (cellX + w).toFloat(), (y + rowH).toFloat(), paint)
          }

          paint.color = when {
            line is ExportImageLine.MonthHeader -> Color.WHITE
            line is ExportImageLine.TotalLine -> {
              if (cell.isNeg) C_OUT else when (line.kind) {
                "in" -> C_IN
                "out" -> C_OUT
                else -> C_LEFT
              }
            }
            cell.isYear -> C_YR
            cell.isMuted -> C_MUTED
            else -> C_INK
          }

          paint.textSize = 14f
          paint.typeface = when {
            line is ExportImageLine.MonthHeader || line is ExportImageLine.TotalLine -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            ci == 0 -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            cell.isYear -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
          }

          var text = cell.text
          // fit text
          val maxW = w - 20
          if (paint.measureText(text) > maxW) {
            while (text.length > 1 && paint.measureText("$text…") > maxW) {
              text = text.substring(0, text.length - 1)
            }
            text = "$text…"
          }

          if (cell.isLeft) {
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(text, (cellX + 12).toFloat(), (y + rowH / 2 + 5).toFloat(), paint)
          } else {
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(text, (cellX + w - 10).toFloat(), (y + rowH / 2 + 5).toFloat(), paint)
          }

          cellX += w
        }
      }

      // Bottom line of row
      canvas.drawLine(
        pad.toFloat(),
        (y + rowH).toFloat(),
        (pad + totalTableWidth).toFloat(),
        (y + rowH).toFloat(),
        linePaint
      )
    }

    // Vertical grid lines
    var vx = pad
    colWidths.forEach { w ->
      vx += w
      canvas.drawLine(
        vx.toFloat(),
        topH.toFloat(),
        vx.toFloat(),
        (topH + lines.size * rowH).toFloat(),
        linePaint
      )
    }

    // Outer table border
    linePaint.style = Paint.Style.STROKE
    canvas.drawRect(
      pad.toFloat(),
      topH.toFloat(),
      (pad + totalTableWidth).toFloat(),
      (topH + lines.size * rowH).toFloat(),
      linePaint
    )

    // Footer
    paint.color = C_MUTED
    paint.textSize = 12f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textAlign = Paint.Align.LEFT
    val df = SimpleDateFormat("d MMMM yyyy", Locale.US)
    val footer = "Prepared ${df.format(Date())}"
    canvas.drawText(footer, pad.toFloat(), (height - pad + 12).toFloat(), paint)

    return bitmap
  }

  fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, "shared")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "to_mariam.png")
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  }

  fun shareImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_TITLE, "To Mariam")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Share image with Mariam")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }
}
