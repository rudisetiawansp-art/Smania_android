package id.rudissp.smania

object AppConstants {
    const val BASE_URL = "https://sman1sukorejo.sch.id/smania/android/"
    const val CEK_APP_VERSION = BASE_URL + "cek_version.php"
    const val LOGIN_URL = BASE_URL + "login.php"
    const val REKAP_PRESENSI = BASE_URL + "presensi_rekap.php"
    const val PRESENSI_URL = BASE_URL + "presensi_simpan.php"
    const val CEK_PRESENSI = BASE_URL + "presensi_cek.php"
    const val PRESENSI_PRENGATURAN = BASE_URL + "presensi_pengaturan.php"

    const val LMS_URL = BASE_URL + "lms_load.php"
    const val LMS_DETAIL_URL = BASE_URL + "lms_detail_tugas.php"
    const val LMS_SUBMIT_DISKUSI = BASE_URL + "lms_submit_diskusi.php"
    const val LMS_SUBMIT_FILE = BASE_URL + "lms_upload_tugas.php"
    const val LMS_HAPUS_DISKUSI = BASE_URL + "lms_hapus_diskusi.php"

    const val PROFIL_URL = BASE_URL + "api_profil.php"
    const val UJIAN_LOCAL_BASE = "http://192.168.188.80/smania/ujian/"
    const val UJIAN_PUBLIC_BASE = "http://36.93.181.53/smania/ujian/"
    const val UJIAN_URL = UJIAN_LOCAL_BASE + "ujian_load.php"
    const val UJIAN_DETAIL_URL = UJIAN_LOCAL_BASE + "ujian_detail.php"
    const val UJIAN_VERIFIKASI_URL = UJIAN_LOCAL_BASE + "ujian_verifikasi.php"
    const val UJIAN_SUBMIT_URL = UJIAN_LOCAL_BASE + "ujian_submit.php"
    const val UJIAN_CATAT_PELANGGARAN_URL = UJIAN_LOCAL_BASE + "ujian_catat_pelanggaran.php"
    const val UJIAN_ULANGI_URL = UJIAN_LOCAL_BASE + "ujian_ulangi.php"

    const val API_JADWAL_KELAS = "https://sman1sukorejo.sch.id/esmansa/api_jadwal_kelas.php"
    const val IBADAH_PROSES = BASE_URL + "ibadah_proses_presensi.php"
    const val IBADAH_REKAP = BASE_URL + "ibadah_rekap.php"
}
