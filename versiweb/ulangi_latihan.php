<?php
// ulangi_latihan.php — reset percobaan latihan supaya siswa bisa mengerjakan ulang
session_start();
date_default_timezone_set('Asia/Jakarta');
if (!isset($_SESSION['status']) || $_SESSION['status'] != "login") {
    header("location: login.php");
    exit;
}

include '../koneksi.php';
$conn_esmansa = mysqli_connect("localhost", "root", "", "smansuko_esmansa");

$nis      = mysqli_real_escape_string($conn_esmansa, $_SESSION['nis']);
$kelas    = mysqli_real_escape_string($conn_esmansa, $_SESSION['kelas']);
$id_ujian = isset($_GET['id']) ? (int)$_GET['id'] : 0;

// Hanya menerima POST supaya tidak ter-trigger oleh prefetch/preview browser
if ($id_ujian == 0 || ($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    header("Location: index.php");
    exit;
}

$query_ujian = mysqli_query($conn_esmansa, "
    SELECT * FROM ujian 
    WHERE id = $id_ujian AND FIND_IN_SET('$kelas', REPLACE(kelas, ', ', ',')) > 0
");
if (mysqli_num_rows($query_ujian) == 0) {
    die("Latihan tidak ditemukan atau tidak tersedia untuk kelas Anda.");
}
$ujian = mysqli_fetch_assoc($query_ujian);

// Hanya tipe latihan yang boleh diulang; ujian biasa tetap sekali saja
if (($ujian['tipe'] ?? 'ujian') != 'latihan') {
    header("Location: lihat_hasil_siswa.php?id=$id_ujian");
    exit;
}

// Tidak boleh diulang setelah jendela waktunya berakhir
if (!empty($ujian['waktu_tes'])) {
    $waktu_habis = strtotime($ujian['waktu_tes']) + ((int)$ujian['durasi'] * 60);
    if (time() > $waktu_habis) {
        header("Location: lihat_hasil_siswa.php?id=$id_ujian");
        exit;
    }
}

// Hapus percobaan sebelumnya: jawaban, nilai, dan hasil pengundian soal
mysqli_query($conn_esmansa, "DELETE FROM jawaban_siswa   WHERE id_ujian = $id_ujian AND nis = '$nis'");
mysqli_query($conn_esmansa, "DELETE FROM nilai_ujian     WHERE id_ujian = $id_ujian AND nis = '$nis'");
mysqli_query($conn_esmansa, "DELETE FROM soal_acak_siswa WHERE id_ujian = $id_ujian AND nis = '$nis'");

unset($_SESSION['kode_terverifikasi_' . $id_ujian]);

// Soal akan diundi ulang saat kerjakan_ujian.php dibuka
header("Location: kerjakan_ujian.php?id=$id_ujian");
exit;
