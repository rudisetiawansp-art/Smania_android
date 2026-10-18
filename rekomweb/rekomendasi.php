<?php
session_start();

if (!isset($_SESSION['status']) || $_SESSION['status'] != "login") {
    header("location: ../index.php?pesan=belum_login");
    exit();
}

$nis_siswa = trim($_SESSION['siswa_nis']);
$tingkat_siswa = isset($_SESSION['siswa_tingkat']) ? strtoupper(trim($_SESSION['siswa_tingkat'])) : '';

// ==============================================================================
// 1. KONEKSI DATABASE (dapurhosting) - kredensial di koneksi.php
// ==============================================================================
require_once __DIR__ . '/koneksi.php';
$conn = koneksi_db();
$t = DB_TABLE;
$t_snbt = DB_TABLE_SNBT;

// Pemetaan mapel pendukung per kelompok prodi (acuan SNPMB Kurikulum Merdeka)
require_once __DIR__ . '/mapel_pendukung.php';

// ==============================================================================
// 2. EKSTRAKSI DATA NILAI DARIPADA CSV GOOGLE SHEETS
// ==============================================================================
$mapel_mapping = [
    'PAI'   => 'Pendidikan Agama', 'PP' => 'Pendidikan Pancasila', 'BINDO' => 'Bahasa Indonesia',
    'MAT'   => 'Matematika', 'BINGG' => 'Bahasa Inggris', 'PJOK' => 'Pendidikan Jasmani',
    'INFO'  => 'Informatika', 'BJAWA' => 'Bahasa Jawa', 'IPA' => 'Ilmu Pengetahuan Alam',
    'IPS'   => 'Ilmu Pengetahuan Sosial', 'SEJ' => 'Sejarah', 'PKWU' => 'Prakarya'
];

$pilihan_mapping = [
    'XII 1' => ['PIL 1' => 'Biologi', 'PIL 2' => 'Geografi', 'PIL 3' => 'Sosiologi', 'PIL 4' => 'Ekonomi'],
    'XII 3' => ['PIL 1' => 'Biologi', 'PIL 2' => 'Sejarah', 'PIL 3' => 'Sosiologi', 'PIL 4' => 'Ekonomi'],
    'XII 7' => ['PIL 1' => 'Fisika', 'PIL 2' => 'Kimia', 'PIL 3' => 'Biologi', 'PIL 4' => 'Informatika'],
];

$csv_url = "";
if ($tingkat_siswa === 'XII' || $tingkat_siswa === '12') {
    $csv_url = "https://docs.google.com/spreadsheets/d/e/2PACX-1vRhDhRmrp2iolNnGfXIhiYUppjqHarC8EBNKvkjhebSBJaa1RvYZieRPc6huRORbi_qTWLbh3kxE04B/pub?gid=1611750839&single=true&output=csv";
} elseif ($tingkat_siswa === 'XI' || $tingkat_siswa === '11') {
    $csv_url = "https://docs.google.com/spreadsheets/d/e/2PACX-1vSVVsVmkSIsMmzJhW-I6m3Szrsz37Dt0hNQwgqbDpc9U9_KKdZ5mVwp-DOROXVfW-M-ONCFg87dVWt4/pub?gid=129962504&single=true&output=csv";
}

$nama_siswa = "Siswa";
$kelas_siswa = "-";
$grand_rata_rata = 0;
$nilai_mapel_rapi = [];
$ditemukan = false;

