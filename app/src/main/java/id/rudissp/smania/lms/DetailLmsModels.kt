package id.rudissp.smania.lms

import com.google.gson.annotations.SerializedName

data class DetailResponse(
    @SerializedName("status") val status: String,
    @SerializedName("tugas") val tugas: TugasModel?,
    @SerializedName("pengumpulan") val pengumpulan: PengumpulanModel?,
    @SerializedName("diskusi") val diskusi: List<DiskusiModel>?
)

data class TugasModel(
    @SerializedName("id_tugas") val idTugas: Int,
    @SerializedName("judul") val judul: String,
    @SerializedName("mapel") val mapel: String,
    @SerializedName("deskripsi") val deskripsi: String,
    @SerializedName("nama_guru") val namaGuru: String,
    @SerializedName("jenis") val jenis: String,
    @SerializedName("is_online") val isOnline: Int,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("file_lampiran") val fileLampiran: String?
)

data class PengumpulanModel(
    @SerializedName("id_pengumpulan") val idPengumpulan: Int,
    @SerializedName("waktu_kumpul") val waktuKumpul: String,
    @SerializedName("file_jawaban") val fileJawaban: String,
    @SerializedName("nilai") val nilai: Int,
    @SerializedName("komentar_guru") val komentar_guru: String
)

data class DiskusiModel(
    @SerializedName("id_diskusi") val idDiskusi: Int,
    @SerializedName("pengirim_id") val pengirimId: String,
    @SerializedName("pengirim_nama") val pengirimNama: String,
    @SerializedName("pengirim_tipe") val pengirimTipe: String, // "siswa" atau "guru"
    @SerializedName("pesan") val pesan: String,
    @SerializedName("created_at") val createdAt: String
)