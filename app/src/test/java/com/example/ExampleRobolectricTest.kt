package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BudgetRow
import com.example.util.ArabicNumberHelper
import com.example.util.ScheduleEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Money map", appName)
  }

  @Test
  fun testArabicNumberParsing() {
    assertEquals(5000.0, ArabicNumberHelper.parseNumber("٥٠٠٠")!!, 0.001)
    assertEquals(1250.5, ArabicNumberHelper.parseNumber("١٢٥٠.٥")!!, 0.001)
    assertEquals(44700.0, ArabicNumberHelper.parseNumber("44,700")!!, 0.001)
    assertEquals("44,700", ArabicNumberHelper.fmt(44700.0))
    assertEquals("−5,000", ArabicNumberHelper.fmt(-5000.0))
    assertEquals("25th", ArabicNumberHelper.ord(25))
    assertEquals("1st", ArabicNumberHelper.ord(1))
  }

  @Test
  fun testScheduleCalculation() {
    // Gam3ya 50 loan: 5000/month, total 50000, starting 2026-08
    val gam3ya = BudgetRow(
      id = "gam3ya-50",
      name = "Gam3ya 50",
      kind = "out",
      amount = 5000.0,
      day = 28,
      start = "2026-08",
      every = 1,
      months = null,
      total = 50000.0,
      stepsJson = "[]",
      overridesJson = "{}",
      isLoan = true,
      paidJson = """{"2026-08":true,"2026-09":true,"2026-10":true}"""
    )

    val sc = ScheduleEngine.computeSchedule(gam3ya, "2026-11", "2027-12")
    assertEquals(10, sc.list.size)
    assertEquals(50000.0, sc.planned, 0.01)
    assertEquals("2026-08", sc.first)
    assertEquals("2027-05", sc.last)

    val loanInfo = ScheduleEngine.loanInfo(gam3ya, "2026-11", "2027-12")
    assertEquals(50000.0, loanInfo.total, 0.01)
    assertEquals(15000.0, loanInfo.paid, 0.01) // 3 months ticked
    assertEquals(35000.0, loanInfo.remaining, 0.01)
  }

  @Test
  fun testStepAndOverrideCalculation() {
    // Rent with step increase from 2027-07 to 6000 and override for 2026-12
    val rent = BudgetRow(
      id = "rent",
      name = "Rent",
      kind = "in",
      amount = 5500.0,
      day = 1,
      start = "2026-08",
      every = 1,
      months = null,
      total = null,
      stepsJson = """[{"from":"2027-07","amount":6000.0}]""",
      overridesJson = """{"2026-12":7000.0}"""
    )

    assertEquals(5500.0, ScheduleEngine.amt(rent, "2026-11", "2026-11", "2027-12"), 0.01)
    assertEquals(7000.0, ScheduleEngine.amt(rent, "2026-12", "2026-11", "2027-12"), 0.01) // override
    assertEquals(5500.0, ScheduleEngine.amt(rent, "2027-01", "2026-11", "2027-12"), 0.01)
    assertEquals(6000.0, ScheduleEngine.amt(rent, "2027-07", "2026-11", "2027-12"), 0.01) // step
    assertEquals(6000.0, ScheduleEngine.amt(rent, "2027-12", "2026-11", "2027-12"), 0.01)
  }

  @Test
  fun testBiometricSecurityPinAndState() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // Initially no PIN and not assigned
    com.example.security.BiometricAuthManager.unassignFingerprint(context)
    assertEquals(false, com.example.security.BiometricAuthManager.isFingerprintAssigned(context))

    // Set backup PIN
    com.example.security.BiometricAuthManager.setBackupPin(context, "1234")
    assertEquals(true, com.example.security.BiometricAuthManager.hasBackupPin(context))
    assertEquals(true, com.example.security.BiometricAuthManager.verifyBackupPin(context, "1234"))
    assertEquals(false, com.example.security.BiometricAuthManager.verifyBackupPin(context, "0000"))
    assertEquals(false, com.example.security.BiometricAuthManager.verifyBackupPin(context, "12345"))
  }
}
