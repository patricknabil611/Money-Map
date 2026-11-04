package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.model.BudgetRow
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

data class VoiceParsedTask(
  val kind: String, // "in" for income, "out" for expense, "loan" for loan, "task" for reminder task
  val title: String,
  val amount: Double,
  val day: Int? = null,
  val months: Int? = null,
  val category: String = "General",
  val rawVoiceText: String = ""
)

object VoiceNlpEngine {

  private const val TAG = "VoiceNlpEngine"

  // Lazy Gemini model initialization with structured schema
  private val taskSchema = Schema.obj(
    properties = mapOf(
      "kind" to Schema.string(description = "Must be one of: 'in', 'out', 'loan', 'task'"),
      "title" to Schema.string(description = "Brief title of the budget item or task"),
      "amount" to Schema.double(description = "Amount in Egyptian Pounds (EGP) or 0 if pure task"),
      "day" to Schema.integer(description = "Day of the month (1-31), or 0 if unspecified"),
      "months" to Schema.integer(description = "Duration in months if it is an installment or loan, else 0"),
      "category" to Schema.string(description = "Category such as Bills, Food, Housing, Salary, Shopping")
    ),
    optionalProperties = listOf("day", "months", "category")
  )

  private val generativeModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = taskSchema
      }
    )
  }

  suspend fun parseVoiceCommand(voiceInput: String): VoiceParsedTask = withContext(Dispatchers.IO) {
    val cleanInput = voiceInput.trim()
    if (cleanInput.isBlank()) {
      return@withContext VoiceParsedTask(
        kind = "out",
        title = "Voice Expense",
        amount = 0.0,
        rawVoiceText = cleanInput
      )
    }

    // Try Gemini AI Model first with realistic 12s timeout
    try {
      val prompt = """
        You are a financial voice assistant for P Money (an Egyptian Pounds budget app).
        Analyze the following voice command spoken by the user in English or Arabic, and extract the financial entry or task.
        User voice command: "$cleanInput"
      """.trimIndent()

      val response = withTimeout(12_000L) {
        generativeModel.generateContent(prompt)
      }

      val jsonStr = response.text
      if (!jsonStr.isNullOrBlank()) {
        val obj = JSONObject(jsonStr)
        val kind = obj.optString("kind", "out").lowercase()
        val title = obj.optString("title", "Voice entry").replaceFirstChar { it.uppercase() }
        val amount = obj.optDouble("amount", 0.0)
        val dayRaw = obj.optInt("day", 0)
        val day = if (dayRaw in 1..31) dayRaw else null
        val monthsRaw = obj.optInt("months", 0)
        val months = if (monthsRaw > 0) monthsRaw else null
        val category = obj.optString("category", "General")

        return@withContext VoiceParsedTask(
          kind = if (kind in listOf("in", "out", "loan", "task")) kind else "out",
          title = title,
          amount = amount,
          day = day,
          months = months,
          category = category,
          rawVoiceText = cleanInput
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Gemini parsing fallback to fast rule-based engine: ${e.message}")
    }

    // High-performance On-Device Rule-Based NLP Parser (Works offline seamlessly!)
    return@withContext parseOnDeviceRuleBased(cleanInput)
  }

  fun parseOnDeviceRuleBased(text: String): VoiceParsedTask {
    val lower = text.lowercase()
    val isArabic = text.any { it in '\u0600'..'\u06FF' }

    // 1. Detect Kind
    val isIncome = lower.contains("salary") || lower.contains("income") || lower.contains("مرتب") ||
                   lower.contains("قبض") || lower.contains("دخل") || lower.contains("مكافأة")
    val isLoan = lower.contains("loan") || lower.contains("قرض") || lower.contains("سلف") ||
                 lower.contains("تقسيط") || lower.contains("قسط")
    val isTask = lower.contains("task") || lower.contains("remind") || lower.contains("تذكير") ||
                 lower.contains("مهمة")

    val kind = when {
      isLoan -> "loan"
      isIncome -> "in"
      isTask -> "task"
      else -> "out"
    }

    // 2. Extract Numbers (Amounts)
    val numberRegex = Regex("""(\d+(?:[.,]\d+)?)""")
    val match = numberRegex.find(text)
    val amount = match?.value?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    // 3. Extract Day of month (e.g. "on 15th", "day 5", "يوم 10")
    val dayRegex = Regex("""(?:day|on|يوم)\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
    val dayMatch = dayRegex.find(text)
    val day = dayMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { if (it in 1..31) it else null }

    // 4. Generate clean title
    var title = text
      .replace(Regex("""(?:add|new|create|expense|income|task|loan|egp|pounds|جنيه|مصروف|ضيف|اضافة)""", RegexOption.IGNORE_CASE), "")
      .replace(numberRegex, "")
      .replace(dayRegex, "")
      .trim()

    if (title.isBlank()) {
      title = when (kind) {
        "in" -> if (isArabic) "دخل جديد" else "Voice Income"
        "loan" -> if (isArabic) "قرض أو قسط" else "Voice Loan"
        "task" -> if (isArabic) "تذكير بمهمة" else "Voice Task"
        else -> if (isArabic) "مصروف جديد" else "Voice Expense"
      }
    } else {
      title = title.replaceFirstChar { it.uppercase() }
    }

    return VoiceParsedTask(
      kind = kind,
      title = title,
      amount = amount,
      day = day,
      category = if (isIncome) "Income" else if (isLoan) "Loans" else "Expenses",
      rawVoiceText = text
    )
  }
}
