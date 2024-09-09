package com.saverio.pdfviewer

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.math.min

object RealPathUtil {
    private const val TAG = "[RealPathUtil plugin]: "


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return ("com.google.android.apps.photos.content" == uri.authority || "com.google.android.apps.photos.contentprovider" == uri.authority)
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Drive.
     */
    private fun isGoogleDriveUri(uri: Uri): Boolean {
        return "com.google.android.apps.docs.storage" == uri.authority || "com.google.android.apps.docs.storage.legacy" == uri.authority
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )

        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            } else if (!cursor!!.moveToFirst()) {
                return getMediaStore(context, uri)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @return The value of the _data column, which is typically a file path.
     */
    private fun getMediaStore(
        context: Context, uri: Uri?
    ): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, null, null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndexes = arrayOf(
                    cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                )
                return columnIndexes.joinToString("-") { cursor.getString(it) }
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * Get content:// from segment list
     * In the new Uri Authority of Google Photos, the last segment is not the content:// anymore
     * So let's iterate through all segments and find the content uri!
     *
     * @param segments The list of segment
     */
    private fun getContentFromSegments(segments: List<String>): String {
        var contentPath = ""

        for (item in segments) {
            if (item.startsWith("content://")) {
                contentPath = item
                break
            }
        }

        return contentPath
    }

    /**
     * Check if a file exists on device
     *
     * @param filePath The absolute file path
     */
    private fun fileExists(filePath: String): Boolean {
        val file = File(filePath)

        return file.exists()
    }

    /**
     * Get full file path from external storage
     *
     * @param pathData The storage type and the relative path
     */
    private fun getPathFromExtSD(pathData: Array<String>): String {
        val type = pathData[0]
        var relativePath = ""
        if (pathData.size > 1) {
            relativePath = "/" + pathData[1]
        }
        var fullPath: String

        // on my Sony devices (4.4.4 & 5.1.1), `type` is a dynamic string
        // something like "71F8-2C0A", some kind of unique id per storage
        // don't know any API that can get the root path of that storage based on its id.
        //
        // so no "primary" type, but let the check here for other devices
        if ("primary".equals(type, ignoreCase = true)) {
            fullPath = Environment.getExternalStorageDirectory().toString() + relativePath
            if (fileExists(fullPath)) {
                return fullPath
            }
        }

        // Environment.isExternalStorageRemovable() is `true` for external and internal storage
        // so we cannot relay on it.
        //
        // instead, for each possible path, check if file exists
        // we'll start with secondary storage as this could be our (physically) removable sd card
        fullPath = (System.getenv("SECONDARY_STORAGE") ?: "") + relativePath
        if (fileExists(fullPath)) {
            return fullPath
        }

        fullPath = (System.getenv("EXTERNAL_STORAGE") ?: "") + relativePath
        if (fileExists(fullPath)) {
            return fullPath
        }

        fullPath = "/storage/$type$relativePath"
        if (fileExists(fullPath)) {
            return fullPath
        }

        return fullPath
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.<br></br>
     * <br></br>
     * Callers should check whether the path is local before assuming it
     * represents a local file.
     *
     * @param context The context.
     * @param uri The Uri to query.
     */
    fun getRealPath(context: Context, uri: Uri): String? {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val fullPath = getPathFromExtSD(split)
                return if (fullPath !== "") {
                    fullPath
                } else {
                    null
                }
            } else if (isDownloadsDocument(uri)) {
                // thanks to https://github.com/hiddentao/cordova-plugin-filepath/issues/34#issuecomment-430129959
                var cursor: Cursor? = null
                try {
                    cursor = context.contentResolver.query(
                        uri,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )
                    if (cursor != null && cursor.moveToFirst()) {
                        val fileName = cursor.getString(0)
                        val folders = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ).absolutePath
                        val path = "$folders/$fileName"
                        if (!TextUtils.isEmpty(path)) {
                            return path
                        }
                    }
                } finally {
                    cursor?.close()
                }
                //
                val id = DocumentsContract.getDocumentId(uri)
                try {
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), id.toLong()
                    )
                    return getDataColumn(context, contentUri, null, null)
                } catch (e: NumberFormatException) {
                    //In Android 8 and Android P the id is not a number
                    return uri.path!!.replaceFirst("^/document/raw:".toRegex(), "")
                        .replaceFirst("^raw:".toRegex(), "")
                }
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).filter { it.isNotEmpty() }.toTypedArray()
                val type = split[0]
                val contentUri: Uri? = when (type) {
                    "image" -> {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    "video" -> {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    "audio" -> {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    else -> {
                        MediaStore.Files.getContentUri("external")
                    }
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    split[1]
                )
                var realPath: String? = getDataColumn(context, contentUri, selection, selectionArgs)
                if (realPath == null) {
                    realPath = getMediaStore(context, contentUri)
                }
                if (realPath == null) {
                    realPath = getMediaStore(context, uri)
                }
                return realPath
            } else if (isGoogleDriveUri(uri)) {
                return getDriveFilePath(uri, context)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                val contentPath = getContentFromSegments(uri.pathSegments)
                return if (contentPath !== "") {
                    getRealPath(context, Uri.parse(contentPath))
                } else {
                    null
                }
            }

            if (isGoogleDriveUri(uri)) {
                return getDriveFilePath(uri, context)
            }

            return try {
                getDataColumn(context, uri, null, null)
            } catch (ex: Exception) {
                getMediaStore(context, uri)
            }
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }

        return null
    }

    @SuppressLint("Recycle")
    private fun getDriveFilePath(uri: Uri, context: Context): String {
        val returnCursor = context.contentResolver.query(uri, null, null, null, null)
        /*
         * Get the column indexes of the data in the Cursor,
         *     * move to the first row in the Cursor, get the data,
         *     * and display it.
         * */
        val nameIndex = returnCursor!!.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor.moveToFirst()
        val name = (returnCursor.getString(nameIndex))
        val file = File(context.cacheDir, name)
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(file)
            var read: Int
            val maxBufferSize = 1 * 1024 * 1024
            val bytesAvailable = inputStream!!.available()

            //int bufferSize = 1024;
            val bufferSize = min(bytesAvailable.toDouble(), maxBufferSize.toDouble())
                .toInt()

            val buffers = ByteArray(bufferSize)
            while ((inputStream.read(buffers).also { read = it }) != -1) {
                outputStream.write(buffers, 0, read)
            }
            // Log.e("File Size","Size " + file.length());
            inputStream.close()
            outputStream.close()
            // Log.e("File Path","Path " + file.getPath());
            // Log.e("File Size","Size " + file.length());
        } catch (e: Exception) {
            println("Exception $e")
        }
        return file.path
    }

    suspend fun computeHash(context: Context, uri: Uri, filename: String?): String? {
        return try {
            val digester = MessageDigest.getInstance("SHA256")
            if (!filename.isNullOrEmpty()) {
                digester.update(filename.toByteArray())
            }
            // Perform IO operations on the IO dispatcher
            withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                inputStream.use { stream ->
                    val buffer = ByteArray(1024 * 1024)
                    val amountRead = stream.read(buffer)
                    if (amountRead == -1) return@withContext null
                    digester.update(buffer, 0, amountRead)
                }
            }
            val hash = String.format("%032x", BigInteger(1, digester.digest()))
            hash
        } catch (e: NoSuchAlgorithmException) {
            Log.e("util.kt", "NoSuchAlgorithmException: computeHash failed!", e)
            null
        } catch (e: IOException) {
            Log.e("util.kt", "IOException: computeHash failed!", e)
            null
        } catch (e: SecurityException) {
            Log.e("util.kt", "SecurityException: computeHash failed!", e)
            null
        } catch (e: Throwable) {
            Log.e("util.kt", "computeHash failed!", e)
            null
        }
    }
}