if (!empty($csv_url) && ($handle = @fopen($csv_url, "r")) !== FALSE) {
    $all_rows = [];
    while (($row = fgetcsv($handle, 5000, ",")) !== FALSE) { $all_rows[] = array_map('trim', $row); }
    fclose($handle);

    $row_header = null;
    $row_label_smt = null;
    foreach ($all_rows as $i => $r) {
        if (in_array('NIS', $r, true)) { $row_header = $i; $row_label_smt = $i - 1; break; }
    }

    if ($row_header !== null) {
        $headers = $all_rows[$row_header];
        $label_smt_row = $all_rows[$row_label_smt] ?? [];
        $col_nis = array_search('NIS', $headers, true);
        $col_nama = array_search('NAMA SISWA', $headers, true);
        $col_kelas = array_search('Kelas', $headers, true);

        $smt_blocks = [];
        foreach ($label_smt_row as $idx => $val) {
            if (preg_match('/^SMT\s*\d+$/i', $val)) { $smt_blocks[$val] = $idx; }
        }

        $smt_keys = array_keys($smt_blocks);
        $smt_ranges = [];
        foreach ($smt_keys as $k => $label) {
            $start = $smt_blocks[$label];
            $end = isset($smt_keys[$k + 1]) ? $smt_blocks[$smt_keys[$k + 1]] - 1 : count($headers) - 1;
            $smt_ranges[$label] = [$start, $end];
        }

        $matched_row = null;
        for ($i = $row_header + 1; $i < count($all_rows); $i++) {
            if (isset($all_rows[$i][$col_nis]) && $all_rows[$i][$col_nis] === $nis_siswa) {
                $matched_row = $all_rows[$i]; break;
            }
        }

        if ($matched_row !== null) {
            $ditemukan = true;
            $nama_siswa = $matched_row[$col_nama] ?? "Siswa";
            $kelas_siswa = $matched_row[$col_kelas] ?? "-";
            $kelas_siswa_clean = strtoupper(trim($kelas_siswa));

            $rekap_mapel = []; 
            $grand_total_nilai = 0; $grand_total_count = 0;

            foreach ($smt_ranges as $label => $range) {
                for ($c = $range[0]; $c <= $range[1]; $c++) {
                    $nama_kolom = $headers[$c] ?? '';
                    if ($nama_kolom === '') continue;
                    
                    $nama_kolom_clean = strtoupper(trim($nama_kolom));
                    $nilai = $matched_row[$c] ?? '-';

                    if (!in_array($nama_kolom_clean, ['SUM', 'RANK'])) {
                        $nama_tampil = $nama_kolom;
                        if (isset($pilihan_mapping[$kelas_siswa_clean][$nama_kolom_clean])) {
                            $nama_tampil = $pilihan_mapping[$kelas_siswa_clean][$nama_kolom_clean];
                        } elseif (isset($mapel_mapping[$nama_kolom_clean])) {
                            $nama_tampil = $mapel_mapping[$nama_kolom_clean];
                        }

                        $nilai_angka = str_replace(',', '.', $nilai);
                        if (is_numeric($nilai_angka)) {
                            if (!isset($rekap_mapel[$nama_tampil])) { $rekap_mapel[$nama_tampil] = ['total' => 0, 'count' => 0]; }
                            $rekap_mapel[$nama_tampil]['total'] += (float)$nilai_angka;
                            $rekap_mapel[$nama_tampil]['count']++;
                            $grand_total_nilai += (float)$nilai_angka; $grand_total_count++;
                        }
                    }
                }
            }

            foreach ($rekap_mapel as $nama_mapel => $data_mapel) {
                $avg = round($data_mapel['total'] / $data_mapel['count'], 2);
                $nilai_mapel_rapi[$nama_mapel] = $avg; 
            }
            $grand_rata_rata = ($grand_total_count > 0) ? round($grand_total_nilai / $grand_total_count, 2) : 0;
        }
    }
}

// Ambil Daftar Unik Kampus dari Database untuk Dropdown Filter
$list_kampus = [];
$res_kampus = $conn->query("SELECT DISTINCT nama_ptn FROM `$t` ORDER BY nama_ptn ASC");
if ($res_kampus) {
    while ($r = $res_kampus->fetch_assoc()) {
        $list_kampus[] = $r['nama_ptn'];
    }
}

