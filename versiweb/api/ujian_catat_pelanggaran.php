<?php
// versiweb/api/ujian_catat_pelanggaran.php
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
$data_jawaban_sync = $_REQUEST['data_jawaban_sync'] ?? null;

if ($id_ujian <= 0 || empty($nis)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'ID Ujian dan NIS wajib diisi.']);
    exit;
}

$query_ujian = mysqli_query($conn_esmansa, "SELECT * FROM ujian WHERE id = $id_ujian");
$ujian = $query_ujian ? mysqli_fetch_assoc($query_ujian) : null;
$is_latihan = (($ujian['tipe'] ?? 'ujian') == 'latihan');

if ($is_latihan) {
    echo json_encode(['status' => 'success', 'pelanggaran' => 0, 'statusUjian' => 'sedang']);
    exit;
}

// Pastikan baris jawaban_siswa ada
$q_cek = mysqli_query($conn_esmansa, "SELECT id, status FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' ORDER BY id DESC LIMIT 1");
$row_cek = $q_cek ? mysqli_fetch_assoc($q_cek) : null;

if (!$row_cek) {
    mysqli_query($conn_esmansa, "
        INSERT INTO jawaban_siswa (id_ujian, nis, nama_siswa, kelas, waktu_mulai, status, jawaban, soal_terakhir, pelanggaran, terakhir_aktif)
        VALUES ($id_ujian, '$nis', '', '', NOW(), 'sedang', '{}', 1, 0, NOW())
    ");
}

if (!empty($data_jawaban_sync) && json_decode($data_jawaban_sync, true) !== null) {
    $jwb_esc = mysqli_real_escape_string($conn_esmansa, $data_jawaban_sync);
    mysqli_query($conn_esmansa, "
        UPDATE jawaban_siswa
        SET jawaban = '$jwb_esc', terakhir_aktif = NOW()
        WHERE id_ujian = $id_ujian AND nis = '$nis'
    ");
}

mysqli_query($conn_esmansa, "
    UPDATE jawaban_siswa
    SET pelanggaran = pelanggaran + 1, pelanggaran_total = pelanggaran_total + 1, terakhir_aktif = NOW()
    WHERE id_ujian = $id_ujian AND nis = '$nis'
");

$q_pel = mysqli_query($conn_esmansa, "
    SELECT pelanggaran, status FROM jawaban_siswa
    WHERE id_ujian = $id_ujian AND nis = '$nis'
    ORDER BY id DESC LIMIT 1
");
$row_pel = $q_pel ? mysqli_fetch_assoc($q_pel) : null;
$count_pel = (int)($row_pel['pelanggaran'] ?? 0);
$status_ujian = $row_pel['status'] ?? 'sedang';

if ($count_pel >= 3 && $status_ujian != 'selesai') {
    $status_ujian = 'diblokir';
    mysqli_query($conn_esmansa, "
        UPDATE jawaban_siswa SET status = 'diblokir'
        WHERE id_ujian = $id_ujian AND nis = '$nis'
    ");
} elseif ($count_pel < 3 && $status_ujian == 'diblokir') {
    // Jika guru mereset/unblock user di server (pelanggaran < 3), ubah status kembali ke sedang
    $status_ujian = 'sedang';
    mysqli_query($conn_esmansa, "
        UPDATE jawaban_siswa SET status = 'sedang'
        WHERE id_ujian = $id_ujian AND nis = '$nis'
    ");
}

echo json_encode([
    'status'      => 'success',
    'pelanggaran' => $count_pel,
    'statusUjian' => $status_ujian
]);
