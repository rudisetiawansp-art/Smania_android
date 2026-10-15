<?php
// versiweb/api/ujian_verifikasi.php
header('Content-Type: application/json; charset=UTF-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST');
header('Access-Control-Allow-Headers: Content-Type');

date_default_timezone_set('Asia/Jakarta');

$conn_esmansa = mysqli_connect("localhost", "root", "", "smansuko_esmansa");
if (!$conn_esmansa) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Gagal terhubung ke database.']);
    exit;
}

$id_ujian = (int)($_REQUEST['id_ujian'] ?? $_REQUEST['id'] ?? 0);
$kode_akses = mysqli_real_escape_string($conn_esmansa, trim($_REQUEST['kode_akses'] ?? ''));
$nis = mysqli_real_escape_string($conn_esmansa, trim($_REQUEST['nis'] ?? ''));

if ($id_ujian <= 0) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'ID Ujian tidak valid.']);
    exit;
}

$query_ujian = mysqli_query($conn_esmansa, "SELECT * FROM ujian WHERE id = $id_ujian");
if (!$query_ujian || mysqli_num_rows($query_ujian) == 0) {
    echo json_encode(['status' => 'error', 'message' => 'Ujian tidak ditemukan.']);
    exit;
}
$ujian = mysqli_fetch_assoc($query_ujian);

// Latihan bebas tanpa kode
if (($ujian['tipe'] ?? 'ujian') == 'latihan') {
    echo json_encode(['status' => 'success', 'message' => 'Latihan dapat langsung dikerjakan.']);
    exit;
}

// Jika siswa sedang mengerjakan, izinkan lanjut
$query_sedang = mysqli_query($conn_esmansa, "
    SELECT id FROM jawaban_siswa
    WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'
");
if ($query_sedang && mysqli_num_rows($query_sedang) > 0) {
    echo json_encode(['status' => 'success', 'message' => 'Lanjutkan ujian.']);
    exit;
}

// Cek verifikasi kode akses global
$query_cek = mysqli_query($conn_esmansa, "
    SELECT * FROM kode_akses_global
    WHERE kode = '$kode_akses' AND expired_pada > NOW()
    ORDER BY id DESC LIMIT 1
");

if ($query_cek && mysqli_num_rows($query_cek) > 0) {
    echo json_encode(['status' => 'success', 'message' => 'Kode verifikasi valid.']);
} else {
    echo json_encode(['status' => 'error', 'message' => 'Kode akses salah atau sudah kadaluarsa!']);
}
