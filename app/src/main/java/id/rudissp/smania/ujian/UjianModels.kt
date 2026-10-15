package id.rudissp.smania.ujian

import java.io.Serializable

data class UjianItem(
    val id: String = "",
    val judulUjian: String = "",
    val mapel: String = "",
    val waktuTes: String = "",
    val durasi: Int = 0,
    val jmlPg: Int = 0,
    val jmlUraian: Int = 0,
    val kelas: String = "",
    val tipe: String = "ujian", // "ujian" atau "latihan"
    val statusPengerjaan: String = "", // "selesai", "sedang", ""
    val isSelesai: Boolean = false,
    val isSedang: Boolean = false,
    val lewat: Boolean = false,
    val kategori: String = "tersedia", // "tersedia", "selesai", "berjalan", "latihan"
    val statusLabel: String = "Tersedia",
    val statusClass: String = "status-aktif"
) : Serializable

data class SoalUjian(
    val id: Int = 0,
    val idUjian: Int = 0,
    val tipeSoal: String = "pg", // pg, pg_kompleks, benar_salah, benar_salah_tabel, uraian
    val pertanyaan: String = "",
    val gambar: String? = null,
    val videoUrl: String? = null,
    val opsiA: String? = null,
    val opsiB: String? = null,
    val opsiC: String? = null,
    val opsiD: String? = null,
    val opsiE: String? = null,
    val skor: Double = 10.0,
    val kunciJawaban: String? = null,
    val tabelPernyataan: List<String> = emptyList()
) : Serializable {
    val tipeLabel: String
        get() = when (tipeSoal) {
            "pg" -> "Pilihan Ganda"
            "pg_kompleks" -> "PG Kompleks"
            "benar_salah" -> "Benar / Salah"
            "benar_salah_tabel" -> "Benar / Salah Tabel"
            "uraian" -> "Uraian"
            else -> tipeSoal.replaceFirstChar { it.uppercase() }
        }

    val optionsList: List<Pair<String, String>>
        get() = buildList {
            opsiA?.takeIf { it.isNotBlank() }?.let { add("A" to it) }
            opsiB?.takeIf { it.isNotBlank() }?.let { add("B" to it) }
            opsiC?.takeIf { it.isNotBlank() }?.let { add("C" to it) }
            opsiD?.takeIf { it.isNotBlank() }?.let { add("D" to it) }
            opsiE?.takeIf { it.isNotBlank() }?.let { add("E" to it) }
        }

    val statements: List<Pair<String, String>>
        get() = buildList {
            opsiA?.takeIf { it.isNotBlank() }?.let { add("A" to it) }
            opsiB?.takeIf { it.isNotBlank() }?.let { add("B" to it) }
            opsiC?.takeIf { it.isNotBlank() }?.let { add("C" to it) }
            opsiD?.takeIf { it.isNotBlank() }?.let { add("D" to it) }
            opsiE?.takeIf { it.isNotBlank() }?.let { add("E" to it) }
        }
}

data class JawabanItem(
    val jawaban: String = "",
    val benar: Boolean = false,
    val skor: Double = 0.0,
    val tipe: String = "",
    val kunci: String? = null
) : Serializable

data class UjianResponse(
    val status: String = "success",
    val data: List<UjianItem> = emptyList(),
    val countTersedia: Int = 0,
    val countSelesai: Int = 0,
    val countBerjalan: Int = 0,
    val countLatihan: Int = 0
)

data class UjianDetailResponse(
    val status: String = "success",
    val message: String? = null,
    val ujian: UjianItem? = null,
    val soal: List<SoalUjian> = emptyList(),
    val sisaDetik: Long = 0,
    val pelanggaran: Int = 0,
    val statusUjian: String = "sedang", // sedang, selesai, diblokir
    val jawabanTersimpan: Map<String, JawabanItem> = emptyMap()
) : Serializable

data class PelanggaranResult(
    val status: String = "success",
    val pelanggaran: Int = 0,
    val statusUjian: String = "sedang" // sedang, diblokir
) : Serializable

data class HasilUjianData(
    val idUjian: String = "",
    val judulUjian: String = "",
    val mapel: String = "",
    val kelas: String = "",
    val tipe: String = "ujian",
    val nilaiTotal: Double = 0.0,
    val jawabanBenar: Int = 0,
    val jawabanSalah: Int = 0,
    val totalSoal: Int = 0,
    val skorPg: Double = 0.0,
    val skorUraian: Double = 0.0,
    val bisaDiulang: Boolean = false,
    val detailJawaban: Map<Int, JawabanItem> = emptyMap(),
    val daftarSoal: List<SoalUjian> = emptyList()
) : Serializable
