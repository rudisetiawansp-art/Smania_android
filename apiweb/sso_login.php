<?php
session_start();

$secret_key = 'KunciRahasiaEsmansaSuperSangatAman123!'; 

// Gunakan $_REQUEST agar bisa menerima GET dan POST
$nis       = $_REQUEST['nis'] ?? '';
$timestamp = $_REQUEST['timestamp'] ?? '';
$signature = $_REQUEST['signature'] ?? '';

if (empty($nis) || empty($timestamp) || empty($signature)) {
    die("Akses ditolak: Parameter SSO tidak lengkap.");
}

// 1. Cek Expired Token (60 detik)
if (abs(time() - (int)$timestamp) > 60) {
    die("Akses ditolak: Token SSO sudah kadaluarsa. Silakan muat ulang halaman portal utama.");
}

// 2. Verifikasi Signature
$expected_signature = hash_hmac('sha256', $nis . '|' . $timestamp, $secret_key);

if (!hash_equals($expected_signature, $signature)) {
    die("Akses ditolak: Signature SSO tidak valid.");
}

// 3. Sertakan file koneksi database lokal
require_once 'koneksi.php'; 
$db = $koneksi ?? $conn ?? null;

if (!$db) {
    die("Error: Variabel koneksi database tidak ditemukan di koneksi.php");
}

// 4. Query data siswa
$stmt = $db->prepare("SELECT * FROM users WHERE nis = ?");
$stmt->bind_param("s", $nis);
$stmt->execute();
$result = $stmt->get_result();
$user   = $result->fetch_assoc();

if ($user) {
    // Set Session Login Lokal Sekolah
    $_SESSION['nis']           = $user['nis'];
    $_SESSION['nama']          = $user['nama']; 
    $_SESSION['level']         = $user['level'];
    $_SESSION['tingkat']       = $user['tingkat'];
    $_SESSION['kelas']         = $user['kelas'];
    $_SESSION['guruwali']      = $user['guruwali'];
    $_SESSION['nisn']          = $user['nisn'];
    $_SESSION['status']        = "login";
    
    $_SESSION['siswa_nis']     = $user['nis'];
    $_SESSION['siswa_nama']    = $user['nama'];
    $_SESSION['siswa_kelas']   = $user['kelas'];
    $_SESSION['siswa_tingkat'] = $user['tingkat'];
    $_SESSION['is_login']      = true;

    // Direct ke Halaman Ujian Lokal
    header("Location: /smania/ujian"); 
    exit;
} else {
    die("User dengan NIS " . htmlspecialchars($nis) . " tidak ditemukan di sistem ujian lokal.");
}