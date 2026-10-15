<?php
// versiweb/api/ujian_ulangi.php
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
$nis = mysqli_real_escape_string($conn_esmansa, trim($_REQUEST['nis'] ?? ''));

if ($id_ujian <= 0 || empty($nis)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'ID Ujian dan NIS wajib diisi.']);
    exit;
}

$query_ujian = mysqli_query($conn_esmansa, "SELECT * FROM ujian WHERE id = $id_ujian");
$ujian = $query_ujian ? mysqli_fetch_assoc($query_ujian) : null;

if (($ujian['tipe'] ?? 'ujian') != 'latihan') {
    echo json_encode(['status' => 'error', 'message' => 'Hanya modul latihan yang dapat diulang.']);
    exit;
}

mysqli_query($conn_esmansa, "DELETE FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis'");
mysqli_query($conn_esmansa, "DELETE FROM nilai_ujian WHERE id_ujian = $id_ujian AND nis = '$nis'");
mysqli_query($conn_esmansa, "DELETE FROM soal_acak_siswa WHERE id_ujian = $id_ujian AND nis = '$nis'");

echo json_encode([
    'status' => 'success',
    'message' => 'Modul latihan berhasil direset.'
]);
