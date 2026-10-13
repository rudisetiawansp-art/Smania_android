package id.rudissp.smania.jadwal

data class JadwalItemModel(
    val jamKe: Int,
    val status: String,
    val mapel: String,
    val infoBawah: String
)

data class HariModel(
    val namaHari: String,
    val listJadwal: List<JadwalItemModel>
)