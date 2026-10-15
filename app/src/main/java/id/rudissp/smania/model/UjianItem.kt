package id.rudissp.smania.model

/**
 * Data class representing an exam (ujian) fetched from the server.
 * Fields match the JSON produced by versiweb/api/exams.php.
 */
data class UjianItem(
    val id: String,
    val judulUjian: String,
    val mapel: String,
    val tipe: String,          // "ujian" or "latihan"
    val durasi: Int,
    val jmlPg: Int,
    val jmlUraian: Int,
    val waktuTes: Long,        // epoch seconds
    val kategori: String,      // "tersedia","selesai","berjalan"
    val statusLabel: String,
    val statusClass: String,
    val sisaWaktu: String? = null
) {
    // Helper properties used by UI logic
    val isSelesai get() = kategori == "selesai"
    val isSedang get() = kategori == "berjalan"
    val lewat get() = statusLabel == "Waktu Habis"
}
