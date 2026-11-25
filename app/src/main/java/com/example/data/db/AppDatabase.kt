package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.MonthEntry
import com.example.data.model.PhotoReceipt
import com.example.data.model.ProjectEntity
import com.example.data.model.Sha2aItem
import com.example.util.ScheduleEngine

@Database(
  entities = [
    BudgetRow::class,
    MonthEntry::class,
    Sha2aItem::class,
    PhotoReceipt::class,
    AppSettings::class,
    ProjectEntity::class
  ],
  version = 4,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun budgetDao(): BudgetDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add createdAt and legacyOrder to budget_rows (existing records stay createdAt = NULL)
        db.execSQL("ALTER TABLE budget_rows ADD COLUMN createdAt INTEGER")
        db.execSQL("ALTER TABLE budget_rows ADD COLUMN legacyOrder INTEGER")

        // 2. Add createdAt and legacyOrder to sha2a_items (existing records stay createdAt = NULL)
        db.execSQL("ALTER TABLE sha2a_items ADD COLUMN createdAt INTEGER")
        db.execSQL("ALTER TABLE sha2a_items ADD COLUMN legacyOrder INTEGER")

        // 3. Add legacyArrangeDone and materializedMonthsJson to app_settings
        db.execSQL("ALTER TABLE app_settings ADD COLUMN legacyArrangeDone INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN materializedMonthsJson TEXT NOT NULL DEFAULT '[]'")

        // 4. Create independent month_entries table
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `month_entries` (
            `id` TEXT NOT NULL,
            `month` TEXT NOT NULL,
            `sourceRowId` TEXT,
            `name` TEXT NOT NULL,
            `kind` TEXT NOT NULL,
            `amount` REAL NOT NULL,
            `day` INTEGER,
            `isPaid` INTEGER NOT NULL,
            `isLoan` INTEGER NOT NULL,
            `payInfoText` TEXT NOT NULL,
            `createdAt` INTEGER,
            `legacyOrder` INTEGER,
            PRIMARY KEY(`id`)
          )
          """.trimIndent()
        )

        // 5. Read existing budget_rows in their pre-migration order (sortOrder ASC, name ASC)
        val existingRows = mutableListOf<BudgetRow>()
        db.query(
          """
          SELECT id, name, kind, amount, day, start, every, months, total,
                 stepsJson, overridesJson, isLoan, paidJson, sortOrder
          FROM budget_rows
          ORDER BY sortOrder ASC, name ASC
          """.trimIndent()
        ).use { c ->
          while (c.moveToNext()) {
            existingRows.add(
              BudgetRow(
                id = c.getString(0),
                name = c.getString(1),
                kind = c.getString(2),
                amount = c.getDouble(3),
                day = if (c.isNull(4)) null else c.getInt(4),
                start = c.getString(5),
                every = c.getInt(6),
                months = if (c.isNull(7)) null else c.getInt(7),
                total = if (c.isNull(8)) null else c.getDouble(8),
                stepsJson = c.getString(9),
                overridesJson = c.getString(10),
                isLoan = c.getInt(11) != 0,
                paidJson = c.getString(12),
                sortOrder = c.getInt(13),
                createdAt = null,
                legacyOrder = null
              )
            )
          }
        }

        // Assign legacyOrder = current position within each group:
        // - Income rows: 0, 1, 2...
        // - Non-loan expenses: 0, 1, 2...
        // - Loan expenses: 10000, 10001, ... (keeps Loans order independent without colliding with Home expenses)
        var inPos = 0
        var expPos = 0
        var loanPos = 0
        for (row in existingRows) {
          val pos = when {
            row.kind == "in" -> inPos++
            row.isLoan -> 10000 + (loanPos++)
            else -> expPos++
          }
          db.execSQL(
            "UPDATE budget_rows SET legacyOrder = ? WHERE id = ?",
            arrayOf<Any>(pos, row.id)
          )
        }

        // 6. Assign legacyOrder = current position for existing sha2a_items per (projectId, side)
        val sha2aCounters = mutableMapOf<Pair<String, String>, Int>()
        db.query(
          "SELECT id, projectId, side FROM sha2a_items ORDER BY projectId ASC, side ASC, sortOrder ASC, name ASC"
        ).use { c ->
          while (c.moveToNext()) {
            val id = c.getString(0)
            val projId = c.getString(1)
            val side = c.getString(2)
            val key = projId to side
            val pos = sha2aCounters[key] ?: 0
            sha2aCounters[key] = pos + 1
            db.execSQL(
              "UPDATE sha2a_items SET legacyOrder = ? WHERE id = ?",
              arrayOf<Any>(pos, id)
            )
          }
        }

        // 7. Materialize existing months (up through currentMonth) into independent legacy month_entries
        var startMonth = "2026-11"
        var endMonth = "2027-12"
        var hasSettingsRow = false
        db.query("SELECT startMonth, endMonth FROM app_settings WHERE id = 'main' LIMIT 1").use { c ->
          if (c.moveToFirst()) {
            hasSettingsRow = true
            startMonth = c.getString(0)
            endMonth = c.getString(1)
          }
        }

        val materializedMonths = mutableListOf<String>()
        if (existingRows.isNotEmpty()) {
          val curMonth = ScheduleEngine.currentMonth()
          var minMonth = startMonth
          existingRows.forEach { r ->
            if (ScheduleEngine.validM(r.start) && ScheduleEngine.mIdx(r.start) < ScheduleEngine.mIdx(minMonth)) {
              minMonth = r.start
            }
          }
          val maxLegacyMonth = if (ScheduleEngine.mIdx(curMonth) >= ScheduleEngine.mIdx(minMonth)) {
            curMonth
          } else {
            minMonth
          }

          val monthsToScan = ScheduleEngine.mRange(minMonth, maxLegacyMonth)
          for (m in monthsToScan) {
            val inForMonth = existingRows
              .filter { it.kind == "in" && ScheduleEngine.amt(it, m, startMonth, endMonth) > 0 }
              .sortedWith(compareBy({ it.sortOrder }, { it.name }))

            val outForMonth = existingRows
              .filter { it.kind != "in" && ScheduleEngine.amt(it, m, startMonth, endMonth) > 0 }
              .sortedWith(compareBy({ it.day ?: 99 }, { it.sortOrder }))

            if (inForMonth.isNotEmpty() || outForMonth.isNotEmpty()) {
              materializedMonths.add(m)
              inForMonth.forEachIndexed { idx, r ->
                val amt = ScheduleEngine.amt(r, m, startMonth, endMonth)
                val payInfo = ScheduleEngine.payInfo(r, m, startMonth, endMonth)
                val paid = if (r.getPaid()[m] == true) 1 else 0
                val entryId = "legacy-me-$m-${r.id}"
                db.execSQL(
                  """
                  INSERT OR REPLACE INTO month_entries
                  (id, month, sourceRowId, name, kind, amount, day, isPaid, isLoan, payInfoText, createdAt, legacyOrder)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                  """.trimIndent(),
                  arrayOf<Any?>(entryId, m, r.id, r.name, "in", amt, r.day, paid, 0, payInfo, idx)
                )
              }
              outForMonth.forEachIndexed { idx, r ->
                val amt = ScheduleEngine.amt(r, m, startMonth, endMonth)
                val payInfo = ScheduleEngine.payInfo(r, m, startMonth, endMonth)
                val paid = if (r.getPaid()[m] == true) 1 else 0
                val isLoanInt = if (r.isLoan) 1 else 0
                val entryId = "legacy-me-$m-${r.id}"
                db.execSQL(
                  """
                  INSERT OR REPLACE INTO month_entries
                  (id, month, sourceRowId, name, kind, amount, day, isPaid, isLoan, payInfoText, createdAt, legacyOrder)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                  """.trimIndent(),
                  arrayOf<Any?>(entryId, m, r.id, r.name, "out", amt, r.day, paid, isLoanInt, payInfo, idx)
                )
              }
            }
          }
        }

        if (hasSettingsRow && materializedMonths.isNotEmpty()) {
          val matJson = AppSettings.materializedMonthsToJson(materializedMonths)
          db.execSQL(
            "UPDATE app_settings SET materializedMonthsJson = ? WHERE id = 'main'",
            arrayOf<Any>(matJson)
          )
        }
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "money_map_database"
        )
          .addMigrations(MIGRATION_3_4)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
