package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object ArabicNumberHelper {

  private val easternArabic = "٠١٢٣٤٥٦٧٨٩"
  private val persianArabic = "۰۱۲۳۴۵۶۷۸۹"

  /**
   * Replaces Arabic/Persian digits with standard ASCII 0-9 digits,
   * removes spaces and commas.
   */
  fun normalizeDigits(input: String?): String {
    if (input.isNullOrBlank()) return ""
    val sb = StringBuilder()
    for (ch in input) {
      val eastIdx = easternArabic.indexOf(ch)
      if (eastIdx >= 0) {
        sb.append(eastIdx)
        continue
      }
      val persIdx = persianArabic.indexOf(ch)
      if (persIdx >= 0) {
        sb.append(persIdx)
        continue
      }
      if (ch != ',' && ch != ' ' && ch != '\u00A0') {
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  fun parseNumber(input: String?): Double? {
    val norm = normalizeDigits(input)
    if (norm.isEmpty()) return null
    return norm.toDoubleOrNull()
  }

  fun parseInt(input: String?): Int? {
    val num = parseNumber(input) ?: return null
    return num.toInt()
  }

  /**
   * Matches web app: Math.round(Number(n)||0); return (n<0?'−':'')+Math.abs(n).toLocaleString('en-US')
   */
  fun fmt(n: Double?): String {
    val value = n ?: 0.0
    val rounded = value.roundToLong()
    val isNeg = rounded < 0
    val absVal = abs(rounded)

    val symbols = DecimalFormatSymbols(Locale.US)
    val formatter = DecimalFormat("#,###", symbols)
    val formatted = formatter.format(absVal)
    return if (isNeg) "−$formatted" else formatted
  }

  fun fmt(n: Long?): String = fmt((n ?: 0L).toDouble())
  fun fmt(n: Int?): String = fmt((n ?: 0).toDouble())

  fun ord(day: Int): String {
    val s = listOf("th", "st", "nd", "rd")
    val v = day % 100
    val suffix = s.getOrNull((v - 20) % 10) ?: s.getOrNull(v) ?: s[0]
    return "$day$suffix"
  }
}