// ==============================================================================
// 3. LOGIKA MATCHING ENGINE DENGAN DATA MYSQL DINAMIS
//    (memakai tren 3 tahun peminat -> estimasi peminat tahun ini)
// ==============================================================================
$rekomendasi = [];
$search_keyword  = $_POST['keyword'] ?? '';
$kampus_filter   = $_POST['kampus'] ?? 'Semua';
$prestasi_dipilih = $_POST['prestasi'] ?? 'Tidak Ada';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && $ditemukan) {
    
    // Susun Query Mengambil Data Prodi dari MySQL
    $where_sql = "WHERE 1=1";
    $params = [];
    $types = "";

    if ($kampus_filter !== 'Semua') {
        $where_sql .= " AND nama_ptn = ?";
        $params[] = $kampus_filter;
        $types .= "s";
    }

    if (!empty($search_keyword)) {
        $where_sql .= " AND (nama_prodi LIKE ? OR nama_ptn LIKE ?)";
        $kw = "%" . $search_keyword . "%";
        $params[] = $kw;
        $params[] = $kw;
        $types .= "ss";
    }

    // Muat data jalur SNBT/UTBK (tabel keketatan_ptn_snbt) sekali di awal.
    // Jika tabel SNBT belum di-import, halaman tetap jalan tanpa info SNBT.
    $snbt_map = [];
    if ($res_s = $conn->query("SELECT kode_prodi, kuota_terbaru, peminat_thn_1 FROM `$t_snbt`")) {
        while ($s = $res_s->fetch_assoc()) {
            $snbt_map[(string)$s['kode_prodi']] = $s;
        }
        $res_s->close();
    }

    $stmt = $conn->prepare("SELECT kode_ptn, nama_ptn, kode_prodi, nama_prodi, jenis_porto, jenjang, kuota_terbaru, peminat_thn_1, peminat_thn_2, peminat_thn_3, rasio_keketatan FROM `$t` $where_sql LIMIT 100");
    if (count($params) > 0) {
        $stmt->bind_param($types, ...$params);
    }
    $stmt->execute();
    $res = $stmt->get_result();

    while ($j = $res->fetch_assoc()) {
    $skor = 0;
    $alasan = [];

    $kuota  = (int)$j['kuota_terbaru'];
    $pin1   = (int)$j['peminat_thn_1']; // (N-1)
    $pin2   = (int)$j['peminat_thn_2']; // (N-2)
    $pin3   = (int)$j['peminat_thn_3']; // (N-3)

    // --- 1. Estimasi Peminat (Regresi Linear Perbaikan Bug) ---
    $titik = [];
    if ($pin3 > 0) $titik[] = $pin3;
    if ($pin2 > 0) $titik[] = $pin2;
    if ($pin1 > 0) $titik[] = $pin1;

    $n = count($titik);
    if ($n >= 2) {
        $mx = ($n - 1) / 2;
        $my = array_sum($titik) / $n;
        $sxx = 0; $sxy = 0;
        foreach ($titik as $x => $y) { 
            $sxx += ($x - $mx) ** 2; 
            $sxy += ($x - $mx) * ($y - $my); 
        }
        $slope = $sxx > 0 ? $sxy / $sxx : 0.0;
        // Fix Formula: (n - mx) adalah jarak dari mean x ke target x
        $est_peminat = (int) max(0, round($my + $slope * ($n - $mx))); 
    } else {
        $slope = 0.0;
        $est_peminat = $pin1;
    }

    $rasio_keketatan = ($kuota > 0) ? $pin1 / $kuota : (float)$j['rasio_keketatan'];
    $rasio_estimasi  = ($kuota > 0) ? $est_peminat / $kuota : 0.0;
    $rasio_efektif   = 0.8 * $rasio_keketatan + 0.2 * $rasio_estimasi;

    // --- 2. Passing Grade Dinamis (Tier Disesuaikan) ---
    if ($rasio_efektif >= 35) {
        $est_min_avg = 92.5;
    } elseif ($rasio_efektif >= 20) {
        $est_min_avg = 89.5;
    } elseif ($rasio_efektif >= 10) {
        $est_min_avg = 87.5;
    } elseif ($rasio_efektif >= 5) {
        $est_min_avg = 85.0;
    } else {
        $est_min_avg = 82.0;
    }

    // --- A. Evaluasi Rata-rata Rapor (Bobot 60%) ---
    if ($grand_rata_rata >= $est_min_avg) {
        // Berikan nilai penuh 60, plus bonus progresif jika jauh melampaui passing grade
        $surplus = $grand_rata_rata - $est_min_avg;
        $skor += min(60, 55 + ($surplus * 2)); 
        $alasan[] = "Rata-rata rapor Anda ({$grand_rata_rata}) melampaui estimasi passing grade ({$est_min_avg}).";
    } else {
        $gap = round($est_min_avg - $grand_rata_rata, 2);
        $poin_kurang = min(60, $gap * 8);
        $skor += max(0, 60 - $poin_kurang);
        $alasan[] = "Rata-rata rapor selisih {$gap} poin dari estimasi aman ({$est_min_avg}).";
    }

// B. Evaluasi Mata Pelajaran Pendukung (Bobot 30%)
$mapel_relevan = mapel_pendukung_prodi($j['nama_prodi']);
$nilai_terbaik = 0;
$mapel_andal = "";

if (empty($mapel_relevan)) {
    // Fallback: Jika tidak ada aturan spesifik, gunakan rata-rata rapor keseluruhan
    $nilai_terbaik = $grand_rata_rata;
    $mapel_andal = "Rata-rata Keseluruhan";
} else {
    foreach ($mapel_relevan as $m) {
        $variasi_mapel = ($m === 'Matematika') 
            ? ['Matematika', 'Matematika Tingkat Lanjut'] 
            : [$m];

        foreach ($variasi_mapel as $vm) {
            if (isset($nilai_mapel_rapi[$vm]) && $nilai_mapel_rapi[$vm] > $nilai_terbaik) {
                $nilai_terbaik = (float)$nilai_mapel_rapi[$vm];
                $mapel_andal = $vm;
            }
        }
    }
}

// Evaluasi berdasarkan 1 mapel tersebut
if ($nilai_terbaik >= 83) { 
    // Bobot proporsional (Nilai 75 = 0 poin, Nilai 100 = 30 poin)
    $skor_mapel = min(30, max(0, (($nilai_terbaik - 75) / 25) * 30));
    $skor += $skor_mapel;
    $alasan[] = "Mata pelajaran pendukung sangat baik: {$mapel_andal} ({$nilai_terbaik}).";
} else {
    // Menampilkan mapel apa yang tertinggi beserta nilainya untuk insight siswa
    $info_mapel = $mapel_andal ?: implode(" / ", $mapel_relevan);
    $nilai_tampil = $nilai_terbaik > 0 ? " ({$nilai_terbaik})" : "";
    $alasan[] = "Nilai mata pelajaran pendukung {$info_mapel}{$nilai_tampil} belum optimal untuk prodi ini.";
}

    // --- C. Bobot Prestasi (Bobot 15% - Berjenjang) ---
    $poin_prestasi = 0;
    switch ($tingkat_prestasi ?? '') { // Asumsi ada variabel tingkat prestasi
        case 'Internasional': $poin_prestasi = 15; break;
        case 'Nasional':      $poin_prestasi = 12; break;
        case 'Provinsi':      $poin_prestasi = 8;  break;
        case 'Kabupaten':     $poin_prestasi = 5;  break;
        default:
            $poin_prestasi = ($prestasi_dipilih !== 'Tidak Ada') ? 5 : 0;
            break;
    }
    $skor += $poin_prestasi;
    if ($poin_prestasi > 0) {
        $alasan[] = "Sertifikat {$prestasi_dipilih} memberikan poin portofolio +{$poin_prestasi}.";
    }

    // --- D. Koreksi Tren Peminat (% Growth) ---
    $persen_tren = ($my > 0) ? ($slope / $my) * 100 : 0;
    if ($n >= 2 && $persen_tren <= -10.0) {
        $skor += 5;
        $alasan[] = "Tren peminat cenderung menurun (" . round($persen_tren, 1) . "%/tahun) - persaingan melonggar.";
    } elseif ($n >= 2 && $persen_tren >= 10.0) {
        $skor -= 5;
        $alasan[] = "Tren peminat meningkat pesat (+" . round($persen_tren, 1) . "%/tahun) - persaingan makin ketat.";
    }

    $skor = max(0, min(100, $skor));

  

        // --- Info jalur SNBT (UTBK) & rasio gabungan SNBP+SNBT ---
        $s = $snbt_map[(string)$j['kode_prodi']] ?? null;
        $snbt_kuota   = $s ? (int)$s['kuota_terbaru'] : 0;
        $snbt_peminat = $s ? (int)$s['peminat_thn_1'] : 0;
        $snbt_rasio   = ($snbt_kuota > 0) ? $snbt_peminat / $snbt_kuota : 0.0;
        $gabung_kuota   = $kuota + $snbt_kuota;
        $gabung_peminat = $pin1 + $snbt_peminat;
        $gabung_rasio   = ($gabung_kuota > 0) ? $gabung_peminat / $gabung_kuota : 0.0;

        $rekomendasi[] = [
            'kampus' => $j['nama_ptn'],
            'prodi' => $j['nama_prodi'],
            'jenjang' => $j['jenjang'],
            'skor' => round($skor),
            'kuota' => $kuota,
            'peminat' => $pin1,
            'rasio' => $rasio_keketatan,
            'est_peminat' => $est_peminat,
            'rasio_est' => $rasio_estimasi,
            'tren' => $slope,
            'ada_snbt' => ($s !== null),
            'snbt_kuota' => $snbt_kuota,
            'snbt_peminat' => $snbt_peminat,
            'snbt_rasio' => $snbt_rasio,
            'gabung_rasio' => $gabung_rasio,
            'alasan' => $alasan
        ];
    }
    $stmt->close();

    // Urutkan berdasarkan Skor Peluang Kelolosan Tertinggi
    usort($rekomendasi, function($a, $b) {
        return $b['skor'] <=> $a['skor'];
    });
}
?>

