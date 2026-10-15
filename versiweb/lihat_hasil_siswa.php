<?php
// lihat_hasil_siswa.php
session_start();
if (!isset($_SESSION['status']) || $_SESSION['status'] != "login") {
    header("location: login.php");
    exit;
}

include '../koneksi.php';
$conn_esmansa = mysqli_connect("localhost", "root", "", "smansuko_esmansa");

$nis = $_SESSION['nis'];
$id_ujian = isset($_GET['id']) ? (int)$_GET['id'] : 0;

if ($id_ujian == 0) {
    header("Location: dashboard.php");
    exit;
}

// Ambil nilai (Hanya untuk memvalidasi bahwa siswa memang sudah selesai)
$query_nilai = mysqli_query($conn_esmansa, "
    SELECT * FROM nilai_ujian 
    WHERE id_ujian = $id_ujian AND nis = '$nis'
");
if (mysqli_num_rows($query_nilai) == 0) {
    header("Location: dashboard.php");
    exit;
}
$nilai = mysqli_fetch_assoc($query_nilai);

// Ambil data ujian
$query_ujian = mysqli_query($conn_esmansa, "
    SELECT * FROM ujian WHERE id = $id_ujian
");
$ujian = mysqli_fetch_assoc($query_ujian);

// ============================================================
// TIPE LATIHAN: siapkan rincian jawaban siswa + daftar soal
// ============================================================
$is_latihan = (($ujian['tipe'] ?? 'ujian') == 'latihan');

$detail_jawaban = [];
$soal_latihan   = [];
$jumlah_benar_detail = 0;
$jumlah_salah_detail = 0;

if ($is_latihan) {
    // Ambil jawaban terakhir siswa untuk ujian ini
    $q_jwb = mysqli_query($conn_esmansa, "
        SELECT * FROM jawaban_siswa 
        WHERE id_ujian = $id_ujian AND nis = '$nis' 
        ORDER BY id DESC LIMIT 1
    ");
    if ($q_jwb && mysqli_num_rows($q_jwb) > 0) {
        $row_jwb = mysqli_fetch_assoc($q_jwb);
        $detail_jawaban = json_decode($row_jwb['jawaban'] ?? '{}', true) ?: [];
    }

    // Soal yang benar-benar diterima siswa (urutan acak hasil pengundian)
    $q_soal = mysqli_query($conn_esmansa, "
        SELECT s.* FROM soal_acak_siswa sa
        JOIN soal_ujian s ON s.id = sa.id_soal
        WHERE sa.id_ujian = $id_ujian AND sa.nis = '$nis'
        ORDER BY sa.urutan ASC
    ");
    if ($q_soal && mysqli_num_rows($q_soal) > 0) {
        while ($row = mysqli_fetch_assoc($q_soal)) { $soal_latihan[] = $row; }
    } else {
        // Fallback bila pengundian soal tidak tersimpan
        $q_soal2 = mysqli_query($conn_esmansa, "SELECT * FROM soal_ujian WHERE id_ujian = $id_ujian ORDER BY id ASC");
        if ($q_soal2) { while ($row = mysqli_fetch_assoc($q_soal2)) { $soal_latihan[] = $row; } }
    }

    // Hitung ringkasan benar/salah (hanya soal objektif)
    foreach ($soal_latihan as $s) {
        if ($s['tipe_soal'] === 'uraian') continue;
        $d = $detail_jawaban[$s['id']] ?? [];
        if (isset($d['benar'])) {
            if ($d['benar']) $jumlah_benar_detail++; else $jumlah_salah_detail++;
        } else {
            $jw = (string)($d['jawaban'] ?? '');
            if ($jw !== '' && $jw === (string)($s['kunci_jawaban'] ?? '')) $jumlah_benar_detail++;
            else $jumlah_salah_detail++;
        }
    }
}

$nilai_total    = (isset($nilai['nilai_total']) && $nilai['nilai_total'] !== null) ? round((float)$nilai['nilai_total']) : 0;
$total_soal_nya = (int)($nilai['total_soal'] ?? count($soal_latihan));

// Latihan boleh dikerjakan ulang selama jendela waktunya masih terbuka
if (!empty($ujian['waktu_tes'])) {
    $waktu_habis_latihan = strtotime($ujian['waktu_tes']) + ((int)($ujian['durasi'] ?? 0) * 60);
    $bisa_diulang = $is_latihan && time() < $waktu_habis_latihan;
} else {
    $bisa_diulang = $is_latihan;
}
?>
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= $is_latihan ? 'Hasil Latihan' : 'Ujian Selesai' ?> - <?= htmlspecialchars($ujian['judul_ujian']) ?></title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@200..900&display=swap" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet">
    <style>
        body { font-family: 'Source Sans 3', sans-serif; }
        .btn-primary-custom { background: linear-gradient(135deg, #850027 0%, #ac153a 100%); color: white; transition: all 0.3s ease; }
        .btn-primary-custom:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(133, 0, 39, 0.3); }
        /* Supaya tabel dari soal yang dibuat guru tetap tampil rapi */
        .ck-content table { border-collapse: collapse; width: 100%; margin: 6px 0; }
        .ck-content table td, .ck-content table th { border: 1px solid #ddd; padding: 6px 8px; font-size: 13px; }
    </style>
</head>
<body class="bg-[#fbf9f8] font-sans">
    <div class="min-h-screen flex items-center justify-center p-4">
        <div class="max-w-2xl w-full bg-white rounded-2xl shadow-[0_4px_24px_rgba(0,0,0,0.06)] border border-[#e1bec0] p-8 md:p-10">
            <div class="text-center">
                <!-- Ikon Ceklis Besar -->
                <div class="w-24 h-24 rounded-full <?= $is_latihan ? 'bg-purple-100' : 'bg-green-100' ?> flex items-center justify-center mx-auto mb-6">
                    <span class="material-symbols-outlined text-5xl <?= $is_latihan ? 'text-purple-600' : 'text-green-600' ?>">
                        <?= $is_latihan ? 'school' : 'task_alt' ?>
                    </span>
                </div>
                
                <h1 class="text-3xl font-bold text-gray-800 mb-2"><?= $is_latihan ? 'Latihan Selesai!' : 'Ujian Selesai!' ?></h1>
                <p class="text-lg font-semibold text-[#850027]"><?= htmlspecialchars($ujian['judul_ujian']) ?></p>
                <p class="text-gray-500 text-sm mt-1">
                    <span class="material-symbols-outlined text-[14px] align-middle">menu_book</span> <?= htmlspecialchars($ujian['mapel']) ?> • 
                    <span class="material-symbols-outlined text-[14px] align-middle">group</span> Kelas <?= htmlspecialchars($nilai['kelas']) ?>
                </p>
                <?php if ($is_latihan): ?>
                    <p class="inline-flex items-center gap-1 mt-3 text-[11px] font-bold uppercase tracking-wide bg-purple-50 text-purple-700 border border-purple-200 rounded-full px-3 py-1">
                        Latihan
                    </p>
                <?php endif; ?>
            </div>
            
            <?php if ($is_latihan): ?>
            <!-- ============================== -->
            <!-- HASIL PEKERJAAN - TIPE LATIHAN -->
            <!-- ============================== -->

            <!-- Ringkasan nilai -->
            <div class="mt-8 grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div class="rounded-xl border border-[#e1bec0] bg-[#fbf9f8] p-4 text-center">
                    <p class="text-xs text-gray-500 mb-1">Nilai</p>
                    <p class="text-3xl font-bold text-[#850027]"><?= $nilai_total ?></p>
                </div>
                <div class="rounded-xl border border-green-200 bg-green-50 p-4 text-center">
                    <p class="text-xs text-gray-500 mb-1">Benar</p>
                    <p class="text-2xl font-bold text-green-700"><?= $jumlah_benar_detail ?></p>
                </div>
                <div class="rounded-xl border border-red-200 bg-red-50 p-4 text-center">
                    <p class="text-xs text-gray-500 mb-1">Salah</p>
                    <p class="text-2xl font-bold text-red-600"><?= $jumlah_salah_detail ?></p>
                </div>
                <div class="rounded-xl border border-gray-200 bg-gray-50 p-4 text-center">
                    <p class="text-xs text-gray-500 mb-1">Total Soal</p>
                    <p class="text-2xl font-bold text-gray-700"><?= $total_soal_nya ?></p>
                </div>
            </div>

            <div class="mt-4 p-4 bg-purple-50/60 border border-purple-100 rounded-xl text-center">
                <p class="text-gray-700 text-sm leading-relaxed">
                    Ini adalah <strong>latihan</strong>, jadi nilainya bisa langsung kamu lihat.
                    Pelajari kembali soal yang masih salah ya, lalu coba lagi sampai paham. 💪
                </p>
            </div>

            <!-- Rincian per soal -->
            <?php if (count($soal_latihan) > 0): ?>
            <div class="mt-8">
                <h2 class="text-base font-bold text-gray-700 mb-4 flex items-center gap-2">
                    <span class="material-symbols-outlined text-[20px]">fact_check</span>
                    Rincian Pekerjaanmu
                </h2>

                <div class="space-y-4">
                <?php
                $no_soal = 1;
                foreach ($soal_latihan as $soal):
                    $id_soal      = $soal['id'];
                    $tipe_soal    = $soal['tipe_soal'];
                    $detail       = $detail_jawaban[$id_soal] ?? [];
                    $jawaban_user = (string)($detail['jawaban'] ?? '');
                    $kunci        = (string)($soal['kunci_jawaban'] ?? '');
                    $poin         = (int)($detail['skor'] ?? 0);
                    $skor_maks    = (int)($soal['skor'] ?? 0);

                    $label_tipe_soal = ['pg' => 'Pilihan Ganda', 'pg_kompleks' => 'PG Kompleks', 'benar_salah' => 'Benar/Salah', 'benar_salah_tabel' => 'Benar/Salah Tabel', 'uraian' => 'Uraian'][$tipe_soal] ?? $tipe_soal;
                    $is_uraian = ($tipe_soal === 'uraian');

                    if (isset($detail['benar'])) {
                        $is_benar = (bool)$detail['benar'];
                    } else {
                        $is_benar = (!$is_uraian && $jawaban_user !== '' && $jawaban_user === $kunci);
                    }

                    // --- Teks jawaban siswa yang mudah dibaca ---
                    $teks_jawaban = '';
                    if ($jawaban_user === '') {
                        $teks_jawaban = '';
                    } elseif ($is_uraian) {
                        $teks_jawaban = nl2br(htmlspecialchars($jawaban_user));
                    } elseif ($tipe_soal === 'pg') {
                        $huruf = strtoupper($jawaban_user);
                        $opsi  = $soal['opsi_' . strtolower($huruf)] ?? '';
                        $teks_jawaban = htmlspecialchars($huruf) . ($opsi ? '. <span class="ck-content">' . $opsi . '</span>' : '');
                    } elseif ($tipe_soal === 'pg_kompleks') {
                        $bagian = [];
                        foreach (array_filter(explode(',', $jawaban_user)) as $h) {
                            $h = strtoupper(trim($h));
                            $opsi = $soal['opsi_' . strtolower($h)] ?? '';
                            $bagian[] = htmlspecialchars($h) . ($opsi ? '. <span class="ck-content">' . $opsi . '</span>' : '');
                        }
                        $teks_jawaban = implode('<br>', $bagian);
                    } elseif ($tipe_soal === 'benar_salah_tabel') {
                        $arr = explode(',', $jawaban_user);
                        $bagian = []; $idx = 0;
                        foreach (['a', 'b', 'c', 'd', 'e'] as $opt) {
                            if (empty($soal['opsi_' . $opt])) continue;
                            $val = strtoupper(trim($arr[$idx] ?? ''));
                            $bagian[] = ($idx + 1) . '. ' . htmlspecialchars($soal['opsi_' . $opt]) . ' → <strong>' . ($val !== '' ? $val : '—') . '</strong>';
                            $idx++;
                        }
                        $teks_jawaban = implode('<br>', $bagian);
                    } else {
                        $teks_jawaban = htmlspecialchars(strtoupper($jawaban_user));
                    }

                    // --- Teks kunci jawaban ---
                    $teks_kunci = '';
                    if (!$is_uraian) {
                        $teks_kunci = ($tipe_soal === 'benar_salah_tabel')
                            ? strtoupper(implode(', ', explode(',', $kunci)))
                            : strtoupper($kunci);
                    }
                ?>
                <div class="rounded-xl border <?= $is_uraian ? 'border-gray-200' : ($is_benar ? 'border-green-200' : 'border-red-200') ?> overflow-hidden">
                    <div class="flex items-center justify-between gap-2 px-4 py-3 <?= $is_uraian ? 'bg-gray-50 border-gray-200' : ($is_benar ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200') ?> border-b">
                        <div class="flex items-center gap-2 min-w-0">
                            <span class="w-7 h-7 shrink-0 rounded-full bg-white border border-gray-200 flex items-center justify-center text-xs font-bold text-gray-700"><?= $no_soal ?></span>
                            <span class="text-xs font-semibold text-gray-600"><?= htmlspecialchars($label_tipe_soal) ?></span>
                        </div>
                        <?php if ($is_uraian): ?>
                            <span class="text-[11px] font-bold px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 whitespace-nowrap">Menunggu koreksi guru</span>
                        <?php elseif ($is_benar): ?>
                            <span class="text-[11px] font-bold px-2 py-0.5 rounded-full bg-green-100 text-green-700 whitespace-nowrap flex items-center gap-1">
                                <span class="material-symbols-outlined text-[13px]">check_circle</span> Benar (+<?= $poin ?>/<?= $skor_maks ?>)
                            </span>
                        <?php else: ?>
                            <span class="text-[11px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-700 whitespace-nowrap flex items-center gap-1">
                                <span class="material-symbols-outlined text-[13px]">cancel</span> Salah (0/<?= $skor_maks ?>)
                            </span>
                        <?php endif; ?>
                    </div>

                    <div class="p-4">
                        <div class="text-[14px] text-gray-800 leading-relaxed mb-3 ck-content"><?= $soal['pertanyaan'] ?></div>

                        <div class="rounded-lg bg-gray-50 border border-gray-100 p-3">
                            <p class="text-[11px] font-bold uppercase tracking-wide text-gray-400 mb-1">Jawabanmu</p>
                            <?php if ($teks_jawaban === ''): ?>
                                <p class="text-[13px] text-gray-400 italic">Tidak dijawab</p>
                            <?php else: ?>
                                <p class="text-[13px] text-gray-800 leading-relaxed"><?= $teks_jawaban ?></p>
                            <?php endif; ?>
                        </div>

                        <?php if (!$is_uraian): ?>
                        <div class="rounded-lg bg-green-50/60 border border-green-100 p-3 mt-2">
                            <p class="text-[11px] font-bold uppercase tracking-wide text-green-700 mb-1">Kunci Jawaban</p>
                            <p class="text-[13px] text-green-800 font-semibold"><?= htmlspecialchars($teks_kunci) ?></p>
                        </div>
                        <?php endif; ?>
                    </div>
                </div>
                <?php
                    $no_soal++;
                endforeach;
                ?>
                </div>
            </div>
            <?php endif; ?>

            <?php else: ?>
            <!-- ============================ -->
            <!-- TAMPILAN UJIAN BIASA (tetap) -->
            <!-- ============================ -->

            <!-- Kotak Pesan Apresiasi -->
            <div class="mt-8 p-6 bg-blue-50/50 border border-blue-100 rounded-xl text-center">
                <p class="text-gray-700 leading-relaxed text-base md:text-lg">
                    Selamat, kamu telah berhasil menyelesaikan asesmen ini! 🎉<br><br>
                    Terima kasih telah mengerjakan evaluasi ini dengan <strong>jujur</strong> dan penuh <strong>tanggung jawab</strong>. Jawabanmu sudah terekam aman di sistem dan sedang menunggu proses penilaian (koreksi) dari Bapak/Ibu Guru.<br><br>
                    Tetap semangat belajar!
                </p>
            </div>
            
            <?php endif; ?>

            <!-- Tombol Navigasi -->
            <div class="mt-8 flex flex-col sm:flex-row justify-center gap-3">
                <?php if ($bisa_diulang): ?>
                <form method="POST" action="ulangi_latihan.php?id=<?= $id_ujian ?>" onsubmit="return confirm('Kerjakan ulang latihan ini? Hasil percobaan sebelumnya akan diganti.');">
                    <button type="submit" class="w-full px-8 py-3 rounded-lg font-bold flex items-center justify-center gap-2 text-white" style="background: #6a1b9a; transition: all 0.3s ease; cursor: pointer; border: none;">
                        <span class="material-symbols-outlined text-[20px]">refresh</span>
                        Kerjakan Ulang
                    </button>
                </form>
                <?php endif; ?>

                <a href="index.php" class="btn-primary-custom px-8 py-3 rounded-lg font-bold flex items-center gap-2 justify-center">
                    <span class="material-symbols-outlined text-[20px]">home</span>
                    Kembali ke Dashboard
                </a>
            </div>

            <?php if ($is_latihan && !$bisa_diulang): ?>
                <p class="mt-4 text-center text-xs text-gray-400">
                    Waktu latihan ini sudah berakhir, jadi belum bisa diulang. Hubungi gurumu bila ingin dibuka kembali.
                </p>
            <?php endif; ?>
        </div>
    </div>

    <script>
        // Halaman ini hanya tampil kalau baris nilai sudah ada di server, jadi
        // lembar jawaban benar-benar tersimpan. Cache jawaban di perangkat baru
        // dihapus di titik ini — bukan saat mengirim — supaya pengiriman yang
        // gagal tidak menghilangkan pekerjaan siswa. Kunci "pending" yang
        // dipakai untuk memulihkan pengiriman gagal ikut dibersihkan.
        try {
            const kunciJawaban = <?= json_encode('esmansa_ujian_' . (int) $id_ujian . '_' . ($_SESSION['nis'] ?? '')) ?>;
            localStorage.removeItem(kunciJawaban);
            localStorage.removeItem(kunciJawaban + '_pending');
        } catch (err) {}
    </script>
</body>
</html>
