package com.example

import com.example.ui.AppTab
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testAppTabValues() {
    assertEquals("Home", AppTab.HOME.title)
    assertEquals("All", AppTab.ALL.title)
    assertEquals("Month", AppTab.MONTH.title)
    assertEquals("Projects", AppTab.PROJECTS.title)
    assertEquals("Loans", AppTab.LOANS.title)
  }
}