<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Rekomendasi PTN & Prodi</title>
    <link rel="stylesheet" href="../css/light.css">
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
    <style>
        body {
            background-color: var(--md-sys-color-background, #f8f9fa);
            color: var(--md-sys-color-on-background, #1f1f1f);
            font-family: 'Roboto', sans-serif;
            margin: 0 auto;
            max-width: 480px;
            min-height: 100vh;
            padding-bottom: 90px;
            position: relative;
        }

        .header {
            padding: 20px;
            background-color: var(--md-sys-color-primary);
            color: var(--md-sys-color-on-primary);
            border-bottom-left-radius: 24px;
            border-bottom-right-radius: 24px;
            display: flex;
            align-items: center;
            gap: 15px;
            position: sticky;
            top: 0;
            z-index: 2000;
            box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
            margin-bottom: 16px;
        }

        .header h2 { margin: 0; font-size: 20px; font-weight: 500; }
        .header small { font-size: 12px; }

        .content { padding: 0 16px; }

        .form-card {
            background: var(--md-sys-color-surface, white);
            border: 1px solid #eee;
            border-radius: 12px;
            padding: 16px;
            box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
            margin-bottom: 20px;
        }
        .form-group { margin-bottom: 14px; }
        .form-group label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: var(--md-sys-color-on-surface-variant, #5f6368); }
        input, select, button { width: 100%; padding: 12px; border-radius: 8px; border: 1px solid #ddd; font-size: 14px; font-family: inherit; box-sizing: border-box; background: var(--md-sys-color-surface, white); color: inherit; }
        button { background-color: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); border: none; font-weight: bold; cursor: pointer; margin-top: 10px; }

        .result-card {
            background: var(--md-sys-color-surface, white);
            border-radius: 12px;
            margin-bottom: 12px;
            padding: 16px;
            border: 1px solid #eee;
            box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
        }
        .kampus-name { font-size: 12px; font-weight: bold; color: var(--md-sys-color-on-surface-variant, #5f6368); text-transform: uppercase; margin: 0; }
        .prodi-title { font-size: 16px; font-weight: bold; color: var(--md-sys-color-primary, #1a73e8); margin: 2px 0 10px 0; }

        .stats-grid { display: flex; gap: 8px; margin-bottom: 12px; background: var(--md-sys-color-surface-variant, #f1f3f4); padding: 8px 12px; border-radius: 8px; text-align: center; }
        .stat-item { flex: 1; }
        .stat-item .val { font-size: 13px; font-weight: bold; color: var(--md-sys-color-on-surface, #202124); }
        .stat-item .lbl { font-size: 10px; color: var(--md-sys-color-on-surface-variant, #5f6368); }

        .alasan-list { margin: 10px 0 0 0; padding-left: 18px; font-size: 12px; color: var(--md-sys-color-on-surface-variant, #444); line-height: 1.5; }
        .progress-bar { height: 6px; background: var(--md-sys-color-surface-variant, #e0e0e0); border-radius: 3px; margin: 8px 0; overflow: hidden; }
        .progress-fill { height: 100%; border-radius: 3px; }

        /* Chip gaya Material 3 (seperti indexjurusan.php) */
        .chip { display: inline-flex; align-items: center; height: 28px; padding: 0 12px; border-radius: 16px; font-size: 11px; font-weight: 500; }
        .ketat-tinggi { background: #f9dedc; color: #410e0b; }
        .ketat-sedang { background: #f9e387; color: #4d3a00; }
        .ketat-longgar { background: #cce8e4; color: #005047; }

        /* Disclaimer & sumber data */
        .disclaimer-card {
            background: var(--md-sys-color-surface, white);
            border: 1px solid #eee;
            border-radius: 12px;
            padding: 14px 16px;
            margin-top: 20px;
            box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
        }
        .disclaimer-card .dis-title { font-size: 12px; font-weight: bold; color: var(--md-sys-color-on-surface-variant, #5f6368); margin: 0 0 6px 0; display: flex; align-items: center; gap: 6px; }
        .disclaimer-card p { margin: 0 0 8px 0; font-size: 11px; line-height: 1.55; color: var(--md-sys-color-on-surface-variant, #5f6368); }
        .disclaimer-card a { color: var(--md-sys-color-primary, #1a73e8); }
    </style>
</head>
<body class="light">

    <div class="header">
        <a href="../dashboard.php" style="color: inherit; text-decoration: none; display: flex;"><span class="material-icons">arrow_back</span></a>
        <div>
            <h2>Rekomendasi PTN & Prodi</h2>
            <small>Analisis Peluang Kelolosan SNBP & SNBT</small>
        </div>
    </div>

    <div class="content">
        <?php if (!$ditemukan): ?>
            <div style="text-align: center; margin-top: 40px; color: #777;">
                <span class="material-icons" style="font-size: 40px;">assignment_late</span>
                <p>Data rapor tidak ditemukan di sheet database.</p>
            </div>
        <?php else: ?>

            <div class="form-card">
                <p style="margin-top:0; font-size: 13px; color:#555;">
                    Siswa: <b><?= htmlspecialchars($nama_siswa) ?></b> (Rata2 Rapor: <b><?= $grand_rata_rata ?></b>)
                </p>
                
                <form method="POST">
                    <div class="form-group">
                        <label>Cari Kata Kunci Prodi</label>
                        <input type="text" name="keyword" placeholder="Contoh: Informatika, Hukum, Manajemen..." value="<?= htmlspecialchars($search_keyword) ?>">
                    </div>

                    <div class="form-group">
                        <label>Filter Target Kampus (PTN)</label>
                        <select name="kampus">
                            <option value="Semua">-- Semua PTN --</option>
                            <?php foreach($list_kampus as $k): ?>
                                <option value="<?= htmlspecialchars($k) ?>" <?= $kampus_filter == $k ? 'selected' : '' ?>><?= htmlspecialchars($k) ?></option>
                            <?php endforeach; ?>
                        </select>
                    </div>

                    <div class="form-group">
                        <label>Sertifikat / Prestasi</label>
                        <select name="prestasi">
                            <option value="Tidak Ada" <?= $prestasi_dipilih == 'Tidak Ada' ? 'selected' : '' ?>>Tidak Ada Sertifikat</option>
                            <option value="Tingkat Nasional" <?= $prestasi_dipilih == 'Tingkat Nasional' ? 'selected' : '' ?>>Juara 1/2/3 Tingkat Nasional</option>
                            <option value="Tingkat Provinsi" <?= $prestasi_dipilih == 'Tingkat Provinsi' ? 'selected' : '' ?>>Juara 1/2/3 Tingkat Provinsi</option>
                            <option value="Tingkat Kabupaten" <?= $prestasi_dipilih == 'Tingkat Kabupaten' ? 'selected' : '' ?>>Juara 1/2/3 Tingkat Kabupaten</option>
                        </select>
                    </div>

                    <button type="submit">Analisis Peluang Kelolosan</button>
                </form>
            </div>

            <?php if ($_SERVER['REQUEST_METHOD'] === 'POST'): ?>
                <h3 style="font-size: 14px; margin-bottom: 12px; color: #444;">Hasil Analisis Kelolosan:</h3>
                
                <?php if (empty($rekomendasi)): ?>
                    <p style="font-size: 13px; color: #777;">Tidak ditemukan prodi yang cocok dengan kriteria pencarian.</p>
                <?php else: ?>
                    <?php foreach ($rekomendasi as $rek): ?>
                        <div class="result-card">
                            <div class="kampus-name"><?= htmlspecialchars($rek['kampus']) ?></div>
                            <div class="prodi-title"><?= htmlspecialchars($rek['prodi']) ?> (<?= htmlspecialchars($rek['jenjang']) ?>)</div>
                            
                            <div style="display:flex; justify-content:space-between; align-items:center; font-size:12px; margin-bottom:4px;">
                                <span>Peluang Kelolosan: <b><?= $rek['skor'] ?>%</b></span>
                                <span class="badge-ketat <?= $rek['rasio'] >= 10 ? 'ketat-tinggi' : ($rek['rasio'] >= 5 ? 'ketat-sedang' : 'ketat-longgar') ?>">
                                    Rasio Keketatan 1 : <?= round($rek['rasio'], 1) ?>
                                </span>
                            </div>

                            <div class="progress-bar">
                                <div class="progress-fill" style="width: <?= $rek['skor'] ?>%; background: <?= $rek['skor'] >= 75 ? '#34a853' : ($rek['skor'] >= 55 ? '#fbbc05' : '#ea4335') ?>;"></div>
                            </div>

                            <!-- Data Historis Daya Tampung & Peminat dari MySQL -->
                            <div class="stats-grid">
                                <div class="stat-item">
                                    <div class="val"><?= number_format($rek['kuota']) ?></div>
                                    <div class="lbl">Kuota</div>
                                </div>
                                <div class="stat-item">
                                    <div class="val"><?= number_format($rek['peminat']) ?></div>
                                    <div class="lbl">Peminat (thn lalu)</div>
                                </div>
                                <div class="stat-item">
                                    <div class="val"><?= number_format($rek['est_peminat']) ?></div>
                                    <div class="lbl">Est. Peminat</div>
                                </div>
                                <div class="stat-item">
                                    <div class="val">1 : <?= round($rek['rasio'], 1) ?></div>
                                    <div class="lbl">Persaingan</div>
                                </div>
                            </div>

                            <?php if (!empty($rek['ada_snbt'])): ?>
                            <!-- Data jalur SNBT (UTBK) & gabungan -->
                            <div class="stats-grid" style="margin-top: -6px;">
                                <div class="stat-item">
                                    <div class="val"><?= number_format($rek['snbt_kuota']) ?></div>
                                    <div class="lbl">Kuota SNBT</div>
                                </div>
                                <div class="stat-item">
                                    <div class="val"><?= number_format($rek['snbt_peminat']) ?></div>
                                    <div class="lbl">Peminat SNBT</div>
                                </div>
                                <div class="stat-item">
                                    <div class="val">1 : <?= round($rek['snbt_rasio'], 1) ?></div>
                                    <div class="lbl">Rasio SNBT</div>
                                </div>
                                <div class="stat-item">
                                    <div class="val">1 : <?= round($rek['gabung_rasio'], 1) ?></div>
                                    <div class="lbl">Gab. SNBP+SNBT</div>
                                </div>
                            </div>
                            <?php endif; ?>

                            <ul class="alasan-list">
                                <?php foreach ($rek['alasan'] as $alasan): ?>
                                    <li><?= htmlspecialchars($alasan) ?></li>
                                <?php endforeach; ?>
                            </ul>
                        </div>
                    <?php endforeach; ?>
                <?php endif; ?>
            <?php endif; ?>

        <?php endif; ?>

        <!-- Disclaimer & sumber data -->
        <div class="disclaimer-card">
            <p class="dis-title"><span class="material-icons" style="font-size: 14px;">info</span> Disclaimer</p>
            <p>Peluang Kelolosan dan estimasi passing grade di halaman ini dihitung
            secara otomatis berdasarkan data peminat & daya tampung <b>3 tahun terakhir</b>
            (regresi tren), ditambah rata-rata rapor, nilai mapel pendukung, dan portofolio
            prestasi. Hasilnya bersifat <b>estimasi</b> — bukan prediksi atau jaminan kelolosan.</p>
            <p><b>Sumber data:</b> SNPMB / BPPP Kemdiktisaintek — daya tampung 2026 &amp;
            histori peminat 2021–2025 (<a href="https://snpmb.id" target="_blank" rel="noopener">snpmb.id</a>).
            Mapel pendukung mengacu pada Kepmen 102/M/2025 (Kurikulum Merdeka).</p>
        </div>
    </div>
</body>
</html>
