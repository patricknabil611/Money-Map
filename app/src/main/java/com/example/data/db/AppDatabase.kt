package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.PhotoReceipt
import com.example.data.model.Sha2aItem
import com.example.data.model.ProjectEntity

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
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
