package com.example.data.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GoogleDriveBackupService {

  private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

  private const val FILE_NAME = "money-map-backup.json"
  private const val MIME_TYPE = "application/json"
  const val OAUTH_CLIENT_ID = com.example.BuildConfig.GOOGLE_OAUTH_CLIENT_ID

  suspend fun findBackupFile(accessToken: String, useAppData: Boolean = false, profileId: String? = null): String? = withContext(Dispatchers.IO) {
    try {
      val targetName = if (profileId != null) "moneymap_sync_$profileId.json" else FILE_NAME
      val query = if (useAppData) {
        "name='$targetName' and 'appDataFolder' in parents and trashed=false"
      } else {
        "name='$targetName' and trashed=false"
      }
      val spacesParam = if (useAppData) "&spaces=appDataFolder" else ""
      val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}$spacesParam&fields=files(id,name,modifiedTime)"
      val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $accessToken")
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext null
        val body = response.body?.string() ?: return@withContext null
        val json = JSONObject(body)
        val files = json.optJSONArray("files")
        if (files != null && files.length() > 0) {
          return@withContext files.getJSONObject(0).optString("id")
        }
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun uploadBackup(
    accessToken: String,
    jsonContent: String,
    useAppData: Boolean = false,
    profileId: String? = null
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val fileName = if (profileId != null) "moneymap_sync_$profileId.json" else FILE_NAME
      val existingFileId = findBackupFile(accessToken, useAppData, profileId)
      if (existingFileId != null) {
        // Update existing file
        val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
        val requestBody = jsonContent.toRequestBody(MIME_TYPE.toMediaType())
        val request = Request.Builder()
          .url(uploadUrl)
          .patch(requestBody)
          .addHeader("Authorization", "Bearer $accessToken")
          .build()

        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            Result.success("Updated existing backup on Google Drive.")
          } else {
            Result.failure(Exception("Drive upload failed: ${response.code}"))
          }
        }
      } else {
        // Create new multipart upload
        val boundary = "====MoneyMapBackupBoundary===="
        val parentsClause = if (useAppData) ""","parents":["appDataFolder"]""" else ""
        val metadata = """{"name":"$fileName","mimeType":"$MIME_TYPE"$parentsClause}"""

        val multipartBody = buildString {
          append("--$boundary\r\n")
          append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
          append(metadata)
          append("\r\n--$boundary\r\n")
          append("Content-Type: $MIME_TYPE\r\n\r\n")
          append(jsonContent)
          append("\r\n--$boundary--")
        }

        val requestBody = multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val request = Request.Builder()
          .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
          .post(requestBody)
          .addHeader("Authorization", "Bearer $accessToken")
          .build()

        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            Result.success("Created new backup on Google Drive.")
          } else {
            Result.failure(Exception("Drive upload failed: ${response.code}"))
          }
        }
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun downloadBackup(
    accessToken: String,
    useAppData: Boolean = false,
    profileId: String? = null
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val fileId = findBackupFile(accessToken, useAppData, profileId)
        ?: return@withContext Result.failure(Exception("No backup found on Google Drive."))
      val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
      val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer $accessToken")
        .build()

      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
          val content = response.body?.string() ?: return@withContext Result.failure(Exception("Empty backup file received."))
          Result.success(content)
        } else {
          Result.failure(Exception("Drive download failed: ${response.code}"))
        }
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
