<?php
session_start();
include 'koneksi.php';

if($_SESSION['status'] != "login"){
    header("location: index.php?pesan=belum_login");
    exit();
}

$nis = $_SESSION['nis'];
$pilihan_1 = $_POST['pilihan_1'];
$pilihan_2 = $_POST['pilihan_2'];

date_default_timezone_set('Asia/Jakarta');
$waktu_update = date('Y-m-d H:i:s'); 

// 1. Validasi: Pilihan 1 dan 2 tidak boleh sama
if ($pilihan_1 == $pilihan_2) {
    echo "<script>alert('Pilihan 1 dan 2 tidak boleh sama!'); window.location='pemilihanmapel.php';</script>";
    exit();
}

// 2. CEK: Apakah sudah ada data sebelumnya?
$q_cek = mysqli_query($koneksi, "SELECT id_seleksi FROM seleksi_paket WHERE nis='$nis'");

if (mysqli_num_rows($q_cek) > 0) {
    // JIKA ADA: Lakukan UPDATE
    $query = "UPDATE seleksi_paket SET 
                pilihan_1 = '$pilihan_1', 
                pilihan_2 = '$pilihan_2', 
                waktu_daftar = '$waktu_update' 
              WHERE nis = '$nis'";
    $pesan = "Pilihan Anda berhasil diperbarui!";
} else {
    // JIKA BELUM ADA: Lakukan INSERT
    $query = "INSERT INTO seleksi_paket (nis, pilihan_1, pilihan_2, waktu_daftar, status_seleksi) 
              VALUES ('$nis', '$pilihan_1', '$pilihan_2', '$waktu_update', 'Pending')";
    $pesan = "Pilihan Anda berhasil disimpan!";
}

$eksekusi = mysqli_query($koneksi, $query);

if ($eksekusi) {
    echo "<script>alert('$pesan'); window.location='pemilihanmapel.php';</script>";
} else {
    echo "<script>alert('Gagal menyimpan data.'); window.location='pemilihanmapel.php';</script>";
}
?>