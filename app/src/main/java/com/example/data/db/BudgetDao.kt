package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.AppSettings
import com.example.data.model.BudgetRow
import com.example.data.model.PhotoReceipt
import com.example.data.model.Sha2aItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

  @Query("SELECT * FROM budget_rows ORDER BY sortOrder ASC, name ASC")
  fun getAllBudgetRows(): Flow<List<BudgetRow>>

  @Query("SELECT * FROM budget_rows WHERE id = :id LIMIT 1")
  suspend fun getBudgetRowById(id: String): BudgetRow?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBudgetRow(row: BudgetRow)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBudgetRows(rows: List<BudgetRow>)

  @Query("DELETE FROM budget_rows WHERE id = :id")
  suspend fun deleteBudgetRow(id: String)

  @Query("DELETE FROM budget_rows")
  suspend fun clearBudgetRows()

  @Query("SELECT * FROM projects ORDER BY sortOrder ASC, name ASC")
  fun getAllProjects(): Flow<List<com.example.data.model.ProjectEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProject(project: com.example.data.model.ProjectEntity)

  @Query("DELETE FROM projects WHERE id = :id")
  suspend fun deleteProject(id: String)

  @Query("SELECT * FROM sha2a_items ORDER BY sortOrder ASC, name ASC")
  fun getAllSha2aItems(): Flow<List<Sha2aItem>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSha2aItem(item: Sha2aItem)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSha2aItems(items: List<Sha2aItem>)

  @Query("DELETE FROM sha2a_items WHERE id = :id")
  suspend fun deleteSha2aItem(id: String)

  @Query("DELETE FROM sha2a_items")
  suspend fun clearSha2aItems()

  @Query("SELECT * FROM photo_receipts ORDER BY createdAt DESC")
  fun getAllPhotoReceipts(): Flow<List<PhotoReceipt>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPhotoReceipt(photo: PhotoReceipt)

  @Query("DELETE FROM photo_receipts WHERE id = :id")
  suspend fun deletePhotoReceipt(id: String)

  @Query("DELETE FROM photo_receipts")
  suspend fun clearPhotoReceipts()

  @Query("SELECT * FROM app_settings WHERE id = 'main' LIMIT 1")
  fun getSettings(): Flow<AppSettings?>

  @Query("SELECT * FROM app_settings WHERE id = 'main' LIMIT 1")
  suspend fun getSettingsSync(): AppSettings?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSettings(settings: AppSettings)

  @Transaction
  suspend fun replaceAllData(
    settings: AppSettings,
    rows: List<BudgetRow>,
    sha2a: List<Sha2aItem>,
    photos: List<PhotoReceipt>
  ) {
    clearBudgetRows()
    clearSha2aItems()
    clearPhotoReceipts()
    insertSettings(settings)
    insertBudgetRows(rows)
    insertSha2aItems(sha2a)
    photos.forEach { insertPhotoReceipt(it) }
  }
}
