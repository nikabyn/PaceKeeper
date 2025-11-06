package org.htwk.pacing.backend.export

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.htwk.pacing.backend.database.PacingDatabase
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// zentrale Server-Konfiguration
private const val SERVER_URL = "https://pacekeeper.pixelpioniere.de/receive/index.php"

/**
 * Sendet die bestehende lokale Datenbank an den Server.
 */
suspend fun sendDatabaseToServer(context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val dbFile = context.getDatabasePath("pacing.db")
        if (!dbFile.exists()) {
            Log.e("ExportAndSend", "Database file not found: ${dbFile.absolutePath}")
            return@withContext false
        }
        Log.d("ExportAndSend", "Sending database file: ${dbFile.absolutePath} (${dbFile.length()} bytes)")
        val success = sendFileToServer(dbFile, SERVER_URL)
        if (success) Log.d("ExportAndSend", "Database successfully sent to server")
        success
    } catch (e: Exception) {
        Log.e("ExportAndSend", "Error sending database: ${e.message}", e)
        false
    }
}

/**
 * Exportiert alle Datenbanktabellen als ZIP und sendet sie.
 */
suspend fun exportAllDatabasesAndSend(
    db: PacingDatabase,
    context: Context
): Boolean = withContext(Dispatchers.IO) {
    try {
        val cacheDir = context.cacheDir
        val zipFile = File(cacheDir, "pacing_database_export_${System.currentTimeMillis()}.zip")

        FileOutputStream(zipFile).use { fos ->
            exportAllAsZip(db, fos)
        }

        Log.d("ExportAndSend", "Database exported to: ${zipFile.absolutePath}")
        val success = sendFileToServer(zipFile, SERVER_URL)

        if (zipFile.exists()) {
            zipFile.delete()
            Log.d("ExportAndSend", "Temporary ZIP file deleted")
        }

        success
    } catch (e: Exception) {
        Log.e("ExportAndSend", "Error during export and send: ${e.message}", e)
        false
    }
}

/**
 * Generische Funktion zum Senden einer Datei an den Server.
 */
private suspend fun sendFileToServer(file: File, serverUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val connection = (URL(serverUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/zip")
            setRequestProperty("X-File-Name", file.name)
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
