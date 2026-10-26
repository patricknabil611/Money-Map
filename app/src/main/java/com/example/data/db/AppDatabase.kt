package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.PhotoReceipt
import com.example.data.model.Sha2aItem
import com.example.data.model.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@Database(
  entities = [BudgetRow::class, Sha2aItem::class, PhotoReceipt::class, AppSettings::class, com.example.data.model.ProjectEntity::class],
  version = 3,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

  abstract fun budgetDao(): BudgetDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "money_map_database"
        ).fallbackToDestructiveMigration(dropAllTables = false)
        .addCallback(object : Callback() {
          override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
              populateInitialData(context.applicationContext, getDatabase(context))
            }
          }
        }).build()
        INSTANCE = instance
        instance
      }
    }

    suspend fun populateInitialData(context: Context, database: AppDatabase) {
      try {
        val inputStream = context.resources.openRawResource(R.raw.initial_data)
        val jsonStr = inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)

        val settingsObj = root.optJSONObject("settings")
        val startM = settingsObj?.optString("start", "2026-11") ?: "2026-11"
        val endM = settingsObj?.optString("end", "2027-12") ?: "2027-12"
        val settings = AppSettings(startMonth = startM, endMonth = endM)

        val rowsList = mutableListOf<BudgetRow>()
        val rowsArr = root.optJSONArray("rows")
        if (rowsArr != null) {
          for (i in 0 until rowsArr.length()) {
            val r = rowsArr.getJSONObject(i)
            val id = r.optString("id", "row-$i")
            val name = r.optString("name", "")
            val kind = r.optString("kind", "out")
            val amount = r.optDouble("amount", 0.0)
            val day = if (r.isNull("day")) null else r.optInt("day")
            val start = r.optString("start", "2026-08")
            val every = r.optInt("every", 1)
            val months = if (r.isNull("months")) null else r.optInt("months")
            val total = if (r.isNull("total")) null else r.optDouble("total")
            val isLoan = r.optBoolean("isLoan", false)
            val order = r.optInt("order", (i + 1) * 10)

            val stepsArr = r.optJSONArray("steps")
            val stepsJson = stepsArr?.toString() ?: "[]"

            val overridesObj = r.optJSONObject("overrides")
            val overridesJson = overridesObj?.toString() ?: "{}"

            val paidObj = r.optJSONObject("paid")
            val paidJson = paidObj?.toString() ?: "{}"

            rowsList.add(
              BudgetRow(
                id = id,
                name = name,
                kind = kind,
                amount = amount,
                day = day,
                start = start,
                every = every,
                months = months,
                total = total,
                stepsJson = stepsJson,
                overridesJson = overridesJson,
                isLoan = isLoan,
                paidJson = paidJson,
                sortOrder = order
              )
            )
          }
        }

        val sha2aList = mutableListOf<Sha2aItem>()
        val sha2aArr = root.optJSONArray("sha2a")
        if (sha2aArr != null) {
          for (i in 0 until sha2aArr.length()) {
            val item = sha2aArr.getJSONObject(i)
            sha2aList.add(
              Sha2aItem(
                id = item.optString("id", "sha2a-$i"),
                name = item.optString("name", ""),
                amount = item.optDouble("amount", 0.0),
                side = item.optString("side", "out"),
                sortOrder = item.optInt("order", i + 1)
              )
            )
          }
        }

        database.budgetDao().replaceAllData(
          settings = settings,
          rows = rowsList,
          sha2a = sha2aList,
          photos = emptyList()
        )

        database.budgetDao().insertProject(
          com.example.data.model.ProjectEntity(
            id = "sha2a",
            name = "Sha2a",
            description = "Apartment costs & Andrew’s receipts",
            sortOrder = 1
          )
        )
      } catch (_: Exception) {}
    }
  }
}
