<?php
// versiweb/api/ujian_load.php
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

$nis = mysqli_real_escape_string($conn_esmansa, $_REQUEST['nis'] ?? '');
$kelas = mysqli_real_escape_string($conn_esmansa, $_REQUEST['kelas'] ?? '');

if (empty($nis) || empty($kelas)) {
    http_response_code(400);
    echo json_encode(['status' => 'error', 'message' => 'NIS dan Kelas wajib diisi.']);
    exit;
}

// Ambil ujian untuk kelas siswa
$query_ujian = mysqli_query($conn_esmansa, "
    SELECT * FROM ujian
    WHERE FIND_IN_SET('$kelas', REPLACE(kelas, ', ', ',')) > 0
    ORDER BY waktu_tes DESC
");

if (!$query_ujian) {
    http_response_code(500);
    echo json_encode(['status' => 'error', 'message' => 'Gagal mengambil data ujian.']);
    exit;
}

// Riwayat: sudah selesai
$query_riwayat = mysqli_query($conn_esmansa, "
    SELECT id_ujian FROM jawaban_siswa
    WHERE nis = '$nis' AND status = 'selesai'
");
$sudah_selesai = [];
if ($query_riwayat) {
    while ($row = mysqli_fetch_assoc($query_riwayat)) {
        $sudah_selesai[$row['id_ujian']] = true;
    }
}

// Riwayat: sedang dikerjakan
$query_sedang = mysqli_query($conn_esmansa, "
    SELECT id_ujian FROM jawaban_siswa
    WHERE nis = '$nis' AND status = 'sedang'
");
$sedang_dikerjakan = [];
if ($query_sedang) {
    while ($row = mysqli_fetch_assoc($query_sedang)) {
        $sedang_dikerjakan[$row['id_ujian']] = true;
    }
}

$now = time();
$count_tersedia = 0;
$count_selesai  = 0;
$count_berjalan = 0;
$count_latihan  = 0;

$out = [];
while ($u = mysqli_fetch_assoc($query_ujian)) {
    $id = (string)$u['id'];
    $waktu_tes = strtotime($u['waktu_tes']);
    $waktu_habis = $waktu_tes + (((int)($u['durasi'] ?? 0)) * 60);

    $is_selesai = isset($sudah_selesai[$id]);
    $is_sedang  = isset($sedang_dikerjakan[$id]);
    $lewat      = ($now > $waktu_habis);

    if ($is_selesai) {
        $kategori = 'selesai';
    } elseif ($lewat) {
        $kategori = 'selesai';
    } elseif ($is_sedang) {
        $kategori = 'berjalan';
    } else {
        $kategori = 'tersedia';
    }

    if ($is_selesai) {
        $status_label = 'Sudah Dikerjakan';
        $status_class = 'status-selesai';
    } elseif ($is_sedang && !$lewat) {
        $status_label = 'Sedang Dikerjakan';
        $status_class = 'status-diproses';
    } elseif ($lewat) {
        $status_label = 'Waktu Habis';
        $status_class = 'status-menunggu';
    } elseif ($now < $waktu_tes) {
        $status_label = 'Belum Mulai';
        $status_class = 'status-menunggu';
    } else {
        $status_label = 'Berlangsung';
        $status_class = 'status-aktif';
    }

    $tipe = ($u['tipe'] ?? 'ujian') == 'latihan' ? 'latihan' : 'ujian';

    if ($kategori === 'tersedia')     $count_tersedia++;
    elseif ($kategori === 'selesai')  $count_selesai++;
    elseif ($kategori === 'berjalan') $count_berjalan++;
    if ($tipe === 'latihan')          $count_latihan++;

    $out[] = [
        'id'               => $id,
        'judulUjian'       => $u['judul_ujian'] ?? '',
        'mapel'            => $u['mapel'] ?? '',
        'waktuTes'         => $u['waktu_tes'] ?? '',
        'durasi'           => (int)($u['durasi'] ?? 0),
        'jmlPg'            => (int)($u['jml_pg'] ?? 0),
        'jmlUraian'        => (int)($u['jml_uraian'] ?? 0),
        'kelas'            => $u['kelas'] ?? '',
        'tipe'             => $tipe,
        'statusPengerjaan' => $is_selesai ? 'selesai' : ($is_sedang ? 'sedang' : ''),
        'isSelesai'        => $is_selesai,
        'isSedang'         => $is_sedang,
        'lewat'            => $lewat,
        'kategori'         => $kategori,
        'statusLabel'      => $status_label,
        'statusClass'      => $status_class
    ];
}

echo json_encode([
    'status'        => 'success',
    'data'          => $out,
    'countTersedia' => $count_tersedia,
    'countSelesai'  => $count_selesai,
    'countBerjalan' => $count_berjalan,
    'countLatihan'  => $count_latihan
]);
