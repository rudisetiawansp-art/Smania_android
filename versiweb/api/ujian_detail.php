<?php
// versiweb/api/ujian_detail.php
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

$id_ujian = (int)($_REQUEST['id'] ?? $_REQUEST['id_ujian'] ?? 0);
$nis = mysqli_real_escape_string($conn_esmansa, trim($_REQUEST['nis'] ?? ''));

if ($id_ujian <= 0 || empty($nis)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'ID Ujian dan NIS wajib diisi.']);
    exit;
}

$query_ujian = mysqli_query($conn_esmansa, "SELECT * FROM ujian WHERE id = $id_ujian");
if (!$query_ujian || mysqli_num_rows($query_ujian) == 0) {
    echo json_encode(['status' => 'error', 'message' => 'Ujian tidak ditemukan.']);
    exit;
}
$ujian = mysqli_fetch_assoc($query_ujian);

// Ambil atau undi soal acak siswa
$jml_pg = max(0, (int)($ujian['jml_pg'] ?? 0));
$jml_uraian = max(0, (int)($ujian['jml_uraian'] ?? 0));
$pakai_seluruh_bank = ($jml_pg === 0 && $jml_uraian === 0);

$q_cek_soal = mysqli_query($conn_esmansa, "SELECT COUNT(*) as total FROM soal_acak_siswa WHERE id_ujian = $id_ujian AND nis = '$nis'");
$jumlah_tersimpan = $q_cek_soal ? (int)(mysqli_fetch_assoc($q_cek_soal)['total'] ?? 0) : 0;

if ($jumlah_tersimpan === 0) {
    $soal_terpilih_id = [];

    $sql_pg = "SELECT id FROM soal_ujian WHERE id_ujian = $id_ujian AND tipe_soal IN ('pg', 'pg_kompleks', 'benar_salah', 'benar_salah_tabel') ORDER BY RAND()";
    if (!$pakai_seluruh_bank && $jml_pg > 0) $sql_pg .= " LIMIT $jml_pg";
    $q_pg = mysqli_query($conn_esmansa, $sql_pg);
    if ($q_pg) { while ($row = mysqli_fetch_assoc($q_pg)) { $soal_terpilih_id[] = (int)$row['id']; } }

    $sql_uraian = "SELECT id FROM soal_ujian WHERE id_ujian = $id_ujian AND tipe_soal = 'uraian' ORDER BY RAND()";
    if (!$pakai_seluruh_bank && $jml_uraian > 0) $sql_uraian .= " LIMIT $jml_uraian";
    $q_uraian = mysqli_query($conn_esmansa, $sql_uraian);
    if ($q_uraian) { while ($row = mysqli_fetch_assoc($q_uraian)) { $soal_terpilih_id[] = (int)$row['id']; } }

    shuffle($soal_terpilih_id);
    if (!empty($soal_terpilih_id)) {
        $values = []; $urutan = 1;
        foreach ($soal_terpilih_id as $id_soal) {
            $values[] = "($id_ujian, '$nis', $id_soal, $urutan)";
            $urutan++;
        }
        mysqli_query($conn_esmansa, "INSERT INTO soal_acak_siswa (id_ujian, nis, id_soal, urutan) VALUES " . implode(", ", $values));
    }
}

$q_soal = mysqli_query($conn_esmansa, "
    SELECT s.* FROM soal_acak_siswa sa
    JOIN soal_ujian s ON sa.id_soal = s.id
    WHERE sa.id_ujian = $id_ujian AND sa.nis = '$nis'
    ORDER BY sa.urutan ASC
");

$soal_list = [];
$sudah_diambil = [];
if ($q_soal) {
    while ($row = mysqli_fetch_assoc($q_soal)) {
        $soal_id = (int)$row['id'];
        if (isset($sudah_diambil[$soal_id])) continue;
        $sudah_diambil[$soal_id] = true;

        $soal_list[] = [
            'id'           => $soal_id,
            'idUjian'      => (int)$row['id_ujian'],
            'tipeSoal'     => $row['tipe_soal'] ?? 'pg',
            'pertanyaan'   => $row['pertanyaan'] ?? '',
            'gambar'       => !empty($row['gambar']) ? $row['gambar'] : null,
            'videoUrl'     => !empty($row['video_url']) ? $row['video_url'] : null,
            'opsiA'        => $row['opsi_a'] ?? null,
            'opsiB'        => $row['opsi_b'] ?? null,
            'opsiC'        => $row['opsi_c'] ?? null,
            'opsiD'        => $row['opsi_d'] ?? null,
            'opsiE'        => $row['opsi_e'] ?? null,
            'skor'         => (float)($row['skor'] ?? 10.0),
            'kunciJawaban' => $row['kunci_jawaban'] ?? null
        ];
    }
}

// Inisialisasi lembar jawaban_siswa
$query_sedang = mysqli_query($conn_esmansa, "
    SELECT * FROM jawaban_siswa
    WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'
    ORDER BY id DESC LIMIT 1
");
$jawaban_siswa = $query_sedang ? mysqli_fetch_assoc($query_sedang) : null;

if (!$jawaban_siswa) {
    mysqli_query($conn_esmansa, "
        INSERT INTO jawaban_siswa (id_ujian, nis, nama_siswa, kelas, waktu_mulai, status, jawaban, soal_terakhir, pelanggaran, terakhir_aktif)
        VALUES ($id_ujian, '$nis', '', '', NOW(), 'sedang', '{}', 1, 0, NOW())
        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
    ");
    $query_sedang = mysqli_query($conn_esmansa, "
        SELECT * FROM jawaban_siswa
        WHERE id_ujian = $id_ujian AND nis = '$nis' ORDER BY id DESC LIMIT 1
    ");
    $jawaban_siswa = $query_sedang ? mysqli_fetch_assoc($query_sedang) : null;
}

$durasi_detik = ((int)($ujian['durasi'] ?? 0)) * 60;
$waktu_mulai = strtotime($jawaban_siswa['waktu_mulai'] ?? 'now');
$waktu_selesai = $waktu_mulai + $durasi_detik;
$sisa_detik = max(0, $waktu_selesai - time());

$jawaban_raw = json_decode($jawaban_siswa['jawaban'] ?? '{}', true) ?: [];

echo json_encode([
    'status'           => 'success',
    'ujian'            => [
        'id'               => (string)$ujian['id'],
        'judulUjian'       => $ujian['judul_ujian'] ?? '',
        'mapel'            => $ujian['mapel'] ?? '',
        'waktuTes'         => $ujian['waktu_tes'] ?? '',
        'durasi'           => (int)($ujian['durasi'] ?? 0),
        'jmlPg'            => (int)($ujian['jml_pg'] ?? 0),
        'jmlUraian'        => (int)($ujian['jml_uraian'] ?? 0),
        'kelas'            => $ujian['kelas'] ?? '',
        'tipe'             => ($ujian['tipe'] ?? 'ujian') == 'latihan' ? 'latihan' : 'ujian'
    ],
    'soal'             => $soal_list,
    'sisaDetik'        => $sisa_detik,
    'pelanggaran'      => (int)($jawaban_siswa['pelanggaran'] ?? 0),
    'statusUjian'      => $jawaban_siswa['status'] ?? 'sedang',
    'jawabanTersimpan' => $jawaban_raw
]);
