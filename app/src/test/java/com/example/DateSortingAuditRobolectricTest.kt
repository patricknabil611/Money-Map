package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.MonthEntry
import com.example.data.model.ProjectEntity
import com.example.data.model.Sha2aItem
import com.example.data.repository.BudgetRepository
import com.example.ui.components.ExportMariamSheet
import com.example.ui.components.MonthEntryEditorSheet
import com.example.ui.components.RowEditorSheet
import com.example.ui.screens.ArrangeLegacyRecordsScreen
import com.example.ui.screens.HomeOverviewScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LegacySectionType
import com.example.ui.screens.LoansScreen
import com.example.ui.screens.MonthScreen
import com.example.ui.screens.Sha2aScreen
import com.example.ui.screens.buildLegacyArrangeSections
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ExportConfig
import com.example.util.ImageExporter
import com.example.util.RecordOrder
import com.example.util.ScheduleEngine
import com.example.util.VoiceNlpEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DateSortingAuditRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun epochFor(year: Int, month1Based: Int, day: Int, hour: Int = 12): Long {
    val cal = Calendar.getInstance(TimeZone.getDefault(), Locale.US)
    cal.set(year, month1Based - 1, day, hour, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
  }

  private fun createInMemoryDb(): AppDatabase {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  private fun createLegacyV3Database(context: Context, dbName: String) {
    context.deleteDatabase(dbName)
    val helper = object : SQLiteOpenHelper(context, dbName, null, 3) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS budget_rows (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            kind TEXT NOT NULL,
            amount REAL NOT NULL,
            day INTEGER,
            start TEXT NOT NULL,
            every INTEGER NOT NULL,
            months INTEGER,
            total REAL,
            stepsJson TEXT NOT NULL,
            overridesJson TEXT NOT NULL,
            isLoan INTEGER NOT NULL,
            paidJson TEXT NOT NULL,
            sortOrder INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS projects (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT NOT NULL,
            sortOrder INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS sha2a_items (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            amount REAL NOT NULL,
            side TEXT NOT NULL,
            sortOrder INTEGER NOT NULL,
            projectId TEXT NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS photo_receipts (
            id TEXT NOT NULL PRIMARY KEY,
            caption TEXT NOT NULL,
            localFileName TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            projectId TEXT NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS app_settings (
            id TEXT NOT NULL PRIMARY KEY,
            startMonth TEXT NOT NULL,
            endMonth TEXT NOT NULL,
            themeMode TEXT NOT NULL,
            driveAccount TEXT NOT NULL,
            lastDriveBackup INTEGER NOT NULL
          )
          """.trimIndent()
        )

        val curM = ScheduleEngine.currentMonth()
        val prevM = ScheduleEngine.mOf(ScheduleEngine.mIdx(curM) - 1)
        val nextM = ScheduleEngine.mOf(ScheduleEngine.mIdx(curM) + 1)

        db.execSQL(
          "INSERT INTO app_settings (id, startMonth, endMonth, themeMode, driveAccount, lastDriveBackup) VALUES ('main', ?, ?, 'system', '', 0)",
          arrayOf(prevM, nextM)
        )
        db.execSQL(
          "INSERT INTO projects (id, name, description, sortOrder) VALUES ('sha2a', 'Sha2a', 'Apt', 1)"
        )
        // Two legacy incomes
        db.execSQL(
          "INSERT INTO budget_rows VALUES ('inc_1', 'Salary', 'in', 50000.0, 1, ?, 1, NULL, NULL, '[]', '{}', 0, '{}', 1)",
          arrayOf(prevM)
        )
        db.execSQL(
          "INSERT INTO budget_rows VALUES ('inc_2', 'Bonus', 'in', 10000.0, 15, ?, 1, NULL, NULL, '[]', '{}', 0, '{}', 2)",
          arrayOf(prevM)
        )
        // Two legacy non-loan expenses with days 20 and 5
        db.execSQL(
          "INSERT INTO budget_rows VALUES ('exp_1', 'Rent', 'out', 15000.0, 20, ?, 1, NULL, NULL, '[]', '{}', 0, '{}', 1)",
          arrayOf(prevM)
        )
        db.execSQL(
          "INSERT INTO budget_rows VALUES ('exp_2', 'Internet', 'out', 800.0, 5, ?, 1, NULL, NULL, '[]', '{}', 0, '{}', 2)",
          arrayOf(prevM)
        )
        // Two legacy loans
        db.execSQL(
          "INSERT INTO budget_rows VALUES ('loan_1', 'Car Loan', 'out', 5000.0, 10, ?, 1, 12, 60000.0, '[]', '{}', 1, '{}', 3)",
          arrayOf(prevM)
        )
        db.execSQL(
          "INSERT INTO budget_rows VALUES ('loan_2', 'Phone Installment', 'out', 2000.0, 25, ?, 1, 6, 12000.0, '[]', '{}', 1, '{}', 4)",
          arrayOf(prevM)
        )
        // Legacy project items
        db.execSQL(
          "INSERT INTO sha2a_items VALUES ('sh_in_1', 'Deposit 1', 100000.0, 'in', 1, 'sha2a')"
        )
        db.execSQL(
          "INSERT INTO sha2a_items VALUES ('sh_in_2', 'Deposit 2', 50000.0, 'in', 2, 'sha2a')"
        )
        db.execSQL(
          "INSERT INTO sha2a_items VALUES ('sh_out_1', 'Tiles', 25000.0, 'out', 1, 'sha2a')"
        )
        db.execSQL(
          "INSERT INTO sha2a_items VALUES ('sh_out_2', 'Paint', 12000.0, 'out', 2, 'sha2a')"
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    helper.writableDatabase.close()
  }

  @Test
  fun test01_Migration3To4_PreservesData_SetsNullCreatedAt_AndShowsArrangeScreenOnce() = runBlocking {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "test_migration_v3_v4_${System.nanoTime()}.db"
    createLegacyV3Database(ctx, dbName)

    val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
      .addMigrations(AppDatabase.MIGRATION_3_4)
      .allowMainThreadQueries()
      .build()

    try {
      val repo = BudgetRepository(db.budgetDao(), ctx)
      val dao = db.budgetDao()

      val rows = dao.getAllBudgetRowsSync()
      val sha2a = dao.getAllSha2aItemsSync()
      val monthEntries = dao.getAllMonthEntriesSync()
      val settings = dao.getSettingsSync()!!

      // 1. All existing data preserved with createdAt == null and non-null legacyOrder
      assertEquals(6, rows.size)
      assertEquals(4, sha2a.size)
      assertTrue(rows.all { it.createdAt == null && it.legacyOrder != null })
      assertTrue(sha2a.all { it.createdAt == null && it.legacyOrder != null })
      assertTrue(monthEntries.isNotEmpty())
      assertTrue(monthEntries.all { it.createdAt == null && it.legacyOrder != null })
      assertFalse(settings.legacyArrangeDone)

      val curM = ScheduleEngine.currentMonth()
      val prevM = ScheduleEngine.mOf(ScheduleEngine.mIdx(curM) - 1)
      val materialized = settings.getMaterializedMonths()
      assertTrue(materialized.contains(prevM))
      assertTrue(materialized.contains(curM))

      // 2. First launch triggers Arrange old records screen
      assertTrue(repo.evaluateLegacyArrangeNeeded())

      // 3. Sections: each month has its own separate Money in / Money out sections; most recent expanded, older collapsed
      val sections = buildLegacyArrangeSections(
        monthEntries = monthEntries,
        budgetRows = rows,
        projects = dao.getAllProjects().first(),
        sha2aItems = sha2a
      )
      val curInSec = sections.first { it.id == "month_in_$curM" }
      val curOutSec = sections.first { it.id == "month_out_$curM" }
      val prevInSec = sections.first { it.id == "month_in_$prevM" }
      val prevOutSec = sections.first { it.id == "month_out_$prevM" }

      assertTrue("Most recent month Money in must start expanded", curInSec.initialExpanded)
      assertTrue("Most recent month Money out must start expanded", curOutSec.initialExpanded)
      assertFalse("Older month Money in must start collapsed", prevInSec.initialExpanded)
      assertFalse("Older month Money out must start collapsed", prevOutSec.initialExpanded)
      assertTrue(sections.any { it.type == LegacySectionType.HOME_INCOME })
      assertTrue(sections.any { it.type == LegacySectionType.HOME_EXPENSE })
      assertTrue(sections.any { it.type == LegacySectionType.LOANS })
      assertTrue(sections.any { it.type == LegacySectionType.PROJECT_IN })
      assertTrue(sections.any { it.type == LegacySectionType.PROJECT_OUT })

      // 4. Closing without saving changes nothing; next launch still shows arrange screen
      assertTrue(repo.evaluateLegacyArrangeNeeded())
      val beforeCurIn = RecordOrder.sort(monthEntries.filter { it.month == curM && it.kind == "in" }).map { it.name }
      assertEquals(listOf("Salary", "Bonus"), beforeCurIn)

      // 5. Reorder ONLY in prevM (swap Bonus before Salary in prevM), leave curM unchanged, and Save!
      val prevInEntries = RecordOrder.sort(monthEntries.filter { it.month == prevM && it.kind == "in" })
      val curInEntries = RecordOrder.sort(monthEntries.filter { it.month == curM && it.kind == "in" })
      val monthOrders = mapOf(
        prevInEntries[1].id to 0, // Bonus first in prevM
        prevInEntries[0].id to 1, // Salary second in prevM
        curInEntries[0].id to 0,  // Salary still first in curM
        curInEntries[1].id to 1   // Bonus still second in curM
      )
      repo.saveLegacyArrangement(
        monthEntryOrders = monthOrders,
        budgetRowOrders = mapOf("inc_2" to 0, "inc_1" to 1),
        sha2aOrders = mapOf("sh_out_2" to 0, "sh_out_1" to 1)
      )

      // 6. After Save: legacyArrangeDone == true, screen NEVER appears again
      assertTrue(dao.getSettingsSync()!!.legacyArrangeDone)
      assertFalse(repo.evaluateLegacyArrangeNeeded())

      // 7. Reordering prevM did NOT affect curM!
      val updatedEntries = dao.getAllMonthEntriesSync()
      val afterPrevIn = RecordOrder.sort(updatedEntries.filter { it.month == prevM && it.kind == "in" }).map { it.name }
      val afterCurIn = RecordOrder.sort(updatedEntries.filter { it.month == curM && it.kind == "in" }).map { it.name }
      assertEquals(listOf("Bonus", "Salary"), afterPrevIn)
      assertEquals(listOf("Salary", "Bonus"), afterCurIn)

      // 8. BudgetRow legacyOrder applies everywhere that same record appears (Home, All, Export)
      val afterHomeIn = RecordOrder.sort(dao.getAllBudgetRowsSync().filter { it.kind == "in" }).map { it.name }
      assertEquals(listOf("Bonus", "Salary"), afterHomeIn)

      // 9. Project legacyOrder persisted
      val afterProjOut = RecordOrder.sort(dao.getAllSha2aItemsSync().filter { it.side == "out" }).map { it.name }
      assertEquals(listOf("Paint", "Tiles"), afterProjOut)
    } finally {
      db.close()
      ctx.deleteDatabase(dbName)
    }
  }

  @Test
  fun test02_NoLegacyRecords_SilentlyMarksArrangeDoneAndSkipsScreen() = runBlocking {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val db = createInMemoryDb()
    try {
      val repo = BudgetRepository(db.budgetDao(), ctx)
      repo.checkAndSeedIfEmpty()
      // Add only a brand new record with real createdAt
      repo.saveRow(
        BudgetRow(id = "r1", name = "New Income", kind = "in", amount = 1000.0, day = null, start = "2026-11", createdAt = 1_700_000_000_000L)
      ) { 1_700_000_000_000L }

      assertFalse(repo.evaluateLegacyArrangeNeeded())
      assertTrue(db.budgetDao().getSettingsSync()!!.legacyArrangeDone)
    } finally {
      db.close()
    }
  }

  @Test
  fun test03_MonthlyCopiesIndependence_AndBatchMaterializationPreservesOrderWithRealTimestamps() = runBlocking {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val db = createInMemoryDb()
    try {
      val repo = BudgetRepository(db.budgetDao(), ctx)
      val dao = db.budgetDao()
      dao.insertSettings(
        AppSettings(
          startMonth = "2026-10",
          endMonth = "2026-12",
          legacyArrangeDone = true,
          materializedMonthsJson = AppSettings.materializedMonthsToJson(setOf("2026-10", "2026-11"))
        )
      )
      // Base BudgetRows (legacy)
      dao.insertBudgetRow(BudgetRow(id = "r_rent", name = "Rent", kind = "out", amount = 10000.0, day = 25, start = "2026-10", createdAt = null, legacyOrder = 0))
      dao.insertBudgetRow(BudgetRow(id = "r_net", name = "Internet", kind = "out", amount = 800.0, day = 3, start = "2026-10", createdAt = null, legacyOrder = 1))
      dao.insertBudgetRow(BudgetRow(id = "r_gym", name = "Gym", kind = "out", amount = 1200.0, day = 12, start = "2026-10", createdAt = null, legacyOrder = 2))

      // Oct has order: Rent, Internet, Gym
      dao.insertMonthEntries(
        listOf(
          MonthEntry(id = "oct_1", month = "2026-10", sourceRowId = "r_rent", name = "Rent", kind = "out", amount = 10000.0, day = 25, createdAt = null, legacyOrder = 0),
          MonthEntry(id = "oct_2", month = "2026-10", sourceRowId = "r_net", name = "Internet", kind = "out", amount = 800.0, day = 3, createdAt = null, legacyOrder = 1),
          MonthEntry(id = "oct_3", month = "2026-10", sourceRowId = "r_gym", name = "Gym", kind = "out", amount = 1200.0, day = 12, createdAt = null, legacyOrder = 2)
        )
      )
      // Nov has DIFFERENT order: Gym (legacy), Rent (legacy), Internet (new)
      dao.insertMonthEntries(
        listOf(
          MonthEntry(id = "nov_1", month = "2026-11", sourceRowId = "r_gym", name = "Gym", kind = "out", amount = 1200.0, day = 12, createdAt = null, legacyOrder = 0),
          MonthEntry(id = "nov_2", month = "2026-11", sourceRowId = "r_rent", name = "Rent", kind = "out", amount = 10000.0, day = 25, createdAt = null, legacyOrder = 1),
          MonthEntry(id = "nov_3", month = "2026-11", sourceRowId = "r_net", name = "Internet", kind = "out", amount = 800.0, day = 3, createdAt = 1_763_000_000_000L, legacyOrder = null)
        )
      )

      // Edit Nov's "Rent" -> Oct's "Rent" MUST remain untouched
      val novRent = dao.getMonthEntryById("nov_2")!!
      repo.saveMonthEntry(novRent.copy(name = "Apartment Rent", amount = 11000.0, day = 28))
      val octRentAfter = dao.getMonthEntryById("oct_1")!!
      assertEquals("Rent", octRentAfter.name)
      assertEquals(10000.0, octRentAfter.amount, 0.01)
      assertEquals(25, octRentAfter.day)
      // Nov edit preserved createdAt == null & legacyOrder == 1
      val novRentAfter = dao.getMonthEntryById("nov_2")!!
      assertNull(novRentAfter.createdAt)
      assertEquals(1, novRentAfter.legacyOrder)

      // Automatically create Dec 2026 copies in one batch
      val batchTime = epochFor(2026, 12, 1, 9)
      val decEntries = repo.ensureMonthMaterialized("2026-12") { batchTime }
      val decOut = RecordOrder.sort(decEntries.filter { it.kind == "out" })

      // Copies of legacy records are NEW records (non-null createdAt, null legacyOrder)
      assertEquals(3, decOut.size)
      assertTrue(decOut.all { it.createdAt != null && it.legacyOrder == null })
      // Preserves exact source month (Nov) order: Gym -> Apartment Rent -> Internet
      assertEquals(listOf("Gym", "Apartment Rent", "Internet"), decOut.map { it.name })
      // Strictly increasing timestamps within the batch (+0ms, +1ms, +2ms)
      assertTrue(decOut[0].createdAt!! < decOut[1].createdAt!!)
      assertTrue(decOut[1].createdAt!! < decOut[2].createdAt!!)
      // Day field preserved on copies, NOT controlling sort order (12, 28, 3)
      assertEquals(listOf(12, 28, 3), decOut.map { it.day })
    } finally {
      db.close()
    }
  }

  @Test
  fun test04_SequentialAdditionsAndEditsAcrossAllScreens_IncludingVoiceNlpAndProjects() = runBlocking {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val db = createInMemoryDb()
    try {
      val repo = BudgetRepository(db.budgetDao(), ctx)
      val dao = db.budgetDao()
      dao.insertSettings(
        AppSettings(
          startMonth = "2026-11",
          endMonth = "2027-02",
          legacyArrangeDone = true,
          materializedMonthsJson = AppSettings.materializedMonthsToJson(setOf("2026-11"))
        )
      )

      // Seed 1 legacy record per category so we verify legacy-first then createdAt-ASC
      dao.insertBudgetRow(BudgetRow(id = "leg_in", name = "Old Salary", kind = "in", amount = 40000.0, day = null, start = "2026-11", createdAt = null, legacyOrder = 0))
      dao.insertBudgetRow(BudgetRow(id = "leg_out", name = "Old Rent", kind = "out", amount = 10000.0, day = 28, start = "2026-11", createdAt = null, legacyOrder = 0))
      dao.insertBudgetRow(BudgetRow(id = "leg_loan", name = "Old Loan", kind = "out", amount = 3000.0, day = 15, start = "2026-11", months = 6, total = 18000.0, isLoan = true, createdAt = null, legacyOrder = 10000))
      dao.insertSha2aItem(Sha2aItem(id = "leg_sh_in", name = "Old Deposit", amount = 50000.0, side = "in", projectId = "sha2a", createdAt = null, legacyOrder = 0))
      dao.insertSha2aItem(Sha2aItem(id = "leg_sh_out", name = "Old Cement", amount = 8000.0, side = "out", projectId = "sha2a", createdAt = null, legacyOrder = 0))

      var clock = epochFor(2026, 11, 25, 10)
      fun nextTime(): Long {
        clock += 60_000L // +1 minute (crosses 100000ms modulo boundary every 2 calls!)
        return clock
      }

      // Add 3 incomes sequentially (unsorted names Z, A, M)
      repo.saveRow(BudgetRow(id = "in_z", name = "Zebra Bonus", kind = "in", amount = 5000.0, day = 25, start = "2026-11")) { nextTime() }
      repo.saveRow(BudgetRow(id = "in_a", name = "Alpha Freelance", kind = "in", amount = 3000.0, day = 2, start = "2026-11")) { nextTime() }
      val voiceIn = VoiceNlpEngine.parseOnDeviceRuleBased("Add income Middle Dividend 2500 on day 12")
      repo.saveRow(BudgetRow(id = "in_m", name = voiceIn.title, kind = voiceIn.kind, amount = voiceIn.amount, day = voiceIn.day, start = "2026-11")) { nextTime() }

      // Add 3 expenses sequentially with reverse days (29, 14, 1)
      repo.saveRow(BudgetRow(id = "out_z", name = "Zebra Bill", kind = "out", amount = 900.0, day = 29, start = "2026-11")) { nextTime() }
      repo.saveRow(BudgetRow(id = "out_a", name = "Alpha Water", kind = "out", amount = 300.0, day = 14, start = "2026-11")) { nextTime() }
      val voiceOut = VoiceNlpEngine.parseOnDeviceRuleBased("Add expense Middle Gym 1200 on day 1")
      repo.saveRow(BudgetRow(id = "out_m", name = voiceOut.title, kind = "out", amount = voiceOut.amount, day = voiceOut.day, start = "2026-11")) { nextTime() }

      // Add 2 loans sequentially
      repo.saveRow(BudgetRow(id = "ln_z", name = "Zebra Loan", kind = "out", amount = 1000.0, day = 20, start = "2026-11", months = 4, total = 4000.0, isLoan = true)) { nextTime() }
      repo.saveRow(BudgetRow(id = "ln_a", name = "Alpha Loan", kind = "out", amount = 2000.0, day = 5, start = "2026-11", months = 4, total = 8000.0, isLoan = true)) { nextTime() }

      // Add 3 project Money In and 3 project Money Out items across the 100000ms modulo boundary
      repo.saveSha2aItem(Sha2aItem(id = "p_in_1", name = "Zebra Fund", amount = 10000.0, side = "in", projectId = "sha2a")) { nextTime() }
      repo.saveSha2aItem(Sha2aItem(id = "p_in_2", name = "Alpha Fund", amount = 20000.0, side = "in", projectId = "sha2a")) { nextTime() }
      repo.saveSha2aItem(Sha2aItem(id = "p_in_3", name = "Middle Fund", amount = 30000.0, side = "in", projectId = "sha2a")) { nextTime() }

      repo.saveSha2aItem(Sha2aItem(id = "p_out_1", name = "Zebra Wood", amount = 4000.0, side = "out", projectId = "sha2a")) { nextTime() }
      repo.saveSha2aItem(Sha2aItem(id = "p_out_2", name = "Alpha Paint", amount = 5000.0, side = "out", projectId = "sha2a")) { nextTime() }
      repo.saveSha2aItem(Sha2aItem(id = "p_out_3", name = "Middle Glass", amount = 6000.0, side = "out", projectId = "sha2a")) { nextTime() }

      // Verify sorting across Home, All, Loans, Month, Projects, Export
      val allRows = dao.getAllBudgetRowsSync()
      val sortedIn = RecordOrder.sort(allRows.filter { it.kind == "in" })
      assertEquals(listOf("leg_in", "in_z", "in_a", "in_m"), sortedIn.map { it.id })

      val sortedOut = RecordOrder.sort(allRows.filter { it.kind != "in" })
      assertEquals(listOf("leg_out", "leg_loan", "out_z", "out_a", "out_m", "ln_z", "ln_a"), sortedOut.map { it.id })

      val sortedLoans = RecordOrder.sort(allRows.filter { it.isLoan })
      assertEquals(listOf("leg_loan", "ln_z", "ln_a"), sortedLoans.map { it.id })

      // Verify expanded loan monthly schedule remains strictly chronological!
      val loanSchedule = ScheduleEngine.computeSchedule(sortedLoans.last(), "2026-11", "2027-02")
      assertEquals(listOf("2026-11", "2026-12", "2027-01", "2027-02"), loanSchedule.list)

      // Verify Month tab entries for 2026-11 (created automatically on saveRow) are sorted by createdAt ASC, NOT day
      val monthOut = RecordOrder.sort(dao.getMonthEntriesForMonthSync("2026-11").filter { it.kind == "out" })
      assertEquals(listOf("out_z", "out_a", "out_m", "ln_z", "ln_a"), monthOut.map { it.sourceRowId })
      assertEquals(listOf(29, 14, 1, 20, 5), monthOut.map { it.day })

      // Verify Projects sorting (no 100000ms wrap-around!)
      val projItems = dao.getAllSha2aItemsSync()
      assertEquals(listOf("leg_sh_in", "p_in_1", "p_in_2", "p_in_3"), RecordOrder.sort(projItems.filter { it.side == "in" }).map { it.id })
      assertEquals(listOf("leg_sh_out", "p_out_1", "p_out_2", "p_out_3"), RecordOrder.sort(projItems.filter { it.side == "out" }).map { it.id })

      // Edit BOTH an old (legacy) record and a new record -> neither changes createdAt or position!
      val origInZ = dao.getBudgetRowById("in_z")!!
      val origLegIn = dao.getBudgetRowById("leg_in")!!
      repo.saveRow(origLegIn.copy(name = "Renamed Old Salary", amount = 45000.0)) { nextTime() }
      repo.saveRow(origInZ.copy(name = "Renamed Zebra", amount = 5500.0)) { nextTime() }

      val afterEditIn = RecordOrder.sort(dao.getAllBudgetRowsSync().filter { it.kind == "in" })
      assertEquals(listOf("leg_in", "in_z", "in_a", "in_m"), afterEditIn.map { it.id })
      assertNull(afterEditIn[0].createdAt)
      assertEquals(origInZ.createdAt, afterEditIn[1].createdAt)

      // Verify Export to Mariam image generation succeeds with both legacy and dated records
      val bmp = ImageExporter.generateBitmap(
        config = ExportConfig(
          fromMonth = "2026-11",
          toMonth = "2026-12",
          includeIncome = true,
          includeLeftover = true,
          includeExpenses = true,
          totalsAddUpAll = true,
          selectedRowIds = allRows.map { it.id }.toSet(),
          includeDayAndAmountCols = true,
          includeYearTotals = true
        ),
        allRows = allRows,
        rangeStart = "2026-11",
        rangeEnd = "2027-02"
      )
      assertTrue(bmp.width > 0 && bmp.height > 0)
    } finally {
      db.close()
    }
  }

  @Test
  fun test05_DateFormatting_EnglishAndArabicLocales_AndNullForLegacy() {
    val nov25 = epochFor(2026, 11, 25, 14)
    assertNull(RecordOrder.formatCreatedDate(null, Locale.US))
    assertEquals("25 Nov 2026", RecordOrder.formatCreatedDate(nov25, Locale.US))

    val arFormatted = RecordOrder.formatCreatedDate(nov25, Locale("ar"))
    assertNotNull(arFormatted)
    assertTrue(arFormatted!!.isNotBlank())
  }

  private fun sampleUiFixture(): Triple<List<BudgetRow>, List<MonthEntry>, List<Sha2aItem>> {
    val nov25 = epochFor(2026, 11, 25, 14)
    val rows = listOf(
      BudgetRow(id = "r_leg_in", name = "Legacy Income", kind = "in", amount = 10000.0, day = null, start = "2026-11", createdAt = null, legacyOrder = 0),
      BudgetRow(id = "r_new_in", name = "Fresh Income", kind = "in", amount = 2000.0, day = null, start = "2026-11", createdAt = nov25, legacyOrder = null),
      BudgetRow(id = "r_leg_out", name = "Legacy Rent", kind = "out", amount = 5000.0, day = 20, start = "2026-11", createdAt = null, legacyOrder = 0),
      BudgetRow(id = "r_new_out", name = "Fresh Coffee", kind = "out", amount = 500.0, day = 5, start = "2026-11", createdAt = nov25, legacyOrder = null),
      BudgetRow(id = "r_leg_loan", name = "Legacy Loan", kind = "out", amount = 1000.0, day = 10, start = "2026-11", months = 6, total = 6000.0, isLoan = true, createdAt = null, legacyOrder = 10000),
      BudgetRow(id = "r_new_loan", name = "Fresh Loan", kind = "out", amount = 1500.0, day = 2, start = "2026-11", months = 4, total = 6000.0, isLoan = true, createdAt = nov25, legacyOrder = null)
    )
    val monthEntries = listOf(
      MonthEntry(id = "m_leg_in", month = "2026-11", name = "Legacy Salary", kind = "in", amount = 10000.0, createdAt = null, legacyOrder = 0),
      MonthEntry(id = "m_new_in", month = "2026-11", name = "Fresh Bonus", kind = "in", amount = 2000.0, createdAt = nov25, legacyOrder = null),
      MonthEntry(id = "m_leg_out", month = "2026-11", name = "Legacy Internet", kind = "out", amount = 800.0, day = 22, createdAt = null, legacyOrder = 0),
      MonthEntry(id = "m_new_out", month = "2026-11", name = "Fresh Groceries", kind = "out", amount = 1500.0, day = 3, createdAt = nov25, legacyOrder = null)
    )
    val sha2aItems = listOf(
      Sha2aItem(id = "s_leg_in", name = "Legacy Deposit", amount = 20000.0, side = "in", projectId = "sha2a", createdAt = null, legacyOrder = 0),
      Sha2aItem(id = "s_new_in", name = "Fresh Deposit", amount = 5000.0, side = "in", projectId = "sha2a", createdAt = nov25, legacyOrder = null),
      Sha2aItem(id = "s_leg_out", name = "Legacy Tiles", amount = 8000.0, side = "out", projectId = "sha2a", createdAt = null, legacyOrder = 0),
      Sha2aItem(id = "s_new_out", name = "Fresh Paint", amount = 3000.0, side = "out", projectId = "sha2a", createdAt = nov25, legacyOrder = null)
    )
    return Triple(rows, monthEntries, sha2aItems)
  }

  @Test
  fun test06a_MonthScreen_DateDisplayAndDayPreserved() {
    val (rows, monthEntries, _) = sampleUiFixture()
    val settings = AppSettings(startMonth = "2026-10", endMonth = "2026-12", legacyArrangeDone = true)
    composeTestRule.setContent {
      MyApplicationTheme {
        MonthScreen(
          rows = rows,
          monthEntries = monthEntries,
          settings = settings,
          selectedMonth = "2026-11",
          onMonthChange = {},
          onEntryClick = {},
          onAddEntry = {},
          onToggleEntryPaid = { _, _ -> }
        )
      }
    }
    composeTestRule.onNodeWithTag("month_screen_list").performScrollToIndex(3)
    composeTestRule.onNodeWithTag("month_entry_date_m_new_in", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("month_entry_date_m_leg_in", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("month_screen_list").performScrollToIndex(4)
    composeTestRule.onNodeWithTag("month_entry_date_m_new_out", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("month_entry_date_m_leg_out", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("22", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithText("3", useUnmergedTree = true).assertExists()
  }

  @Test
  fun test06b_HomeOverviewScreen_DateDisplay() {
    val (rows, _, _) = sampleUiFixture()
    val settings = AppSettings(startMonth = "2026-10", endMonth = "2026-12", legacyArrangeDone = true)
    composeTestRule.setContent {
      MyApplicationTheme {
        HomeOverviewScreen(
          rows = rows,
          settings = settings,
          selectedMonth = "2026-11",
          onMonthChange = {},
          onAddIncome = {},
          onAddExpense = {},
          onEditRow = {},
          onEditCell = { _, _ -> },
          onNavigateToTab = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("home_income_date_r_new_in", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("home_income_date_r_leg_in", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_expense_date_r_new_out", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("home_expense_date_r_leg_out", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun test06c_AllTabGrid_CompactDateDisplayFitsCell() {
    val (rows, _, _) = sampleUiFixture()
    val settings = AppSettings(startMonth = "2026-10", endMonth = "2026-12", legacyArrangeDone = true)
    composeTestRule.setContent {
      MyApplicationTheme {
        HomeScreen(
          rows = rows,
          settings = settings,
          onAddIncome = {},
          onAddExpense = {},
          onEditRow = {},
          onEditCell = { _, _ -> },
          onExportMariam = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("all_row_date_r_new_in", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("all_row_date_r_leg_in", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("all_tab_grid_list").performScrollToIndex(9)
    composeTestRule.onNodeWithTag("all_row_date_r_new_out", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("all_row_date_r_leg_out", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun test06d_LoansScreen_DateDisplay() {
    val (rows, _, _) = sampleUiFixture()
    val settings = AppSettings(startMonth = "2026-10", endMonth = "2026-12", legacyArrangeDone = true)
    composeTestRule.setContent {
      MyApplicationTheme {
        LoansScreen(
          rows = rows,
          settings = settings,
          openLoanIds = emptySet(),
          onToggleExpanded = {},
          onTogglePaid = { _, _, _ -> },
          onAddLoan = {},
          onEditLoan = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("loan_date_r_new_loan", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("loan_date_r_leg_loan", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun test06e_ProjectsScreen_DateDisplay() {
    val (_, _, sha2aItems) = sampleUiFixture()
    composeTestRule.setContent {
      MyApplicationTheme {
        Sha2aScreen(
          items = sha2aItems,
          photos = emptyList(),
          onAddItem = {},
          onEditItem = {},
          onOpenPhoto = {},
          onAddPhotos = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("project_item_date_s_new_in", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("project_item_date_s_leg_in", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("project_item_date_s_new_out", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("project_item_date_s_leg_out", useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun test06f_ArrangeLegacyRecordsScreen_DragWithinSectionAndSave() {
    var savedMonthOrders: Map<String, Int>? = null
    val arrangeEntries = listOf(
      MonthEntry(id = "nov_a", month = "2026-11", name = "First Nov", kind = "in", amount = 1000.0, createdAt = null, legacyOrder = 0),
      MonthEntry(id = "nov_b", month = "2026-11", name = "Second Nov", kind = "in", amount = 2000.0, createdAt = null, legacyOrder = 1),
      MonthEntry(id = "oct_a", month = "2026-10", name = "First Nov", kind = "in", amount = 1000.0, createdAt = null, legacyOrder = 0)
    )
    composeTestRule.setContent {
      MyApplicationTheme {
        ArrangeLegacyRecordsScreen(
          monthEntries = arrangeEntries,
          budgetRows = emptyList(),
          projects = emptyList(),
          sha2aItems = emptyList(),
          onSave = { mOrders, _, _ -> savedMonthOrders = mOrders }
        )
      }
    }
    composeTestRule.onNodeWithTag("arrange_legacy_title").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drag_handle_month_in_2026-11_nov_a").performTouchInput {
      down(center)
      moveBy(Offset(0f, 180f))
      up()
    }
    composeTestRule.onNodeWithTag("arrange_legacy_save_btn").performClick()
    assertNotNull(savedMonthOrders)
    assertEquals(1, savedMonthOrders!!["nov_a"])
    assertEquals(0, savedMonthOrders!!["nov_b"])
    assertEquals(0, savedMonthOrders!!["oct_a"])
  }

  @Test
  fun test06g_RowEditorSheet_NoReorderControls_KeepsDayInput() {
    val (rows, _, _) = sampleUiFixture()
    composeTestRule.setContent {
      MyApplicationTheme {
        RowEditorSheet(
          row = rows[2],
          isNew = false,
          rangeStart = "2026-10",
          rangeEnd = "2026-12",
          onDismiss = {},
          onSave = {},
          onDelete = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("row_day_input").assertExists()
    composeTestRule.onNodeWithTag("move_up_btn").assertDoesNotExist()
    composeTestRule.onNodeWithTag("move_down_btn").assertDoesNotExist()
  }

  @Test
  fun test06h_MonthEntryEditorSheet_KeepsDayInput() {
    val (_, monthEntries, _) = sampleUiFixture()
    composeTestRule.setContent {
      MyApplicationTheme {
        MonthEntryEditorSheet(
          entry = monthEntries[2],
          isNew = false,
          onDismiss = {},
          onSave = {},
          onDelete = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("month_entry_day_input").assertExists()
  }

  @Test
  fun test06i_ExportMariamSheet_DateDisplay() {
    val (rows, _, _) = sampleUiFixture()
    composeTestRule.setContent {
      MyApplicationTheme {
        ExportMariamSheet(
          allRows = rows,
          rangeStart = "2026-10",
          rangeEnd = "2026-12",
          onDismiss = {}
        )
      }
    }
    composeTestRule.onNodeWithTag("export_in_date_r_new_in", useUnmergedTree = true).assertExists()
    composeTestRule.onNodeWithTag("export_in_date_r_leg_in", useUnmergedTree = true).assertDoesNotExist()
  }
}
