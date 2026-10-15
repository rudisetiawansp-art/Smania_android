<?php
// dashboard.php
session_start();
date_default_timezone_set('Asia/Jakarta');
if (!isset($_SESSION['status']) || $_SESSION['status'] != "login") {
    header("location: login.php");
    exit;
}

include '../koneksi.php';

$conn_esmansa = mysqli_connect("localhost", "root", "", "smansuko_esmansa");
date_default_timezone_set('Asia/Jakarta');
$nis = $_SESSION['nis'];
$nama = $_SESSION['nama'];
$kelas = $_SESSION['kelas'];
$tingkat = $_SESSION['tingkat'];

// Ambil ujian yang tersedia untuk kelas siswa
$query_ujian = mysqli_query($conn_esmansa, "
    SELECT * FROM ujian 
    WHERE FIND_IN_SET('$kelas', REPLACE(kelas, ', ', ',')) > 0 
    ORDER BY waktu_tes DESC
");

// Riwayat: sudah selesai
$query_riwayat = mysqli_query($conn_esmansa, "
    SELECT id_ujian FROM jawaban_siswa 
    WHERE nis = '$nis' AND status = 'selesai'
");
$sudah_selesai = [];
while ($row = mysqli_fetch_assoc($query_riwayat)) {
    $sudah_selesai[$row['id_ujian']] = true;
}

// Riwayat: sedang dikerjakan
$query_sedang = mysqli_query($conn_esmansa, "
    SELECT id_ujian FROM jawaban_siswa 
    WHERE nis = '$nis' AND status = 'sedang'
");
$sedang_dikerjakan = [];
while ($row = mysqli_fetch_assoc($query_sedang)) {
    $sedang_dikerjakan[$row['id_ujian']] = true;
}

// Ambil semua ujian ke array dulu, biar bisa dipakai 2x (hitung statistik + render)
$semua_ujian = [];
while ($u = mysqli_fetch_assoc($query_ujian)) {
    $semua_ujian[] = $u;
}

// ============================================================
// KATEGORISASI & STATISTIK
// ============================================================
$now = time();
$count_tersedia = 0;
$count_selesai  = 0;
$count_berjalan = 0;
$count_latihan  = 0;

foreach ($semua_ujian as &$u) {
    $id_ujian = $u['id'];
    $waktu_tes = strtotime($u['waktu_tes']);
    $waktu_habis = $waktu_tes + ($u['durasi'] * 60);

    $is_selesai = isset($sudah_selesai[$id_ujian]);
    $is_sedang  = isset($sedang_dikerjakan[$id_ujian]);
    $lewat      = ($now > $waktu_habis);

    // --- Tentukan kategori utama ---
    if ($is_selesai) {
        $kategori = 'selesai';
    } elseif ($lewat) {
        // Waktu habis — masuk "selesai" walau tidak dikerjakan
        $kategori = 'selesai';
    } elseif ($is_sedang) {
        $kategori = 'berjalan';
    } else {
        // Belum dikerjakan & masih ada waktu
        $kategori = 'tersedia';
    }

    // --- Status label untuk badge ---
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

    $u['_kategori']     = $kategori;
    $u['_status_label'] = $status_label;
    $u['_status_class'] = $status_class;
    $u['_is_selesai']   = $is_selesai;
    $u['_is_sedang']    = $is_sedang;
    $u['_lewat']        = $lewat;
    $u['_waktu_tes']    = $waktu_tes;
    $u['_waktu_habis']  = $waktu_habis;
    $u['_now']          = $now;

    if ($kategori === 'tersedia')     $count_tersedia++;
    elseif ($kategori === 'selesai')  $count_selesai++;
    elseif ($kategori === 'berjalan') $count_berjalan++;
    if (($u['tipe'] ?? 'ujian') == 'latihan') $count_latihan++;
}
unset($u);
?>
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SMANIA - Ujian</title>
    
    <link rel="stylesheet" href="../css/light.css"> 
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
    <style>
        body {
            background-color: var(--md-sys-color-background);
            color: var(--md-sys-color-on-background);
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

        .header h2 { margin: 0; font-size: 18px; }

        .content { padding: 0 16px; }

        /* Statistik = filter */
        .stat-container {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 10px;
            margin-bottom: 20px;
        }
        
        .stat-card {
            background: white;
            border-radius: 12px;
            padding: 12px;
            border: 2px solid #eee;
            box-shadow: 0 2px 8px rgba(0,0,0,0.03);
            text-align: center;
            cursor: pointer;
            transition: all 0.2s ease;
            user-select: none;
            -webkit-tap-highlight-color: transparent;
        }
        .stat-card:hover { border-color: #ddd; }

        .stat-card.is-active[data-filter="tersedia"] { border-color: #1565c0; background: #f2f8fe; }
        .stat-card.is-active[data-filter="selesai"]  { border-color: #2e7d32; background: #f3faf3; }
        .stat-card.is-active[data-filter="berjalan"] { border-color: #e65100; background: #fff8f0; }
        .stat-card.is-active[data-filter="latihan"]  { border-color: #6a1b9a; background: #faf3fc; }
        
        .stat-card .icon-wrap {
            width: 32px; height: 32px;
            border-radius: 50%;
            display: flex; align-items: center; justify-content: center;
            margin: 0 auto 8px auto;
        }
        .stat-card p.title {
            font-size: 10px; color: #888;
            margin: 0 0 4px 0; font-weight: 500;
        }
        .stat-card p.value {
            font-size: 18px; font-weight: bold;
            margin: 0; color: #333;
        }

        /* Kartu ujian */
        .card-lapor {
            background: white;
            border-radius: 16px;
            margin-bottom: 15px;
            padding: 15px;
            border: 1px solid #eee;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
            text-decoration: none;
            color: inherit;
            display: block;
        }

        .head-lapor {
            display: flex; justify-content: space-between; align-items: flex-start;
            margin-bottom: 10px;
            border-bottom: 1px dashed #eee;
            padding-bottom: 10px;
        }
        .pelapor-info { display: flex; align-items: center; gap: 10px; flex: 1; }
        .lapor-title { margin: 0 0 5px 0; font-size: 15px; font-weight: bold; color: #333; line-height: 1.3; }
        .guru-tujuan {
            display: inline-block; margin-top: 4px;
            font-size: 11px; color: #1565c0; background: #e3f2fd;
            padding: 4px 8px; border-radius: 6px; font-weight: bold;
        }

        .tgl-lapor {
            margin: 0; font-size: 11px; color: #888;
            display: flex; align-items: center; gap: 4px;
        }

        .status {
            padding: 4px 8px; border-radius: 6px;
            font-size: 10px; font-weight: bold;
            display: inline-flex; align-items: center; gap: 3px;
        }
        .status-menunggu { background: #f5f5f5; color: #555; }
        .status-diproses { background: #fff3e0; color: #e65100; }
        .status-aktif    { background: #e3f2fd; color: #1565c0; }
        .status-selesai  { background: #e8f5e9; color: #2e7d32; }

        /* Penanda tipe ujian: Ujian / Latihan */
        .tipe-badge {
            display: inline-flex; align-items: center; gap: 3px;
            padding: 3px 8px; border-radius: 6px;
            font-size: 10px; font-weight: bold;
            text-transform: uppercase; letter-spacing: 0.4px;
            white-space: nowrap; flex-shrink: 0; align-self: flex-start;
            margin: 0 6px;
        }
        .tipe-badge[data-tipe="ujian"]   { background: #e3f2fd; color: #1565c0; }
        .tipe-badge[data-tipe="latihan"] { background: #f3e5f5; color: #6a1b9a; }

        .detail-ujian {
            display: flex; justify-content: space-between; align-items: center;
            margin-top: 12px; padding-top: 12px;
        }

        .btn-action {
            font-size: 11px; font-weight: 600; padding: 6px 12px;
            border-radius: 8px; text-decoration: none;
            display: inline-flex; align-items: center; gap: 4px;
        }
        .btn-primary-action { background: #e3f2fd; color: #1565c0; border: 1px solid #bbdefb; }
        .btn-success-action { background: #e8f5e9; color: #2e7d32; border: 1px solid #c8e6c9; }
        
        .empty-state { text-align: center; margin-top: 40px; color: #888; }

        .filter-empty {
            display: none; text-align: center;
            padding: 40px 20px; color: #888;
        }
        .filter-empty.show { display: block; }
    </style>
</head>
<body class="light">
    
    <div class="header">
        <a href="../dashboard.php" style="color: inherit;"><span class="material-icons">arrow_back</span></a>
        <h2>Ujian Online</h2>
    </div>

    <div class="content">
        
        <!-- Statistik — klik untuk filter -->
        <div class="stat-container"><!-- Tambahan filter Latihan -->
            <div class="stat-card" data-filter="tersedia" onclick="setFilter('tersedia')">
                <div class="icon-wrap" style="background: #e3f2fd; color: #1565c0;">
                    <span class="material-icons" style="font-size: 18px;">assignment</span>
                </div>
                <p class="title">Tersedia</p>
                <p class="value"><?= $count_tersedia ?></p>
            </div>
            <div class="stat-card" data-filter="selesai" onclick="setFilter('selesai')">
                <div class="icon-wrap" style="background: #e8f5e9; color: #2e7d32;">
                    <span class="material-icons" style="font-size: 18px;">check_circle</span>
                </div>
                <p class="title">Selesai</p>
                <p class="value"><?= $count_selesai ?></p>
            </div>
            <div class="stat-card" data-filter="berjalan" onclick="setFilter('berjalan')">
                <div class="icon-wrap" style="background: #fff3e0; color: #e65100;">
                    <span class="material-icons" style="font-size: 18px;">hourglass_top</span>
                </div>
                <p class="title">Berjalan</p>
                <p class="value"><?= $count_berjalan ?></p>
            </div>
            <div class="stat-card" data-filter="latihan" onclick="setFilter('latihan')">
                <div class="icon-wrap" style="background: #f3e5f5; color: #6a1b9a;">
                    <span class="material-icons" style="font-size: 18px;">school</span>
                </div>
                <p class="title">Latihan</p>
                <p class="value"><?= $count_latihan ?></p>
            </div>
        </div>

        <h3 id="judulDaftar" style="font-size: 14px; margin: 24px 0 12px 0; color: #555; font-weight: bold;">Ujian Tersedia</h3>

        <!-- Daftar Ujian -->
        <?php if (count($semua_ujian) > 0): ?>
            <?php foreach ($semua_ujian as $ujian):
                $id_ujian      = $ujian['id'];
                $kategori      = $ujian['_kategori'];
                $status_label  = $ujian['_status_label'];
                $status_class  = $ujian['_status_class'];
                $is_selesai    = $ujian['_is_selesai'];
                $is_sedang     = $ujian['_is_sedang'];
                $lewat         = $ujian['_lewat'];
                $waktu_tes     = $ujian['_waktu_tes'];
                $waktu_habis   = $ujian['_waktu_habis'];
                $now           = $ujian['_now'];

                $sisa_waktu = '';
                if (!$is_selesai && !$lewat && $now >= $waktu_tes && $now <= $waktu_habis) {
                    $sisa_menit = ceil(($waktu_habis - $now) / 60);
                    $sisa_waktu = "Sisa $sisa_menit menit";
                }

                // Penanda tipe: 'latihan' atau 'ujian'
                $tipe       = (($ujian['tipe'] ?? 'ujian') == 'latihan') ? 'latihan' : 'ujian';
                $tipe_label = ($tipe == 'latihan') ? 'Latihan' : 'Ujian';
            ?>
            <div class="card-lapor" data-status="<?= $kategori ?>" data-tipe="<?= $tipe ?>">
                <div class="head-lapor">
                    <div class="pelapor-info">
                        <div>
                            <p class="lapor-title"><?= htmlspecialchars($ujian['judul_ujian']) ?></p>
                            <span class="guru-tujuan"><?= htmlspecialchars($ujian['mapel']) ?></span>
                        </div>
                    </div>
                    <span class="tipe-badge" data-tipe="<?= $tipe ?>"><?= $tipe_label ?></span>
                    <div style="text-align: right; min-width: 90px;">
                        <span class="status <?= $status_class ?>"><?= $status_label ?></span>
                    </div>
                </div>

                <div class="tgl-lapor">
                    <span class="material-icons" style="font-size: 14px;">schedule</span>
                    <?= date('d/m/y H:i', $waktu_tes) ?> • <?= $ujian['durasi'] ?> Menit
                    <span style="margin-left: auto; color: #888; font-size: 10px;">
                        PG: <?= $ujian['jml_pg'] ?> | Ur: <?= $ujian['jml_uraian'] ?>
                    </span>
                </div>

                <div class="detail-ujian" style="border-top: 1px dashed #eee;">
                    <div>
                        <?php if ($sisa_waktu): ?>
                            <span style="font-size: 11px; color: #e65100; font-weight: 500;">
                                ⏱️ <?= $sisa_waktu ?>
                            </span>
                        <?php endif; ?>
                    </div>
                    
                    <div>
                        <?php if ($is_selesai): ?>
                            <div style="display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end;">
                                <a href="lihat_hasil_siswa.php?id=<?= $id_ujian ?>" class="btn-action btn-success-action">
                                    <span class="material-icons" style="font-size: 14px;">analytics</span> Lihat Hasil
                                </a>
                                <?php if ($tipe == 'latihan' && !$lewat): ?>
                                    <form method="POST" action="ulangi_latihan.php?id=<?= $id_ujian ?>" style="margin: 0;" onsubmit="return confirm('Kerjakan ulang latihan ini? Hasil percobaan sebelumnya akan diganti.');">
                                        <button type="submit" class="btn-action btn-primary-action" style="border: none; font-family: inherit; cursor: pointer;">
                                            <span class="material-icons" style="font-size: 14px;">refresh</span> Kerjakan Ulang
                                        </button>
                                    </form>
                                <?php endif; ?>
                            </div>
                        <?php elseif ($is_sedang && !$lewat): ?>
                            <a href="kerjakan_ujian.php?id=<?= $id_ujian ?>" class="btn-action btn-primary-action">
                                <span class="material-icons" style="font-size: 14px;">play_arrow</span> Lanjutkan
                            </a>
                        <?php elseif ($lewat): ?>
                            <span style="font-size: 11px; color: #aaa; font-style: italic;">Waktu Habis</span>
                        <?php elseif ($now < $waktu_tes): ?>
                            <span style="font-size: 11px; color: #aaa; font-style: italic;">Belum Mulai</span>
                        <?php else: ?>
                            <a href="kerjakan_ujian.php?id=<?= $id_ujian ?>" class="btn-action btn-primary-action">
                                <span class="material-icons" style="font-size: 14px;">play_arrow</span> Kerjakan
                            </a>
                        <?php endif; ?>
                    </div>
                </div>
            </div>
            <?php endforeach; ?>

            <div class="filter-empty" id="filterEmpty">
                <span class="material-icons" style="font-size: 48px; color: #ddd; margin-bottom: 10px; display: block;">search_off</span>
                <p style="font-size: 14px; margin: 0; color: #666;" id="filterEmptyText">Tidak ada ujian pada kategori ini.</p>
            </div>

        <?php else: ?>
            <div class="empty-state">
                <span class="material-icons" style="font-size: 48px; color: #ddd; margin-bottom: 10px; display: block;">inbox</span>
                <p style="font-size: 14px; margin: 0; color: #666;">Belum ada ujian yang tersedia untuk kelas Anda.</p>
                <p style="font-size: 12px; margin-top: 4px;">Silakan cek kembali nanti.</p>
            </div>
        <?php endif; ?>

    </div>

    <script>
        const LABEL_FILTER = {
            tersedia: 'Ujian Tersedia',
            selesai:  'Ujian Selesai',
            berjalan: 'Ujian Sedang Berjalan',
            latihan:  'Latihan'
        };

        const EMPTY_TEXT = {
            tersedia: 'Belum ada ujian yang tersedia untuk dikerjakan.',
            selesai:  'Belum ada ujian yang selesai.',
            berjalan: 'Tidak ada ujian yang sedang dikerjakan.',
            latihan:  'Belum ada latihan yang tersedia.'
        };

        let filterAktif = 'tersedia'; // DEFAULT: tersedia (belum dikerjakan)

        function setFilter(filter) {
            filterAktif = filter;

            document.querySelectorAll('.stat-card').forEach(card => {
                card.classList.toggle('is-active', card.dataset.filter === filter);
            });

            document.getElementById('judulDaftar').textContent = LABEL_FILTER[filter] || 'Daftar Ujian';

            let jumlahTampil = 0;
            document.querySelectorAll('.card-lapor').forEach(card => {
                // Filter "latihan" berdasarkan tipe ujian, bukan status pengerjaan
                const tampil = (filter === 'latihan')
                    ? (card.dataset.tipe === 'latihan')
                    : (card.dataset.status === filter);
                card.style.display = tampil ? 'block' : 'none';
                if (tampil) jumlahTampil++;
            });

            const empty = document.getElementById('filterEmpty');
            const emptyText = document.getElementById('filterEmptyText');
            if (empty && emptyText) {
                emptyText.textContent = EMPTY_TEXT[filter] || 'Tidak ada data.';
                empty.classList.toggle('show', jumlahTampil === 0);
            }

            try { localStorage.setItem('esmansa_filter_ujian', filter); } catch(e) {}
        }

        (function initFilter() {
            let saved = 'tersedia';
            try {
                const s = localStorage.getItem('esmansa_filter_ujian');
                if (s && LABEL_FILTER[s]) saved = s;
            } catch(e) {}
            setFilter(saved);
        })();
    </script>
</body>
</html>