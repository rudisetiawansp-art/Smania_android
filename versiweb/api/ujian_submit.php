<?php
// versiweb/api/ujian_submit.php
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
$nama = mysqli_real_escape_string($conn_esmansa, trim($_REQUEST['nama'] ?? ''));
$kelas = mysqli_real_escape_string($conn_esmansa, trim($_REQUEST['kelas'] ?? ''));
$data_jawaban_lokal = $_REQUEST['data_jawaban_lokal'] ?? '{}';
$nilai_total_req = (float)($_REQUEST['nilai_total'] ?? 0);

if ($id_ujian <= 0 || empty($nis)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'ID Ujian dan NIS wajib diisi.']);
    exit;
}

$jwb_esc = mysqli_real_escape_string($conn_esmansa, $data_jawaban_lokal);

// Update status jawaban_siswa menjadi selesai
mysqli_query($conn_esmansa, "
    UPDATE jawaban_siswa
    SET jawaban = '$jwb_esc', nilai = $nilai_total_req, status = 'selesai', terakhir_aktif = NOW()
    WHERE id_ujian = $id_ujian AND nis = '$nis'
");

// Insert atau update tabel nilai_ujian
$q_cek_nilai = mysqli_query($conn_esmansa, "
    SELECT id FROM nilai_ujian WHERE id_ujian = $id_ujian AND nis = '$nis'
");

if ($q_cek_nilai && mysqli_num_rows($q_cek_nilai) > 0) {
    mysqli_query($conn_esmansa, "
        UPDATE nilai_ujian
        SET nilai_total = $nilai_total_req, waktu_selesai = NOW()
        WHERE id_ujian = $id_ujian AND nis = '$nis'
    ");
} else {
    mysqli_query($conn_esmansa, "
        INSERT INTO nilai_ujian (id_ujian, nis, nama_siswa, kelas, nilai_total, waktu_selesai)
        VALUES ($id_ujian, '$nis', '$nama', '$kelas', $nilai_total_req, NOW())
    ");
}

echo json_encode([
    'status' => 'success',
    'message' => 'Jawaban berhasil dikumpulkan.'
]);
