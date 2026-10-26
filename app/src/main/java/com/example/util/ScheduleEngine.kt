package com.example.util

import com.example.data.model.BudgetRow
import java.util.Calendar
import java.util.Locale

data class ScheduleResult(
  val map: Map<String, Double>,
  val list: List<String>,
  val planned: Double,
  val finite: Boolean,
  val total: Double?,
  val every: Int,
  val first: String?,
  val last: String?
)

data class LoanInfoResult(
  val sc: ScheduleResult,
  val total: Double,
  val paid: Double,
  val remaining: Double
)

object ScheduleEngine {

  val MN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  fun currentMonth(): String {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    return mk(y, m)
  }

  fun mk(y: Int, m: Int): String = String.format(Locale.US, "%04d-%02d", y, m)

  fun validM(s: String?): Boolean {
    if (s.isNullOrBlank()) return false
    return Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(s)
  }

  fun mIdx(s: String): Int {
    val parts = s.split("-").map { it.toInt() }
    return parts[0] * 12 + (parts[1] - 1)
  }

  fun mOf(i: Int): String {
    val y = i / 12
    val m = (i % 12) + 1
    return mk(y, m)
  }

  fun mLabel(s: String): String {
    val i = mIdx(s)
    val monthName = MN[i % 12]
    val year = i / 12
    return "$monthName $year"
  }

  fun yearOf(s: String): Int = mIdx(s) / 12

  fun mRange(a: String, b: String): List<String> {
    val out = mutableListOf<String>()
    val startIdx = mIdx(a)
    val endIdx = mIdx(b)
    for (i in startIdx..endIdx) {
      out.add(mOf(i))
    }
    return out
  }

  fun allSelectableMonths(): List<String> = mRange("2024-01", "2032-12")

  fun rateAt(r: BudgetRow, m: String): Double {
    var a = r.amount
    val steps = r.getSteps().filter { validM(it.from) }.sortedBy { mIdx(it.from) }
    val mIndex = mIdx(m)
    for (s in steps) {
      if (mIdx(s.from) <= mIndex) {
        a = s.amount
      }
    }
    return a
  }

  fun computeSchedule(r: BudgetRow, rangeStart: String, rangeEnd: String): ScheduleResult {
    val start = if (validM(r.start)) r.start else rangeStart
    val every = maxOf(1, r.every)
    val months = if (r.months != null && r.months > 0) r.months else null
    val total = if (r.total != null && r.total > 0) r.total else null
    val finite = months != null || total != null
    val ov = r.getOverrides()
    val ovIdxs = ov.keys.filter { validM(it) }.map { mIdx(it) }
    val lastOv = if (ovIdxs.isNotEmpty()) ovIdxs.maxOrNull() ?: Int.MIN_VALUE else Int.MIN_VALUE
    val s0 = mIdx(start)
    val curIdx = mIdx(currentMonth())
    val endCap = if (finite) s0 + 600 else maxOf(mIdx(rangeEnd), curIdx, s0)

    val map = mutableMapOf<String, Double>()
    val list = mutableListOf<String>()
    var paid = 0.0

    for (i in s0..endCap) {
      val m = mOf(i)
      val el = i - s0
      var a = 0.0
      if (el % every == 0 && (months == null || el / every < months)) {
        a = rateAt(r, m)
      }
      if (ov.containsKey(m)) {
        a = maxOf(0.0, ov[m] ?: 0.0)
      }
      if (total != null) {
        a = minOf(a, maxOf(0.0, total - paid))
      }
      if (a > 0) {
        map[m] = a
        list.add(m)
        paid += a
      }
      if (finite && i >= lastOv) {
        if ((total != null && paid >= total - 1e-6) || (months != null && el >= (months - 1) * every)) {
          break
        }
      }
    }

    return ScheduleResult(
      map = map,
      list = list,
      planned = paid,
      finite = finite,
      total = total,
      every = every,
      first = list.firstOrNull(),
      last = list.lastOrNull()
    )
  }

  fun amt(r: BudgetRow, m: String, rangeStart: String, rangeEnd: String): Double {
    val sc = computeSchedule(r, rangeStart, rangeEnd)
    return sc.map[m] ?: 0.0
  }

  fun planAmt(r: BudgetRow, m: String, rangeStart: String, rangeEnd: String): Double {
    val ov = r.getOverrides().toMutableMap()
    ov.remove(m)
    val copy = r.copy(overridesJson = BudgetRow.overridesToJson(ov))
    return computeSchedule(copy, rangeStart, rangeEnd).map[m] ?: 0.0
  }

  fun remainingAfter(r: BudgetRow, m: String, rangeStart: String, rangeEnd: String): Double? {
    val sc = computeSchedule(r, rangeStart, rangeEnd)
    if (!sc.finite) return null
    val targetIdx = mIdx(m)
    var upto = 0.0
    for (x in sc.list) {
      if (mIdx(x) <= targetIdx) {
        upto += sc.map[x] ?: 0.0
      }
    }
    return if (sc.total != null) maxOf(0.0, sc.total - upto) else maxOf(0.0, sc.planned - upto)
  }

  fun loanInfo(r: BudgetRow, rangeStart: String, rangeEnd: String): LoanInfoResult {
    val sc = computeSchedule(r, rangeStart, rangeEnd)
    val total = sc.total ?: sc.planned
    val paidMap = r.getPaid()
    var paid = 0.0
    for (m in sc.list) {
      if (paidMap[m] == true) {
        paid += sc.map[m] ?: 0.0
      }
    }
    return LoanInfoResult(
      sc = sc,
      total = total,
      paid = paid,
      remaining = maxOf(0.0, total - paid)
    )
  }

  fun everyText(e: Int): String {
    return when (e) {
      1 -> "a month"
      12 -> "a year"
      else -> "every $e months"
    }
  }

  fun payInfo(r: BudgetRow, m: String, rangeStart: String, rangeEnd: String): String {
    val sc = computeSchedule(r, rangeStart, rangeEnd)
    if (!sc.finite) {
      return if (sc.every > 1) "Every ${sc.every} months" else if (r.kind == "in") "Monthly" else "Every month"
    }
    val i = sc.list.indexOf(m) + 1
    val rem = remainingAfter(r, m, rangeStart, rangeEnd) ?: 0.0
    val remText = if (rem > 0) "${ArabicNumberHelper.fmt(rem)} left after this" else "last one"
    return "Payment $i of ${sc.list.size}, $remText"
  }
}
