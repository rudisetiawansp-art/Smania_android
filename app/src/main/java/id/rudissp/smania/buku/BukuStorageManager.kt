package id.rudissp.smania.buku

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object BukuStorageManager {

    private const val FOLDER_NAME = "buku_offline"

    /**
     * Mendapatkan direktori penyimpanan lokal buku offline di internal storage.
     */
    fun getBukuDir(context: Context): File {
        val dir = File(context.filesDir, FOLDER_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Menghasilkan nama file yang unik dan aman dari slug atau URL buku.
     */
    fun getFileName(slug: String, pdfUrl: String): String {
        val cleanSlug = slug.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return if (cleanSlug.isNotEmpty()) {
            "buku_$cleanSlug.pdf"
        } else {
            val hash = md5(pdfUrl)
            "buku_$hash.pdf"
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    /**
     * Memeriksa apakah file buku sudah pernah diunduh dan tersimpan di lokal.
     */
    fun isBookDownloaded(context: Context, slug: String, pdfUrl: String): Boolean {
        val file = getDownloadedFile(context, slug, pdfUrl)
        return file != null && file.exists() && file.length() > 0
    }

    /**
     * Mengembalikan file buku lokal jika tersedia, atau null jika belum diunduh.
     */
    fun getDownloadedFile(context: Context, slug: String, pdfUrl: String): File? {
        val fileName = getFileName(slug, pdfUrl)
        val file = File(getBukuDir(context), fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Mendapatkan ukuran file buku lokal dalam byte (0 jika belum diunduh).
     */
    fun getDownloadedFileSize(context: Context, slug: String, pdfUrl: String): Long {
        val file = getDownloadedFile(context, slug, pdfUrl)
        return file?.length() ?: 0L
    }

    /**
     * Menghapus file buku dari penyimpanan lokal.
     */
    fun deleteDownloadedBook(context: Context, slug: String, pdfUrl: String): Boolean {
        val fileName = getFileName(slug, pdfUrl)
        val file = File(getBukuDir(context), fileName)
        val tempFile = File(getBukuDir(context), "$fileName.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Menghitung total ukuran file yang diunduh dalam byte.
     */
    fun getTotalCacheSize(context: Context): Long {
        val dir = getBukuDir(context)
        var totalSize = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            if (f.isFile && f.name.endsWith(".pdf")) {
                totalSize += f.length()
            }
        }
        return totalSize
    }

    /**
     * Menghitung jumlah buku yang sudah tersimpan secara offline.
     */
    fun getDownloadedBookCount(context: Context): Int {
        val dir = getBukuDir(context)
        val files = dir.listFiles() ?: return 0
        return files.count { it.isFile && it.name.endsWith(".pdf") && it.length() > 0 }
    }

    /**
     * Menghapus semua file buku yang tersimpan di penyimpanan lokal.
     */
    fun clearAllCache(context: Context): Boolean {
        val dir = getBukuDir(context)
        val files = dir.listFiles() ?: return true
        var success = true
        for (f in files) {
            if (f.isFile) {
                if (!f.delete()) {
                    success = false
                }
            }
        }
        return success
    }

    /**
     * Mengunduh file buku dari [pdfUrl] dan menyimpannya secara permanen ke [FOLDER_NAME].
     */
    suspend fun downloadBook(
        context: Context,
        slug: String,
        pdfUrl: String,
        onProgress: (progress: Float, currentBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val fileName = getFileName(slug, pdfUrl)
        val targetFile = File(getBukuDir(context), fileName)

        // Jika sudah ada dan valid, langsung kembalikan
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext Result.success(targetFile)
        }

        val tempFile = File(getBukuDir(context), "$fileName.tmp")

        try {
            val url = URL(pdfUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val fileLength = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    connection.contentLengthLong
                } else {
                    connection.contentLength.toLong()
                }

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total: Long = 0
                        var count: Int

                        while (input.read(buffer).also { count = it } != -1) {
                            total += count
                            output.write(buffer, 0, count)

                            if (fileLength > 0) {
                                val progress = total.toFloat() / fileLength.toFloat()
                                withContext(Dispatchers.Main) {
                                    onProgress(progress, total, fileLength)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    onProgress(-1f, total, 0L)
                                }
                            }
                        }
                    }
                }

                if (tempFile.renameTo(targetFile)) {
                    Result.success(targetFile)
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    Result.success(targetFile)
                }
            } else {
                if (tempFile.exists()) tempFile.delete()
                Result.failure(Exception("Gagal mengunduh: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Format ukuran byte ke string readable (KB/MB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024f * 1024f)
        return if (mb < 0.1f) {
            val kb = bytes / 1024f
            String.format("%.1f KB", kb)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}