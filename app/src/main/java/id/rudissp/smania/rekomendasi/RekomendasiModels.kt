package id.rudissp.smania.rekomendasi

data class SiswaInfo(
    val nama: String = "",
    val kelas: String = "",
    val rataRata: Double = 0.0,
    val ditemukan: Boolean = false
)

data class RekomendasiItem(
    val kampus: String = "",
    val prodi: String = "",
    val jenjang: String = "",
    val skor: Int = 0,
    val kuota: Int = 0,
    val peminat: Int = 0,
    val rasio: Double = 0.0,
    val estPeminat: Int = 0,
    val rasioEst: Double = 0.0,
    val tren: Double = 0.0,
    val adaSnbt: Boolean = false,
    val snbtKuota: Int = 0,
    val snbtPeminat: Int = 0,
    val snbtRasio: Double = 0.0,
    val gabungRasio: Double = 0.0,
    val alasan: List<String> = emptyList()
)

data class RekomendasiResponse(
    val status: Boolean = false,
    val message: String = "",
    val siswa: SiswaInfo = SiswaInfo(),
    val listKampus: List<String> = emptyList(),
    val data: List<RekomendasiItem> = emptyList()
)
