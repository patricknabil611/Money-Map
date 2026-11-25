package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Common contract implemented by every income and expense record type
 * (BudgetRow, MonthEntry, Sha2aItem) so ALL screens and areas share
 * one single sorting rule.
 */
interface OrderableRecord {
  val id: String
  val createdAt: Long?
  val legacyOrder: Int?
}

/**
 * Single source of truth for ordering and date formatting of income/expense records:
 * 1) Legacy records (createdAt == null) come first, sorted by legacyOrder ascending.
 * 2) Then all records with createdAt, sorted by createdAt ascending (oldest -> newest).
 * 3) Tie-breaker: record id ascending.
 */
object RecordOrder {

  val comparator: Comparator<OrderableRecord> = Comparator { a, b ->
    val aIsLegacy = a.createdAt == null
    val bIsLegacy = b.createdAt == null
    if (aIsLegacy != bIsLegacy) {
      return@Comparator if (aIsLegacy) -1 else 1
    }
    if (aIsLegacy) {
      val aOrder = a.legacyOrder ?: Int.MAX_VALUE
      val bOrder = b.legacyOrder ?: Int.MAX_VALUE
      val cmp = aOrder.compareTo(bOrder)
      if (cmp != 0) return@Comparator cmp
    } else {
      val cmp = a.createdAt!!.compareTo(b.createdAt!!)
      if (cmp != 0) return@Comparator cmp
    }
    a.id.compareTo(b.id)
  }

  fun <T : OrderableRecord> sort(records: List<T>): List<T> {
    return records.sortedWith(comparator)
  }

  /**
   * Formats a record's creation timestamp as "25 Nov 2026" following the device locale.
   * Returns null for legacy records (createdAt == null) so no placeholder or empty line is shown.
   */
  fun formatCreatedDate(createdAt: Long?, locale: Locale = Locale.getDefault()): String? {
    if (createdAt == null) return null
    val formatter = SimpleDateFormat("d MMM yyyy", locale)
    return formatter.format(Date(createdAt))
  }
}
