<?php
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST');

require_once __DIR__ . '/koneksi.php';
require_once __DIR__ . '/mapel_pendukung.php';

$conn = koneksi_db();
$t = DB_TABLE;
$t_snbt = DB_TABLE_SNBT;

// Ambil Parameter dari GET atau POST
$nis_siswa = trim($_REQUEST['nis'] ?? '');
$tingkat_siswa = strtoupper(trim($_REQUEST['tingkat'] ?? ''));
$search_keyword = trim($_REQUEST['keyword'] ?? '');
$kampus_filter = trim($_REQUEST['kampus'] ?? 'Semua');
$prestasi_dipilih = trim($_REQUEST['prestasi'] ?? 'Tidak Ada');

if (empty($nis_siswa)) {
    echo json_encode([
        'status' => false,
        'message' => 'Parameter NIS tidak boleh kosong',
        'siswa' => null,
        'list_kampus' => [],
        'data' => []
    ]);
    exit();
}

// ------------------------------------------------------------------------------
// 1. EKSTRAKSI DATA NILAI DARI GOOGLE SHEETS CSV
// ------------------------------------------------------------------------------
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

if (!empty($csv_url)) {
    $context = stream_context_create([
        'http' => [
            'timeout' => 8,
            'header'  => "User-Agent: Mozilla/5.0\r\n"
        ]
    ]);

    $csv_data = @file_get_contents($csv_url, false, $context);
    if ($csv_data !== false) {
        $lines = explode("\n", $csv_data);
        $all_rows = [];
        foreach ($lines as $line) {
            if (trim($line) !== '') {
                $all_rows[] = array_map('trim', str_getcsv($line));
            }
        }

        $row_header = null;
        $row_label_smt = null;
        foreach ($all_rows as $i => $r) {
            if (in_array('NIS', $r, true)) {
                $row_header = $i;
                $row_label_smt = $i - 1;
                break;
            }
        }

        if ($row_header !== null) {
            $headers = $all_rows[$row_header];
            $label_smt_row = $all_rows[$row_label_smt] ?? [];
            $col_nis = array_search('NIS', $headers, true);
            $col_nama = array_search('NAMA SISWA', $headers, true);
            $col_kelas = array_search('Kelas', $headers, true);

            $smt_blocks = [];
            foreach ($label_smt_row as $idx => $val) {
                if (preg_match('/^SMT\s*\d+$/i', $val)) {
                    $smt_blocks[$val] = $idx;
                }
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
                    $matched_row = $all_rows[$i];
                    break;
                }
            }

            if ($matched_row !== null) {
                $ditemukan = true;
                $nama_siswa = $matched_row[$col_nama] ?? "Siswa";
                $kelas_siswa = $matched_row[$col_kelas] ?? "-";
                $kelas_siswa_clean = strtoupper(trim($kelas_siswa));

                $rekap_mapel = [];
                $grand_total_nilai = 0;
                $grand_total_count = 0;

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
                                if (!isset($rekap_mapel[$nama_tampil])) {
                                    $rekap_mapel[$nama_tampil] = ['total' => 0, 'count' => 0];
                                }
                                $rekap_mapel[$nama_tampil]['total'] += (float)$nilai_angka;
                                $rekap_mapel[$nama_tampil]['count']++;
                                $grand_total_nilai += (float)$nilai_angka;
                                $grand_total_count++;
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
}

// ------------------------------------------------------------------------------
// 2. DAFTAR UNIK KAMPUS UNTUK FILTER
// ------------------------------------------------------------------------------
$list_kampus = ['Semua'];
$res_kampus = $conn->query("SELECT DISTINCT nama_ptn FROM `$t` ORDER BY nama_ptn ASC");
if ($res_kampus) {
    while ($r = $res_kampus->fetch_assoc()) {
        $list_kampus[] = $r['nama_ptn'];
    }
}

// ------------------------------------------------------------------------------
// 3. MATCHING ENGINE & REKOMENDASI
// ------------------------------------------------------------------------------
$rekomendasi = [];

if ($ditemukan) {
    $where_sql = "WHERE 1=1";
    $params = [];
    $types = "";

    if (!empty($kampus_filter) && $kampus_filter !== 'Semua') {
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

    // Map SNBT
    $snbt_map = [];
    if ($res_s = $conn->query("SELECT kode_prodi, kuota_terbaru, peminat_thn_1 FROM `$t_snbt`")) {
        while ($s = $res_s->fetch_assoc()) {
            $snbt_map[(string)$s['kode_prodi']] = $s;
        }
        $res_s->close();
    }

    $sql = "SELECT kode_ptn, nama_ptn, kode_prodi, nama_prodi, jenis_porto, jenjang, kuota_terbaru, peminat_thn_1, peminat_thn_2, peminat_thn_3, rasio_keketatan FROM `$t` $where_sql LIMIT 150";
    $stmt = $conn->prepare($sql);
    if (count($params) > 0) {
        $stmt->bind_param($types, ...$params);
    }
    $stmt->execute();
    $res = $stmt->get_result();

    while ($j = $res->fetch_assoc()) {
        $skor = 0;
        $alasan = [];

        $kuota  = (int)$j['kuota_terbaru'];
        $pin1   = (int)$j['peminat_thn_1'];
        $pin2   = (int)$j['peminat_thn_2'];
        $pin3   = (int)$j['peminat_thn_3'];

        // Regresi linear estimasi peminat
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
            $est_peminat = (int) max(0, round($my + $slope * ($n - $mx)));
        } else {
            $slope = 0.0;
            $est_peminat = $pin1;
        }

        $rasio_keketatan = ($kuota > 0) ? $pin1 / $kuota : (float)$j['rasio_keketatan'];
        $rasio_estimasi  = ($kuota > 0) ? $est_peminat / $kuota : 0.0;
        $rasio_efektif   = 0.8 * $rasio_keketatan + 0.2 * $rasio_estimasi;

        // Passing Grade
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

        // A. Evaluasi Rata-rata Rapor (60%)
        if ($grand_rata_rata >= $est_min_avg) {
            $surplus = $grand_rata_rata - $est_min_avg;
            $skor += min(60, 55 + ($surplus * 2));
            $alasan[] = "Rata-rata rapor Anda ({$grand_rata_rata}) melampaui estimasi passing grade ({$est_min_avg}).";
        } else {
            $gap = round($est_min_avg - $grand_rata_rata, 2);
            $poin_kurang = min(60, $gap * 8);
            $skor += max(0, 60 - $poin_kurang);
            $alasan[] = "Rata-rata rapor selisih {$gap} poin dari estimasi aman ({$est_min_avg}).";
        }

        // B. Evaluasi Mapel Pendukung (30%)
        $mapel_relevan = mapel_pendukung_prodi($j['nama_prodi']);
        $nilai_terbaik = 0;
        $mapel_andal = "";

        if (empty($mapel_relevan)) {
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

        if ($nilai_terbaik >= 83) {
            $skor_mapel = min(30, max(0, (($nilai_terbaik - 75) / 25) * 30));
            $skor += $skor_mapel;
            $alasan[] = "Mata pelajaran pendukung sangat baik: {$mapel_andal} ({$nilai_terbaik}).";
        } else {
            $info_mapel = $mapel_andal ?: implode(" / ", $mapel_relevan);
            $nilai_tampil = $nilai_terbaik > 0 ? " ({$nilai_terbaik})" : "";
            $alasan[] = "Nilai mata pelajaran pendukung {$info_mapel}{$nilai_tampil} belum optimal untuk prodi ini.";
        }

        // C. Bobot Prestasi (15%)
        $poin_prestasi = 0;
        switch ($prestasi_dipilih) {
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

        // D. Koreksi Tren Peminat
        $my = ($n > 0) ? array_sum($titik) / $n : 0;
        $persen_tren = ($my > 0) ? ($slope / $my) * 100 : 0;
        if ($n >= 2 && $persen_tren <= -10.0) {
            $skor += 5;
            $alasan[] = "Tren peminat cenderung menurun (" . round($persen_tren, 1) . "%/tahun) - persaingan melonggar.";
        } elseif ($n >= 2 && $persen_tren >= 10.0) {
            $skor -= 5;
            $alasan[] = "Tren peminat meningkat pesat (+" . round($persen_tren, 1) . "%/tahun) - persaingan makin ketat.";
        }

        $skor = max(0, min(100, round($skor)));

        // Info SNBT
        $s = $snbt_map[(string)$j['kode_prodi']] ?? null;
        $snbt_kuota   = $s ? (int)$s['kuota_terbaru'] : 0;
        $snbt_peminat = $s ? (int)$s['peminat_thn_1'] : 0;
        $snbt_rasio   = ($snbt_kuota > 0) ? round($snbt_peminat / $snbt_kuota, 2) : 0.0;
        $gabung_kuota   = $kuota + $snbt_kuota;
        $gabung_peminat = $pin1 + $snbt_peminat;
        $gabung_rasio   = ($gabung_kuota > 0) ? round($gabung_peminat / $gabung_kuota, 2) : 0.0;

        $rekomendasi[] = [
            'kampus'       => $j['nama_ptn'],
            'prodi'        => $j['nama_prodi'],
            'jenjang'      => $j['jenjang'],
            'skor'         => (int)$skor,
            'kuota'        => $kuota,
            'peminat'      => $pin1,
            'rasio'        => round($rasio_keketatan, 2),
            'est_peminat'  => $est_peminat,
            'rasio_est'    => round($rasio_estimasi, 2),
            'tren'         => round($slope, 2),
            'ada_snbt'     => ($s !== null),
            'snbt_kuota'   => $snbt_kuota,
            'snbt_peminat' => $snbt_peminat,
            'snbt_rasio'   => $snbt_rasio,
            'gabung_rasio' => $gabung_rasio,
            'alasan'       => $alasan
        ];
    }
    $stmt->close();

    // Urutkan skor tertinggi
    usort($rekomendasi, function($a, $b) {
        return $b['skor'] <=> $a['skor'];
    });
}

echo json_encode([
    'status' => true,
    'message' => $ditemukan ? 'Data rekomendasi berhasil dimuat' : 'Data siswa tidak ditemukan pada CSV Rapor',
    'siswa' => [
        'nama' => $nama_siswa,
        'kelas' => $kelas_siswa,
        'rata_rata' => $grand_rata_rata,
        'ditemukan' => $ditemukan
    ],
    'list_kampus' => $list_kampus,
    'data' => $rekomendasi
], JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
