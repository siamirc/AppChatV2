package com.example

import java.io.OutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

object FileUploader {
    fun uploadFile(context: Context, fileUri: Uri, onProgress: (Float) -> Unit): String? {
        val contentResolver = context.contentResolver
        var fileName = "file"
        var fileSize = 0L
        
        try {
            contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("FileUploader", "Error querying file info", e)
        }

        val boundary = "Boundary-" + System.currentTimeMillis()
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        try {
            val url = URL("https://tmpfiles.org/api/v1/upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.doInput = true
            conn.doOutput = true
            conn.useCaches = false
            conn.requestMethod = "POST"
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val outputStream: OutputStream = conn.outputStream
            
            // Write boundary headers
            val header = "$twoHyphens$boundary$lineEnd" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd" +
                    "Content-Type: ${contentResolver.getType(fileUri) ?: "application/octet-stream"}$lineEnd$lineEnd"
            outputStream.write(header.toByteArray(Charsets.UTF_8))

            // Write file content
            val fileInputStream = contentResolver.openInputStream(fileUri) ?: return null
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesWritten = 0L
            
            fileInputStream.use { fis ->
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesWritten += bytesRead
                    if (fileSize > 0) {
                        onProgress(totalBytesWritten.toFloat() / fileSize)
                    }
                }
            }

            outputStream.write(lineEnd.toByteArray(Charsets.UTF_8))
            
            // End boundary
            val footer = "$twoHyphens$boundary$twoHyphens$lineEnd"
            outputStream.write(footer.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                // Parse tmpfiles.org JSON response:
                // {"status":"success","data":{"url":"https://tmpfiles.org/1234/filename.ext"}}
                // We want to turn "https://tmpfiles.org/1234/filename.ext" into "https://tmpfiles.org/dl/1234/filename.ext" for direct link!
                val jsonRegex = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val match = jsonRegex.find(responseText)
                if (match != null) {
                    val rawUrl = match.groupValues[1]
                    // Replace "tmpfiles.org/" with "tmpfiles.org/dl/"
                    return rawUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("FileUploader", "Upload failed", e)
        }
        return null
    }
}
