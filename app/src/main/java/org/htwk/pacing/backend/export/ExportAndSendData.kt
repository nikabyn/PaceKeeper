package org.htwk.pacing.backend.export

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import org.htwk.pacing.ui.screens.UserProfileViewModel
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// zentrale Server-Konfiguration
private const val SERVER_URL = "https://pacekeeper.pixelpioniere.de/receive/index.php"
private const val SERVER_SECRET = "DN9d82ohd20iooinlknceOI"

/**
 * Sendet die bestehende lokale Datenbank an den Server.
 */
suspend fun exportAndSendData(
    context: Context,
    viewModel: UserProfileViewModel,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Boolean = withContext(ioDispatcher) {
    try {
        // Datenbank-Instanz direkt abrufen


        val cacheDir = context.cacheDir


        val userProfile = viewModel.profile.firstOrNull() // Flow sammeln

        val userId = userProfile?.userId ?: "unknown"
        Log.d("USER ID", userId)

        // ZIP-Dateinamen erstellen (mit UserID und Zeitstempel)
        val zipFileName = "pacing_database_export_userid_${userId}_${System.currentTimeMillis()}.zip"
        val zipFile = File(cacheDir, zipFileName)
        val dbFile = context.getDatabasePath("pacing.db")

        // Datenbank zippen
        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                dbFile.inputStream().use { fis ->
                    val entry = ZipEntry(zipFileName.removeSuffix(".zip"))
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }


        Log.d("ExportAndSend", "Database exported to: ${zipFile.absolutePath}")

        val success = sendFileToServer(zipFile, SERVER_URL, ioDispatcher)

        // 5. Temporäre ZIP-Datei löschen
        if (zipFile.exists()) {
            val deleted = zipFile.delete()
            if (deleted) {
                Log.d("ExportAndSend", "Temporary ZIP file deleted successfully")
            } else {
                Log.e("ExportAndSend", "Failed to delete temporary ZIP file: ${zipFile.absolutePath}")
            }
        }

        if (success) Log.d("ExportAndSend", "Zipped database successfully sent to server")
        success
    } catch (e: Exception) {
        Log.e("ExportAndSend", "Error sending zipped database: ${e.message}", e)
        false
    }
}

/**
 * Generische Funktion zum Senden einer Datei an den Server.
 */
private suspend fun sendFileToServer(
    file: File,
    serverUrl: String,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): Boolean = withContext(ioDispatcher) {
    try {
        val connection = (URL(serverUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/zip")
            setRequestProperty("X-File-Name", file.name)
            Log.d("DATEINAME",file.name)
            setRequestProperty("X-App-Secret", SERVER_SECRET)
            doOutput = true
            connectTimeout = 30000
            readTimeout = 30000
        }

        file.inputStream().use { input ->
            connection.outputStream.use { output ->
                input.copyTo(output)
            }
        }

        val responseCode = connection.responseCode
        val success = responseCode in 200..299
        Log.d("ExportAndSend", "Server response code: $responseCode")

        if (!success) {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "No error details"
            Log.e("ExportAndSend", "Server error: $errorStream")
        }

        connection.disconnect()
        success
    } catch (e: Exception) {
        Log.e("ExportAndSend", "Error sending file to server: ${e.message}", e)
        false
    }
}
