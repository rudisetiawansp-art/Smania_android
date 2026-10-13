package id.rudissp.smania.lms

import com.google.gson.annotations.SerializedName

data class LmsItem(
    @SerializedName("id_tugas") val idTugas: String,
    @SerializedName("judul") val judul: String,
    @SerializedName("mapel") val mapel: String,
    @SerializedName("jenis") val jenis: String, // "penugasan" atau "materi"
    @SerializedName("is_online") val isOnline: Int, // 1 = online, 0 = tatap muka
    @SerializedName("deadline") val deadline: String, // format: YYYY-MM-DD HH:MM:SS
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("id_pengumpulan") val idPengumpulan: String?,
    @SerializedName("nilai") val nilai: Int
)