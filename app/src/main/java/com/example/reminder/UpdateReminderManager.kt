package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object UpdateReminderManager {

  private const val PREFS_NAME = "p_money_notification_prefs"
  private const val KEY_ENABLED = "update_reminder_enabled"
  private const val KEY_DAY_OF_WEEK = "update_reminder_day_of_week" // Calendar.SUNDAY .. Calendar.SATURDAY
  private const val KEY_HOUR = "update_reminder_hour" // 0..23
  private const val KEY_MINUTE = "update_reminder_minute" // 0..59
  private const val KEY_EDGE_LIGHTING = "edge_lighting_enabled"
  private const val KEY_BILL_REMINDERS = "bill_reminders_enabled"

  const val ACTION_WEEKLY_UPDATE_ALARM = "com.example.ACTION_WEEKLY_UPDATE_ALARM"
  const val ACTION_OPEN_UPDATE_CONFIG = "com.example.ACTION_OPEN_UPDATE_CONFIG"
  const val EXTRA_OPEN_SECTION = "EXTRA_OPEN_SECTION"
  const val EXTRA_TRIGGER_EDGE_LIGHTING = "EXTRA_TRIGGER_EDGE_LIGHTING"
  const val SECTION_NOTIFICATION_SETUP = "notification_setup"

  private const val ALARM_REQUEST_CODE = 9001
  private const val NOTIFICATION_ID = 9002

  // Default: Every Thursday at 1:00 PM (13:00)
  fun isUpdateReminderEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_ENABLED, true)
  }

  fun setUpdateReminderEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
      .apply()
    if (enabled) {
      scheduleWeeklyUpdateReminder(context)
    } else {
      cancelWeeklyUpdateReminder(context)
    }
  }

  fun getReminderDayOfWeek(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getInt(KEY_DAY_OF_WEEK, Calendar.THURSDAY)
  }

  fun setReminderDayOfWeek(context: Context, dayOfWeek: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_DAY_OF_WEEK, dayOfWeek)
      .apply()
    scheduleWeeklyUpdateReminder(context)
  }

  fun getReminderHour(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getInt(KEY_HOUR, 13) // 1:00 PM default
  }

  fun getReminderMinute(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getInt(KEY_MINUTE, 0)
  }

  fun setReminderTime(context: Context, hour: Int, minute: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_HOUR, hour)
      .putInt(KEY_MINUTE, minute)
      .apply()
    scheduleWeeklyUpdateReminder(context)
  }

  fun isEdgeLightingEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_EDGE_LIGHTING, true)
  }

  fun setEdgeLightingEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_EDGE_LIGHTING, enabled)
      .apply()
  }

  fun isBillRemindersEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_BILL_REMINDERS, true)
  }

  fun setBillRemindersEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_BILL_REMINDERS, enabled)
      .apply()
  }

  fun getNextTriggerMillis(context: Context): Long {
    val dayOfWeek = getReminderDayOfWeek(context)
    val hour = getReminderHour(context)
    val minute = getReminderMinute(context)

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      set(Calendar.DAY_OF_WEEK, dayOfWeek)
    }

    // If target is in the past or within 1 minute, schedule for next week
    if (target.timeInMillis <= now.timeInMillis + 60_000) {
      target.add(Calendar.WEEK_OF_YEAR, 1)
    }

    return target.timeInMillis
  }

  fun getNextScheduledFormatted(context: Context): String {
    val millis = getNextTriggerMillis(context)
    val df = SimpleDateFormat("EEEE, MMMM d 'at' h:mm a", Locale.US)
    return df.format(Date(millis))
  }

  fun getDayName(dayOfWeek: Int): String {
    return when (dayOfWeek) {
      Calendar.SUNDAY -> "Sunday"
      Calendar.MONDAY -> "Monday"
      Calendar.TUESDAY -> "Tuesday"
      Calendar.WEDNESDAY -> "Wednesday"
      Calendar.THURSDAY -> "Thursday"
      Calendar.FRIDAY -> "Friday"
      Calendar.SATURDAY -> "Saturday"
      else -> "Thursday"
    }
  }

  fun scheduleWeeklyUpdateReminder(context: Context) {
    if (!isUpdateReminderEnabled(context)) return

    try {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val triggerAtMillis = getNextTriggerMillis(context)

      val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
        action = ACTION_WEEKLY_UPDATE_ALARM
        putExtra("EXTRA_TITLE", "P Money Update Reminder")
        putExtra("EXTRA_MESSAGE", "Weekly update reminder! Tap to check for updates or download the latest P Money APK.")
        putExtra("EXTRA_ID", NOTIFICATION_ID)
        putExtra(EXTRA_OPEN_SECTION, SECTION_NOTIFICATION_SETUP)
        putExtra(EXTRA_TRIGGER_EDGE_LIGHTING, true)
      }

      val pendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
          alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
          alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
      } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
      }

      Log.d("UpdateReminder", "Scheduled weekly update reminder for ${Date(triggerAtMillis)}")
    } catch (e: Exception) {
      Log.e("UpdateReminder", "Error scheduling reminder", e)
    }
  }

  fun cancelWeeklyUpdateReminder(context: Context) {
    try {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
        action = ACTION_WEEKLY_UPDATE_ALARM
      }
      val pendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )
      alarmManager.cancel(pendingIntent)
    } catch (_: Exception) {}
  }

  fun triggerTestNotification(context: Context) {
    ReminderManager.showNotification(
      context = context,
      notificationId = NOTIFICATION_ID,
      title = "P Money Update Reminder",
      message = "Weekly update reminder! Tap here to open Notification Setup & download latest APK.",
      openSection = SECTION_NOTIFICATION_SETUP,
      triggerEdgeLighting = true
    )
  }
}
