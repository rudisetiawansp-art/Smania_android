<?php
// kerjakan_ujian.php (Versi OFFLINE-FIRST + FULL ANTI-CHEAT)
session_start();
date_default_timezone_set('Asia/Jakarta');
if (!isset($_SESSION['status']) || $_SESSION['status'] != "login") {
    header("location: login.php");
    exit;
}

// ============================================================
// PEMBATASAN BROWSER (USER-AGENT CHECK)
// ============================================================
function browserDiizinkan($ua) {
    if (empty($ua)) return false;
    $blokir = ['MiuiBrowser', 'SamsungBrowser', 'UCBrowser', 'UCWEB', 'OPR/', 'OPT/', 'OPiOS', 'HeyTapBrowser', 'VivoBrowser', 'HuaweiBrowser', 'EdgA/', 'EdgiOS', 'FBAN', 'FBAV', 'Instagram', 'MicroMessenger', 'Line/', 'Snapchat', 'TikTok', 'musical_ly', 'GSA/', '; wv)'];
    foreach ($blokir as $kata) { if (stripos($ua, $kata) !== false) return false; }
    // PERBAIKAN FATAL #2: Chrome di iPhone/iPad (UA memakai "CriOS/") dulu
    // selalu ditolak, padahal halaman ini sendiri menyuruh siswa memakai
    // Chrome. Akibatnya siswa iOS pengguna Chrome tidak bisa masuk ujian.
    $chromeAsli = (stripos($ua, 'Chrome/') !== false) || (stripos($ua, 'CriOS/') !== false);
    $safariAsli = (stripos($ua, 'Safari/') !== false && stripos($ua, 'Version/') !== false && stripos($ua, 'CriOS') === false && stripos($ua, 'FxiOS') === false && stripos($ua, 'Chrome/') === false);
    return $chromeAsli || $safariAsli;
}

if (!browserDiizinkan($_SERVER['HTTP_USER_AGENT'] ?? '')) {
    echo '<!DOCTYPE html><html lang="id"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Browser Tidak Didukung</title><style>body{font-family:sans-serif;background:#fff7ed;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:16px;}.box{background:#fff;max-width:440px;padding:32px;border-radius:16px;text-align:center;border:1px solid #fed7aa;}</style></head><body><div class="box"><h2>⚠️ Browser Tidak Didukung</h2><p>Gunakan Google Chrome atau Safari.</p></div></body></html>';
    exit;
}

// ============================================================
// PERBAIKAN FATAL #1: JSON aman untuk <script> maupun database
// Dulu jawaban siswa ditempel MENTAH ke dalam blok <script> (json_encode
// tanpa JSON_HEX_*), padahal isinya berasal dari ketikan siswa. Satu jawaban
// uraian yang memuat "</script>" — misalnya pada soal HTML — memutus blok
// script itu sehingga SELURUH halaman ujian mati: semua kartu soal tetap
// display:none, timer tidak jalan, dan tombol "Kumpulkan" tidak berfungsi.
// Sisa teksnya pun tereksekusi sebagai HTML/JS. JSON_INVALID_UTF8_SUBSTITUTE
// mencegah json_encode() mengembalikan false saat ada byte non-UTF8, sebab
// nilai false itu dulu membuat kolom jawaban tersimpan kosong.
// ============================================================
function jsonAman($data, $bendera = 0) {
    if (defined('JSON_INVALID_UTF8_SUBSTITUTE')) {
        $bendera |= JSON_INVALID_UTF8_SUBSTITUTE;
    }
    $json = json_encode($data, $bendera | JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT);
    return ($json === false) ? '{}' : $json;
}

// Koneksi ke database ESMANSA (bank soal, jawaban, nilai).
// Dulu file ini juga meng-include ../koneksi.php (database SMANIA) padahal tidak
// ada satu pun yang memakainya, sehingga setiap request — termasuk ping tiap 3
// menit dari semua siswa — membuka dua koneksi sekaligus.
// Catatan: kredensial ini masih hardcode dan berbeda antar file (sebagian memakai
// user 'smansuko_esmansa'). Sebaiknya segera dipindah ke satu file config.
$conn_esmansa = mysqli_connect("localhost", "root", "", "smansuko_esmansa");
if (!$conn_esmansa) {
    die("Koneksi database gagal. Hubungi guru pengawas.");
}

$nis = mysqli_real_escape_string($conn_esmansa, $_SESSION['nis']);
$nama = mysqli_real_escape_string($conn_esmansa, $_SESSION['nama']);
$kelas = mysqli_real_escape_string($conn_esmansa, $_SESSION['kelas']);
$id_ujian = isset($_GET['id']) ? (int)$_GET['id'] : 0;

if ($id_ujian == 0) { header("Location: dashboard.php"); exit; }

// ============================================================
// DATA UJIAN (diambil lebih awal supaya tipe bisa dicek duluan)
// ============================================================
$query_ujian = mysqli_query($conn_esmansa, "SELECT * FROM ujian WHERE id = $id_ujian AND FIND_IN_SET('$kelas', REPLACE(kelas, ', ', ',')) > 0");
if (mysqli_num_rows($query_ujian) == 0) die("Ujian tidak ditemukan.");
$ujian = mysqli_fetch_assoc($query_ujian);

// Tipe "latihan": tidak perlu kode akses guru & tidak dikenai blokir anti-cheat
$is_latihan = (($ujian['tipe'] ?? 'ujian') == 'latihan');

// ============================================================
// BATAS WAKTU UJIAN (dihitung di server, bukan cuma di browser)
// ============================================================
$durasi_ujian_detik = ((int) ($ujian['durasi'] ?? 0)) * 60;
$toleransi_waktu = 60; // slack untuk kiriman yang sedang berjalan
// Ambang blokir pelanggaran: satu sumber kebenaran untuk server dan klien
// (sebelumnya angka 3 ditulis ulang di tiga tempat yang berbeda).
$batas_pelanggaran = 3;

// Endpoint AJAX (ping/autosave/pelanggaran) tidak boleh lagi menulis jawaban
// setelah batas waktu habis, supaya jawaban tidak bisa ditambah belakangan.
if (isset($_POST['ping']) || isset($_POST['autosave_jawaban']) || isset($_POST['catat_pelanggaran'])) {
    if ($durasi_ujian_detik > 0) {
        $q_waktu_lembar = mysqli_query($conn_esmansa, "SELECT waktu_mulai FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' ORDER BY id DESC LIMIT 1");
        if ($q_waktu_lembar && ($row_waktu_lembar = mysqli_fetch_assoc($q_waktu_lembar))) {
            if (time() > strtotime($row_waktu_lembar['waktu_mulai']) + $durasi_ujian_detik + $toleransi_waktu) {
                echo json_encode(['status' => 'expired']);
                exit;
            }
        }
    }
}

// ============================================================
// ENDPOINT AJAX: PING & ANTI-CHEAT
// ============================================================
if (isset($_POST['ping'])) {
    if (isset($_POST['data_jawaban']) && json_decode($_POST['data_jawaban'], true) !== null) {
        $jawaban_ping = mysqli_real_escape_string($conn_esmansa, $_POST['data_jawaban']);
        mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET jawaban = '$jawaban_ping', terakhir_aktif = NOW() WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'");
    } else {
        mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET terakhir_aktif = NOW() WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'");
    }
    echo json_encode(['status' => 'ok']); exit;
}

if (isset($_POST['catat_pelanggaran'])) {
    // Latihan tidak mencatat pelanggaran
    if ($is_latihan) { echo json_encode(['status' => 'ignored']); exit; }
    $waktu_sekarang = time();
    if (!isset($_SESSION['last_pelanggaran_'.$id_ujian]) || ($waktu_sekarang - $_SESSION['last_pelanggaran_'.$id_ujian]) >= 2) {
        mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET pelanggaran = pelanggaran + 1, pelanggaran_total = pelanggaran_total + 1, terakhir_aktif = NOW() WHERE id_ujian = $id_ujian AND nis = '$nis' AND status IN ('sedang', 'diblokir')");
        $_SESSION['last_pelanggaran_'.$id_ujian] = $waktu_sekarang;
    }
    if (isset($_POST['data_jawaban_sync'])) {
        $jawaban_sync = mysqli_real_escape_string($conn_esmansa, $_POST['data_jawaban_sync']);
        if (json_decode($jawaban_sync, true) !== null) {
            mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET jawaban = '$jawaban_sync', terakhir_aktif = NOW() WHERE id_ujian = $id_ujian AND nis = '$nis' AND status IN ('sedang', 'diblokir')");
        }
    }
    if (isset($_POST['hapus_token'])) unset($_SESSION['kode_terverifikasi_' . $id_ujian]);
    echo json_encode(['status' => 'recorded']); exit;
}

if (isset($_POST['autosave_jawaban'])) {
    $jawaban_autosave = mysqli_real_escape_string($conn_esmansa, $_POST['data_jawaban'] ?? '{}');
    if (json_decode($jawaban_autosave, true) !== null) {
        mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET jawaban = '$jawaban_autosave', terakhir_aktif = NOW() WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'");
        echo json_encode(['status' => 'saved']);
    } else {
        echo json_encode(['status' => 'invalid']);
    }
    exit;
}

if (isset($_POST['cek_status'])) {
    // Latihan tidak pernah diblokir
    if ($is_latihan) { echo json_encode(['status' => 'ok', 'pelanggaran' => 0]); exit; }
    $cek_pel = mysqli_query($conn_esmansa, "SELECT pelanggaran FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' AND status IN ('sedang', 'diblokir')");
    $baris_pel = $cek_pel ? mysqli_fetch_assoc($cek_pel) : null;
    $pelanggaran_baru = $baris_pel ? (int) ($baris_pel['pelanggaran'] ?? 0) : 0;
    if ($pelanggaran_baru >= $batas_pelanggaran) {
        mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET status = 'diblokir' WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'");
        unset($_SESSION['kode_terverifikasi_' . $id_ujian]);
        echo json_encode(['status' => 'logout', 'message' => "Akses dikunci! Anda melakukan pelanggaran $batas_pelanggaran kali. Ujian ini harus direset oleh guru."]); exit;
    }
    echo json_encode(['status' => 'ok', 'pelanggaran' => $pelanggaran_baru]); exit;
}

// ============================================================
// VERIFIKASI KODE (dilewati untuk tipe Latihan)
// ============================================================
if (!$is_latihan) {
    $token_ujian = $_SESSION['kode_terverifikasi_' . $id_ujian] ?? null;

    // Kalau sudah ada lembar yang sedang dikerjakan, siswa boleh lanjut tanpa
    // memasukkan kode lagi supaya tidak terlempar ke halaman verifikasi di
    // tengah ujian.
    $ada_lembar_sedang = false;
    $q_lembar_sedang = mysqli_query($conn_esmansa, "SELECT COUNT(*) AS total FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'");
    if ($q_lembar_sedang) {
        $ada_lembar_sedang = ((int) (mysqli_fetch_assoc($q_lembar_sedang)['total'] ?? 0)) > 0;
    }

    // Untuk MEMULAI lembar baru, verifikasi harus masih segar karena kode akses
    // guru berganti tiap 5 menit. Sebelumnya token di sesi berlaku selamanya,
    // jadi ujian masih bisa dimulai berjam-jam setelah verifikasi.
    $waktu_verifikasi = $_SESSION['kode_terverifikasi_waktu_' . $id_ujian] ?? ($_SESSION['kode_terverifikasi_waktu'] ?? null);
    $token_segar = is_numeric($waktu_verifikasi) && (time() - (int) $waktu_verifikasi) <= 600;

    if ($token_ujian !== true || (!$ada_lembar_sedang && !$token_segar)) {
        header("Location: verifikasi_kode.php?id=$id_ujian"); exit;
    }
}

$query_cek = mysqli_query($conn_esmansa, "SELECT id FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'selesai'");
if (mysqli_num_rows($query_cek) > 0) { header("Location: lihat_hasil_siswa.php?id=$id_ujian"); exit; }

function tampilkanHalamanDiblokir() {
    echo '<!DOCTYPE html><html lang="id"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Ujian Dikunci</title><link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet"><style>body{font-family:"Roboto",sans-serif;background:#fef2f2;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:16px;}.box{background:#fff;max-width:440px;width:100%;padding:32px;border-radius:16px;box-shadow:0 4px 24px rgba(0,0,0,.1);text-align:center;border:1px solid #fee2e2;}.icon{font-size:56px;margin-bottom:16px;}h2{color:#d32f2f;margin:0 0 12px;font-size:20px;}p{color:#555;line-height:1.6;margin:8px 0;font-size:14px;}.small{color:#888;font-size:12px;margin-top:16px;padding-top:16px;border-top:1px dashed #eee;}</style></head><body><div class="box"><div class="icon">🔒</div><h2>Ujian Anda Dikunci</h2><p>Sistem mendeteksi pelanggaran (pindah tab, minimize, atau membuka aplikasi lain).</p><p class="small">Akses ujian ini hanya bisa dibuka kembali oleh guru. Silakan hubungi guru pengawas untuk mereset akses Anda.</p></div></body></html>'; exit;
}

if (!$is_latihan) {
    $q_cek_blokir = mysqli_query($conn_esmansa, "SELECT id FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'diblokir'");
    if (mysqli_num_rows($q_cek_blokir) > 0) { unset($_SESSION['kode_terverifikasi_' . $id_ujian]); tampilkanHalamanDiblokir(); }
}

// ============================================================
// AMBIL SOAL (BULK INSERT PATTERN)
// ============================================================
function ambilSoalAcak($conn, $id_ujian, $nis, $jml_pg, $jml_uraian) {
    $jml_pg = max(0, (int) $jml_pg);
    $jml_uraian = max(0, (int) $jml_uraian);

    // Kalau guru belum mengatur jumlah pengambilan (dua-duanya 0 / NULL), pakai
    // seluruh bank soal. Dulu nilai kosong ini masuk mentah ke klausa LIMIT,
    // sehingga query error dan halaman ujian gagal dimuat.
    $pakai_seluruh_bank = ($jml_pg === 0 && $jml_uraian === 0);

    $q_cek = mysqli_query($conn, "SELECT COUNT(*) as total FROM soal_acak_siswa WHERE id_ujian = $id_ujian AND nis = '$nis'");
    $jumlah_tersimpan = $q_cek ? (int) (mysqli_fetch_assoc($q_cek)['total'] ?? 0) : 0;

    if ($jumlah_tersimpan === 0) {
        $soal_terpilih_id = [];

        // 'benar_salah' wajib ada di daftar tipe: tanpa itu soal Benar/Salah yang
        // dibuat guru tidak pernah ikut terundi ke ujian.
        $sql_pg = "SELECT id FROM soal_ujian WHERE id_ujian = $id_ujian AND tipe_soal IN ('pg', 'pg_kompleks', 'benar_salah', 'benar_salah_tabel') ORDER BY RAND()";
        if (!$pakai_seluruh_bank && $jml_pg > 0) {
            $sql_pg .= " LIMIT $jml_pg";
        }
        $q_pg = mysqli_query($conn, $sql_pg);
        if ($q_pg) { while ($row = mysqli_fetch_assoc($q_pg)) { $soal_terpilih_id[] = $row['id']; } }

        $sql_uraian = "SELECT id FROM soal_ujian WHERE id_ujian = $id_ujian AND tipe_soal = 'uraian' ORDER BY RAND()";
        if (!$pakai_seluruh_bank && $jml_uraian > 0) {
            $sql_uraian .= " LIMIT $jml_uraian";
        }
        $q_uraian = mysqli_query($conn, $sql_uraian);
        if ($q_uraian) { while ($row = mysqli_fetch_assoc($q_uraian)) { $soal_terpilih_id[] = $row['id']; } }

        shuffle($soal_terpilih_id);
        if (!empty($soal_terpilih_id)) {
            $values = []; $urutan = 1;
            foreach ($soal_terpilih_id as $id_soal) { $values[] = "($id_ujian, '$nis', $id_soal, $urutan)"; $urutan++; }
            // Kalau penulisan gagal (mis. dua tab dibuka bersamaan), undian yang
            // sudah tersimpan tetap dipakai — dibaca ulang di bawah.
            mysqli_query($conn, "INSERT INTO soal_acak_siswa (id_ujian, nis, id_soal, urutan) VALUES " . implode(", ", $values));
        }
    }

    $q_soal = mysqli_query($conn, "SELECT s.* FROM soal_acak_siswa sa JOIN soal_ujian s ON sa.id_soal = s.id WHERE sa.id_ujian = $id_ujian AND sa.nis = '$nis' ORDER BY sa.urutan ASC");
    $soal_list = [];
    $sudah_diambil = [];
    if ($q_soal) {
        while ($row = mysqli_fetch_assoc($q_soal)) {
            // Jaga-jaga kalau undian tercatat dua kali: soal yang sama tidak boleh
            // muncul dua kali di lembar siswa.
            if (isset($sudah_diambil[$row['id']])) { continue; }
            $sudah_diambil[$row['id']] = true;
            $soal_list[] = $row;
        }
    }
    return $soal_list;
}

$soal_list = ambilSoalAcak($conn_esmansa, $id_ujian, $nis, $ujian['jml_pg'], $ujian['jml_uraian']);
$total_soal = count($soal_list);
if ($total_soal == 0) die("Belum ada soal untuk ujian ini.");

// Inisialisasi Database Jawaban (Resume)
$query_sedang = mysqli_query($conn_esmansa, "SELECT * FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang' ORDER BY id DESC LIMIT 1");
$jawaban_siswa = $query_sedang ? mysqli_fetch_assoc($query_sedang) : null;

if (!$jawaban_siswa) {
    // Belum ada lembar berjalan. Penulisan dibuat atomik (INSERT ... ON DUPLICATE
    // KEY UPDATE) supaya dua request yang datang bersamaan — misalnya halaman
    // dimuat sementara beacon ikut jalan — tidak menghasilkan dua baris lembar
    // untuk siswa yang sama. Ini perlu UNIQUE KEY (id_ujian, nis) di tabel.
    mysqli_query($conn_esmansa, "INSERT INTO jawaban_siswa (id_ujian, nis, nama_siswa, kelas, waktu_mulai, status, jawaban, soal_terakhir, pelanggaran, terakhir_aktif) VALUES ($id_ujian, '$nis', '$nama', '$kelas', NOW(), 'sedang', '{}', 1, 0, NOW()) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)");
    $id_lembar = (int) mysqli_insert_id($conn_esmansa);
    if ($id_lembar > 0) {
        $q_lembar = mysqli_query($conn_esmansa, "SELECT * FROM jawaban_siswa WHERE id = $id_lembar");
        $jawaban_siswa = $q_lembar ? mysqli_fetch_assoc($q_lembar) : null;
    }
    if (!$jawaban_siswa) {
        $q_lembar = mysqli_query($conn_esmansa, "SELECT * FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' ORDER BY id DESC LIMIT 1");
        $jawaban_siswa = $q_lembar ? mysqli_fetch_assoc($q_lembar) : null;
    }
    if (!$jawaban_siswa) {
        die("Gagal menyiapkan lembar jawaban. Silakan muat ulang halaman ini.");
    }
}

$jawaban_data = json_decode($jawaban_siswa['jawaban'] ?? '', true) ?: [];
$pelanggaran = (int) ($jawaban_siswa['pelanggaran'] ?? 0);
if ($is_latihan) {
    // Latihan tidak pernah diblokir
    $pelanggaran = 0;
} elseif ($pelanggaran >= $batas_pelanggaran) {
    mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET status = 'diblokir' WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'");
    unset($_SESSION['kode_terverifikasi_' . $id_ujian]); tampilkanHalamanDiblokir();
}

// ============================================================
// BATAS WAKTU LEMBAR INI
// ============================================================
$waktu_mulai_db = strtotime($jawaban_siswa['waktu_mulai']);
$waktu_selesai = $waktu_mulai_db + $durasi_ujian_detik;
$sisa_detik = max(0, $waktu_selesai - time());
$batas_submit = $waktu_selesai + $toleransi_waktu;

// Waktu habis sementara lembar masih 'sedang' dan halaman dimuat ulang: sistem
// menyelesaikan sendiri memakai jawaban terakhir yang tersimpan di server. Dulu
// timer hanya ada di browser, sehingga siswa bisa terus menjawab setelah waktu
// habis asalkan menolak dialog konfirmasi.
if ($durasi_ujian_detik > 0 && $sisa_detik <= 0 && !isset($_POST['submit_ujian_offline'])) {
    $_POST['submit_ujian_offline'] = '1';
    $_POST['data_jawaban_lokal'] = jsonAman(is_array($jawaban_data) ? $jawaban_data : []);
}

// ============================================================
// PENGUMPULAN GAGAL: tampilkan pesan, JANGAN tandai lembar selesai
// ============================================================
function hentikanSubmit($pesan, $id_ujian)
{
    http_response_code(400);
    $pesan_aman = htmlspecialchars($pesan, ENT_QUOTES, 'UTF-8');
    // PERBAIKAN: tombol "Buka Ulang" mengikuti nama berkas yang benar-benar
    // dipakai, supaya berkas hasil perbaikan ini bisa diuji dulu tanpa
    // melempar siswa ke berkas lama yang belum diperbaiki.
    $nama_berkas_diri = basename($_SERVER['SCRIPT_NAME'] ?? 'kerjakan_ujian.php');
    $link_ulang = htmlspecialchars($nama_berkas_diri, ENT_QUOTES, 'UTF-8') . '?id=' . (int) $id_ujian;
    echo '<!DOCTYPE html><html lang="id"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>Jawaban Belum Terkirim</title><style>body{font-family:"Roboto",sans-serif;background:#fff7ed;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:16px;}.box{background:#fff;max-width:460px;width:100%;padding:32px;border-radius:16px;border:1px solid #fed7aa;box-shadow:0 4px 24px rgba(0,0,0,.08);text-align:center;}.icon{font-size:52px;margin-bottom:12px;}h2{color:#b45309;margin:0 0 10px;font-size:20px;}p{color:#555;line-height:1.6;font-size:14px;margin:8px 0;}.aksi{margin-top:20px;}a{display:inline-block;padding:11px 20px;border-radius:8px;background:#b45309;color:#fff;font-weight:600;font-size:14px;text-decoration:none;}</style></head><body><div class="box"><div class="icon">⚠️</div><h2>Jawaban Belum Terkirim</h2><p>' . $pesan_aman . '</p><p>Ujian Anda <strong>belum</strong> ditutup. Buka ulang halaman ujian lewat tombol di bawah, periksa kembali jawaban, lalu tekan <strong>Kumpulkan</strong> lagi — jawaban terakhir Anda dipulihkan otomatis saat halaman dibuka.</p><div class="aksi"><a href="' . $link_ulang . '">Buka Ulang Halaman Ujian</a></div></div></body></html>';
    exit;
}

// ============================================================
// PROSES PENGUMPULAN AKHIR (SUBMIT DARI LOCAL STORAGE KE SERVER)
// ============================================================
if (isset($_POST['submit_ujian_offline'])) {
    // --- Validasi payload sebelum lembar ditandai selesai -----------------
    // Sebelumnya payload yang hilang atau JSON yang rusak diam-diam dianggap
    // lembar kosong: status langsung 'selesai' dengan nilai 0 dan siswa tidak
    // bisa mengulang. Sekarang ditolak dengan pesan yang bisa ditindaklanjuti.
    $payload_mentah = isset($_POST['data_jawaban_lokal']) ? (string) $_POST['data_jawaban_lokal'] : '';
    if (trim($payload_mentah) === '') {
        hentikanSubmit('Permintaan pengumpulan tidak memuat data jawaban sama sekali.', $id_ujian);
    }
    $jawaban_client = json_decode($payload_mentah, true);
    if (!is_array($jawaban_client)) {
        hentikanSubmit('Data jawaban yang terkirim tidak bisa dibaca karena formatnya rusak.', $id_ujian);
    }

    // Sudah lewat batas waktu? Abaikan payload yang datang terlambat dan pakai
    // salinan terakhir di server, supaya jawaban tidak bisa ditambah setelah
    // waktu ujian berakhir. Toleransi singkat menjaga kiriman di detik terakhir.
    // PERBAIKAN FATAL #3: penggantian payload oleh salinan server HANYA untuk
    // ujian berdurasi. Kalau durasi 0/NULL (tanpa batas waktu), $batas_submit
    // hanya 60 detik setelah lembar dibuat, sehingga setiap pengumpulan
    // sesudahnya diam-diam menimpa jawaban siswa dengan salinan server yang
    // basi (ping hanya tiap 3 menit) — jawaban terakhir siswa hilang atau
    // nilainya 0 tanpa pesan apa pun.
    if ($durasi_ujian_detik > 0 && time() > $batas_submit) {
        $jawaban_client = is_array($jawaban_data) ? $jawaban_data : [];
    }

    // Kalau server sudah menyimpan jawaban (hasil autosave/ping) sementara
    // payload yang masuk kosong, kemungkinan data di sisi siswa hilang. Menimpa
    // lembar dengan jawaban kosong jauh lebih merugikan daripada menolak submit.
    $ada_jawaban_payload = false;
    foreach ($jawaban_client as $item_cek) {
        $nilai_cek = is_array($item_cek) ? ($item_cek['jawaban'] ?? '') : '';
        if (is_scalar($nilai_cek) && trim((string) $nilai_cek) !== '') {
            $ada_jawaban_payload = true;
            break;
        }
    }
    $ada_jawaban_tersimpan = false;
    foreach ((array) $jawaban_data as $item_lama) {
        $nilai_lama = is_array($item_lama) ? ($item_lama['jawaban'] ?? '') : '';
        if (is_scalar($nilai_lama) && trim((string) $nilai_lama) !== '') {
            $ada_jawaban_tersimpan = true;
            break;
        }
    }
    if (!$ada_jawaban_payload && $ada_jawaban_tersimpan) {
        hentikanSubmit('Permintaan ini tidak memuat jawaban apa pun, padahal sistem menyimpan jawaban Anda sebelumnya.', $id_ujian);
    }
    $jawaban_baru = [];
    $total_skor_pg = 0;
    $jawaban_benar = 0;
    $maks_skor_total = 0; // Σ skor semua butir yang diterima siswa (termasuk uraian)
    
    foreach ($soal_list as $soal) {
        $id_soal = $soal['id']; $tipe = $soal['tipe_soal'];
        // Bobot butir diambil dari kolom skor soal: guru bebas menentukan nilai
        // tiap butir, jadi tidak boleh diasumsikan 10 poin.
        $skor_butir = (float) ($soal['skor'] ?? 0);
        $maks_skor_total += $skor_butir;

        // Payload dari browser belum tentu rapi: pastikan bentuknya array dan
        // nilai jawabannya skalar, supaya payload aneh tidak menjatuhkan proses
        // pengumpulan di tengah jalan.
        $item_jawaban = (isset($jawaban_client[$id_soal]) && is_array($jawaban_client[$id_soal])) ? $jawaban_client[$id_soal] : [];
        $jawaban_user = $item_jawaban['jawaban'] ?? '';
        $jawaban_user = is_scalar($jawaban_user) ? trim((string) $jawaban_user) : '';
        $skor_soal = 0; $is_benar = false;
        
        if ($tipe == 'pg') {
            $is_benar = ($jawaban_user == $soal['kunci_jawaban']);
            if ($is_benar) { $skor_soal = $skor_butir; $total_skor_pg += $skor_soal; $jawaban_benar++; }
        } elseif ($tipe == 'pg_kompleks') {
            if (!empty($jawaban_user)) {
                $jawaban_array = explode(',', $jawaban_user); sort($jawaban_array);
                $kunci_array = explode(',', $soal['kunci_jawaban']); sort($kunci_array);
                $is_benar = ($jawaban_array == $kunci_array);
                if ($is_benar) { $skor_soal = $skor_butir; $total_skor_pg += $skor_soal; $jawaban_benar++; }
            }
        } elseif ($tipe == 'benar_salah') {
            $is_benar = ($jawaban_user == $soal['kunci_jawaban']);
            if ($is_benar) { $skor_soal = $skor_butir; $total_skor_pg += $skor_soal; $jawaban_benar++; }
        } elseif ($tipe == 'benar_salah_tabel') {
            if (!empty($jawaban_user)) {
                $jawaban_array = explode(',', $jawaban_user);
                $kunci_array = explode(',', $soal['kunci_jawaban']);
                $is_benar = ($jawaban_array === $kunci_array); 
                if ($is_benar) { $skor_soal = $skor_butir; $total_skor_pg += $skor_soal; $jawaban_benar++; }
            }
        }
        $jawaban_baru[$id_soal] = ['jawaban' => $jawaban_user, 'benar' => $is_benar, 'skor' => $skor_soal, 'tipe' => $tipe, 'kunci' => $soal['kunci_jawaban'] ?? null];
    }
    
    // Nilai dihitung dari Σ skor butir yang benar-benar diterima siswa, bukan
    // asumsi "tiap soal 10 poin". Uraian yang belum dikoreksi dihitung 0 dulu
    // supaya angkanya sama dengan perhitungan ulang guru setelah koreksi uraian.
    $nilai_sementara = ($maks_skor_total > 0) ? ($total_skor_pg / $maks_skor_total) * 100 : 0;
    $nilai_sementara = round(min(100, $nilai_sementara), 2);
    // json_encode() meng-escape petik ganda tetapi TIDAK petik tunggal. Tanpa
    // escaping, satu tanda ' pada jawaban uraian membuat query gagal (dan
    // membuka celah injection) sehingga jawaban serta nilai siswa tidak tersimpan.
    $jawaban_json_final = mysqli_real_escape_string($conn_esmansa, jsonAman($jawaban_baru));

    // Kirim ulang (tombol diklik dua kali, halaman dikirim lagi, atau siswa
    // menekan submit lagi setelah selesai) tidak boleh menimpa lembar yang
    // sudah selesai dan mungkin sudah dinilai guru.
    $q_status_lembar = mysqli_query($conn_esmansa, "SELECT status FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' ORDER BY id DESC LIMIT 1");
    if ($q_status_lembar && (mysqli_fetch_assoc($q_status_lembar)['status'] ?? '') === 'selesai') {
        unset($_SESSION['kode_terverifikasi_' . $id_ujian]);
        header("Location: lihat_hasil_siswa.php?id=$id_ujian");
        exit;
    }

    // Nilai disimpan lebih dulu. Kalau penulisan berikutnya gagal dan tabelnya
    // non-transaksional (MyISAM), lembar masih berstatus 'sedang' sehingga siswa
    // bisa mengirim ulang tanpa kehilangan jawaban.
    mysqli_begin_transaction($conn_esmansa);
    $ok_nilai = mysqli_query($conn_esmansa, "INSERT INTO nilai_ujian (id_ujian, nis, nama_siswa, kelas, nilai_total, jawaban_benar, total_soal, skor_pg, skor_uraian, waktu_selesai) VALUES ($id_ujian, '$nis', '$nama', '$kelas', $nilai_sementara, $jawaban_benar, $total_soal, $total_skor_pg, 0, NOW()) ON DUPLICATE KEY UPDATE nilai_total = $nilai_sementara, jawaban_benar = $jawaban_benar, total_soal = $total_soal, skor_pg = $total_skor_pg, skor_uraian = 0, waktu_selesai = NOW()");

    $ok_lembar = mysqli_query($conn_esmansa, "UPDATE jawaban_siswa SET jawaban = '$jawaban_json_final', skor = $total_skor_pg, waktu_selesai = NOW(), status = 'selesai', status_koreksi = 'belum' WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'");
    $baris_lembar = $ok_lembar ? mysqli_affected_rows($conn_esmansa) : 0;

    // Kedua penulisan harus berhasil bersama-sama. Tanpa pemeriksaan ini siswa
    // bisa berstatus 'selesai' tanpa baris nilai (halaman hasil jadi kosong).
    if ($ok_nilai && $ok_lembar && $baris_lembar > 0) {
        mysqli_commit($conn_esmansa);
    } else {
        mysqli_rollback($conn_esmansa);

        // Kalau prosesnya gagal karena lembar ternyata sudah diblokir, tampilkan
        // halaman terkunci supaya siswa tahu yang harus dilakukan.
        $q_status_kini = mysqli_query($conn_esmansa, "SELECT status FROM jawaban_siswa WHERE id_ujian = $id_ujian AND nis = '$nis' ORDER BY id DESC LIMIT 1");
        $status_kini = $q_status_kini ? (mysqli_fetch_assoc($q_status_kini)['status'] ?? '') : '';
        if ($status_kini === 'diblokir' && !$is_latihan) {
            unset($_SESSION['kode_terverifikasi_' . $id_ujian]);
            tampilkanHalamanDiblokir();
        }

        hentikanSubmit('Jawaban Anda gagal disimpan ke server (lembar tidak dalam status dikerjakan atau salah satu penulisan gagal).', $id_ujian);
    }
    
    unset($_SESSION['kode_terverifikasi_' . $id_ujian]);
    header("Location: lihat_hasil_siswa.php?id=$id_ujian");
    exit;
}

// PERBAIKAN FATAL #1 (lanjutan): dipakai jsonAman() supaya isi jawaban tidak
// bisa memutus blok <script> di bawah ini.
$jawaban_awal_json = jsonAman(is_array($jawaban_data) ? $jawaban_data : []);
?>
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Mengerjakan Ujian - <?= htmlspecialchars($ujian['judul_ujian']) ?></title>
    <link rel="stylesheet" href="../css/light.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
    <script src="https://cdn.tailwindcss.com"></script>
    <style>
        body { background-color: #e5e7eb; margin: 0; display: flex; justify-content: center; align-items: flex-start; min-height: 100vh; }
        .app-wrapper { background-color: var(--md-sys-color-background, #fbf9f8); color: var(--md-sys-color-on-background, #1a1a1a); font-family: 'Roboto', sans-serif; width: 100%; max-width: 480px; min-height: 100vh; position: relative; box-shadow: 0 0 20px rgba(0, 0, 0, 0.1); padding-bottom: 90px; overflow-x: hidden; transition: all 0.3s ease; }
        .header { padding: 16px; background-color: var(--md-sys-color-primary, #1a73e8); color: var(--md-sys-color-on-primary, #ffffff); border-bottom-left-radius: 24px; border-bottom-right-radius: 24px; display: flex; justify-content: space-between; align-items: center; position: sticky; top: 0; z-index: 2000; box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1); margin-bottom: 16px; }
        .header h2 { margin: 0; font-size: 15px; font-weight: 700; line-height: 1.2; text-overflow: ellipsis; white-space: nowrap; overflow: hidden; max-width: 250px;}
        .header p { margin: 4px 0 0; font-size: 11px; opacity: 0.9; }
        .content { padding: 0 16px; }
        .card-lapor { background: white; border-radius: 16px; margin-bottom: 16px; padding: 16px; border: 1px solid #eee; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05); }
        .head-lapor { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; border-bottom: 1px dashed #eee; padding-bottom: 12px; }
        .lapor-title { margin: 0; font-size: 15px; font-weight: bold; color: #333; }
        .guru-tujuan { display: inline-block; margin-top: 4px; font-size: 10px; color: #1565c0; background: #e3f2fd; padding: 4px 8px; border-radius: 6px; font-weight: bold; }
        .status { padding: 4px 8px; border-radius: 6px; font-size: 11px; font-weight: bold; display: inline-flex; align-items: center; gap: 4px; }
        .status-diproses { background: #e3f2fd; color: #1565c0; }
        .avatar { width: 30px; height: 30px; border-radius: 50%; background: #e3f2fd; color: #1a73e8; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 13px; }
        .timer-box { font-family: monospace; font-size: 13px; font-weight: bold; background: rgba(255, 255, 255, 0.2); padding: 4px 10px; border-radius: 8px; text-align: center; }
        .timer-warning { background: rgba(211, 47, 47, 0.9); animation: pulse 1s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.7; } }
        .opsi-label { border: 1px solid #eee; border-radius: 12px; transition: background 0.2s; display: flex; padding: 12px; align-items: flex-start; gap: 10px; cursor: pointer; margin-bottom: 10px;}
        .opsi-label:hover { background: #fafafa; border-color: #ddd; }
        .opsi-label.selected { border-color: #1a73e8; background: #f0f7ff; }
        .opsi-label input { margin-top: 2px; }
        .btn-nav { font-size: 12px; font-weight: 600; padding: 10px 14px; border-radius: 8px; display: inline-flex; align-items: center; gap: 4px; cursor: pointer; transition: 0.2s; border: none; text-decoration: none;}
        .btn-nav-outline { background: white; color: #1a73e8; border: 1px solid #1a73e8; }
        .btn-nav-primary { background: var(--md-sys-color-primary, #1a73e8); color: white; }
        .btn-nav-success { background: #2e7d32; color: white; width: 100%; justify-content: center; margin-top: 10px;}
        .btn-nav:disabled { opacity: 0.5; cursor: not-allowed; border-color: #ccc; color: #999; }
        .soal-nav-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px; margin-top: 10px; }
        .soal-nav-item { padding: 6px 0; text-align: center; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; display: block; border: 1px solid transparent; background: #f5f5f5; color: #757575; border-color: #eee; transition: 0.2s;}
        .soal-nav-item.active { background: #e3f2fd !important; color: #1a73e8 !important; border-color: #1a73e8 !important; }
        .soal-nav-item.answered { background: #e8f5e9; color: #2e7d32; border-color: #c8e6c9; }
        .overlay-logout { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8); display: flex; align-items: center; justify-content: center; z-index: 9999; backdrop-filter: blur(4px); }
        .toast-notification { position: fixed; top: 80px; left: 50%; transform: translateX(-50%); padding: 10px 16px; border-radius: 8px; color: white; font-weight: 500; font-size: 13px; z-index: 9998; box-shadow: 0 4px 12px rgba(0,0,0,0.15); animation: slideIn 0.3s ease; white-space: nowrap;}
        .toast-danger { background: #d32f2f; }
        @keyframes slideIn { from { transform: translate(-50%, -20px); opacity: 0; } to { transform: translate(-50%, 0); opacity: 1; } }
        
        @media (min-width: 768px) {
            body { padding: 20px; }
            .app-wrapper { max-width: 95%; border-radius: 20px; min-height: calc(100vh - 40px); padding-bottom: 24px; }
            .header { border-top-left-radius: 20px; border-top-right-radius: 20px; padding: 20px 32px; }
            .content { display: flex; flex-direction: row-reverse; align-items: flex-start; gap: 24px; padding: 0 24px; }
            .desktop-sidebar { width: 320px; flex-shrink: 0; position: sticky; top: 100px; }
            .desktop-main { flex-grow: 1; min-width: 0; }
            .soal-nav-grid { grid-template-columns: repeat(6, 1fr); }
            .header h2 { max-width: 600px; font-size: 18px; }
        }
        
        body { -webkit-touch-callout: none; -webkit-user-select: none; -khtml-user-select: none; -moz-user-select: none; -ms-user-select: none; user-select: none; }
        textarea { -webkit-user-select: auto; -khtml-user-select: auto; -moz-user-select: auto; -ms-user-select: auto; user-select: auto; }
        
        .screenshot-protector { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; pointer-events: none; z-index: 99999; background: transparent; mix-blend-mode: difference; display: none; }
        body.screenshot-mode .screenshot-protector { display: block !important; background: rgba(0, 0, 0, 1) !important; mix-blend-mode: normal !important; }
        body.screenshot-mode .app-wrapper, body.screenshot-mode .header, body.screenshot-mode .card-lapor, body.screenshot-mode .timer-box { filter: brightness(0.02) blur(12px) !important; transition: all 0.01s ease !important; opacity: 0.1 !important; }
        
        .app-wrapper::after { content: ''; position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; pointer-events: none; z-index: 99998; background: transparent; display: none; }
        body.screenshot-mode .app-wrapper::after { display: block !important; background: rgba(0, 0, 0, 0.99) !important; }

        /* Tampilan tabel dari soal yang dibuat guru (Quill table-better) */
        .ck-content table { border-collapse: collapse; width: 100%; margin: 8px 0; }
        .ck-content table td, .ck-content table th { border: 1px solid #ddd; padding: 6px 8px; }
        .ck-content .ql-formula { display: inline-block; max-width: 100%; vertical-align: middle; }
        .ck-content .katex-display { max-width: 100%; overflow-x: auto; overflow-y: hidden; }
    </style>
</head>
<body class="light">
    <div class="app-wrapper">

        <!-- Overlay Pelanggaran -->
        <div id="overlayLogout" class="overlay-logout" style="display:none;">
            <div class="card-lapor" style="max-width: 320px; width: 90%; text-align: center;">
                <div class="material-icons" style="font-size: 48px; color: #d32f2f; margin-bottom: 10px;">block</div>
                <h2 class="text-lg font-bold text-gray-800 mb-2">Akses Ditangguhkan!</h2>
                <p class="text-gray-600 text-[13px] mb-4" id="logoutMessage">Terlalu banyak pelanggaran.</p>
                <button onclick="window.location.href='verifikasi_kode.php?id=<?= $id_ujian ?>'" class="btn-nav btn-nav-primary w-full justify-center"><span class="material-icons" style="font-size: 16px;">key</span> Masukkan Kode Ulang</button>
            </div>
        </div>

        <div class="header">
            <div style="flex: 1; min-width: 0;">
                <h2><?= htmlspecialchars($ujian['judul_ujian']) ?></h2>
                <p><?= htmlspecialchars($nama) ?></p>
            </div>
            <div class="flex flex-col items-end" style="flex-shrink: 0;">
                <div id="timer" class="timer-box">Memuat...</div>
                <div id="pelanggaranDisplay" class="text-[10px] mt-1 <?= $pelanggaran > 0 ? 'text-red-200' : 'text-green-200' ?>" style="<?= $is_latihan ? 'display:none;' : '' ?>">Total Pelanggaran: <span id="pelanggaranCount"><?= $pelanggaran ?>/<?= $batas_pelanggaran ?></span></div>
            </div>
        </div>

        <div class="content">
            <!-- WRAPPER SIDEBAR -->
            <div class="desktop-sidebar">
                <div class="card-lapor">
                    <div class="flex justify-between items-center mb-2">
                        <span class="font-bold text-gray-700 text-[13px]">Progres: <span id="progressText">0</span>%</span>
                    </div>
                    <div class="w-full bg-gray-100 rounded-full h-1.5">
                        <div class="bg-blue-600 h-1.5 rounded-full" id="progressBar" style="width: 0%; transition: width 0.4s ease;"></div>
                    </div>
                </div>

                <?php if (!$is_latihan): ?>
                <!-- PERINGATAN PELANGGARAN: daftar ini mengikuti detektor di blok
                     anti-cheat bagian 3b berkas ini dan peringatan yang sama di
                     verifikasi_kode.php. Samakan bila aturannya diubah. -->
                <details class="card-lapor" style="padding: 12px 16px;">
                    <summary class="cursor-pointer flex items-center gap-1 text-[13px] font-bold text-red-700">
                        <span class="material-icons text-[16px]">warning</span> Yang dihitung pelanggaran
                    </summary>
                    <ul class="text-[11px] text-gray-600 mt-3 pl-4 list-disc space-y-1">
                        <li>Berpindah tab/aplikasi atau meminimalkan browser.</li>
                        <li>Jendela ujian tidak aktif lebih dari ±1,5 detik.</li>
                        <li>Keluar dari halaman ujian (tombol back, menutup tab, pindah halaman).</li>
                        <li>Screenshot: PrintScreen atau screenshot iPhone/iPad.</li>
                    </ul>
                    <p class="text-[11px] font-bold text-red-700 mt-2">
                        Pelanggaran ke-<?= $batas_pelanggaran ?> = ujian terkunci, hanya guru pengawas yang bisa membuka.
                    </p>
                    <p class="text-[10px] text-gray-500 mt-2 pt-2 border-t border-dashed border-gray-200">
                        Klik kanan, copy/paste, dan tombol pintasan (F12, Ctrl+S/P/U) hanya diblokir — tidak dihitung. Keyboard layar & memutar layar HP juga tidak dihitung.
                    </p>
                </details>
                <?php endif; ?>
                
                <div class="card-lapor">
                    <div class="head-lapor flex justify-between items-center cursor-pointer" style="margin-bottom: 8px; padding-bottom: 8px;" onclick="toggleNavigasi()">
                        <p class="lapor-title text-[14px]">Navigasi Semua Soal</p>
                        <span id="ikon-minimize" class="material-icons text-gray-500 transition-transform">expand_less</span>
                    </div>
                    <div id="konten-navigasi">
                        <!-- GRID NAVIGASI DI-GENERATE DI SINI -->
                        <div class="soal-nav-grid mb-4" id="gridNavigasi">
                            <?php for ($i = 1; $i <= $total_soal; $i++): ?>
                                <button type="button" class="soal-nav-item" id="nav-item-<?= $i ?>" onclick="pindahSoal(<?= $i ?>)"><?= $i ?></button>
                            <?php endfor; ?>
                        </div>
                        
                        <div class="bg-gray-50 rounded-xl p-3 text-[11px] space-y-2 border border-gray-100">
                            <div class="flex justify-between items-center">
                                <span class="text-gray-500 flex items-center gap-1.5"><span class="w-2.5 h-2.5 rounded-full bg-green-100 border border-green-500"></span> Terjawab</span>
                                <span class="font-bold text-green-700 text-[12px]" id="terjawabCount">0</span>
                            </div>
                            <div class="flex justify-between items-center">
                                <span class="text-gray-500 flex items-center gap-1.5"><span class="w-2.5 h-2.5 rounded-full bg-gray-200 border border-gray-300"></span> Belum</span>
                                <span class="font-bold text-gray-700 text-[12px]" id="belumCount"><?= $total_soal ?></span>
                            </div>
                        </div>
                    </div>
                </div>
            </div> <!-- END SIDEBAR -->

            <!-- WRAPPER MAIN SOAL (FORM OFFLINE) -->
            <div class="desktop-main">
                <form id="formUjianOffline" method="POST" action="">
                    <input type="hidden" name="submit_ujian_offline" value="1">
                    <input type="hidden" name="data_jawaban_lokal" id="input_data_lokal" value="{}">
                    
                    <!-- SEMUA SOAL DIMUAT SEKALIGUS (DISembunyikan via CSS) -->
                    <?php 
                    $no = 1;
                    foreach ($soal_list as $soal_sekarang): 
                        $id_soal = $soal_sekarang['id'];
                        $tipe = $soal_sekarang['tipe_soal'];
                        $tipe_label = ['pg' => 'Pilihan Ganda', 'pg_kompleks' => 'PG Kompleks', 'benar_salah' => 'Benar/Salah', 'benar_salah_tabel' => 'Benar/Salah Tabel', 'uraian' => 'Uraian'][$tipe] ?? $tipe;
                    ?>
                    
                    <div id="wadah_soal_<?= $no ?>" class="card-lapor container-soal" style="display: none;" data-no="<?= $no ?>" data-id="<?= $id_soal ?>" data-tipe="<?= $tipe ?>">
                        <div class="head-lapor">
                            <div class="flex items-center gap-3">
                                <div class="avatar"><?= $no ?></div>
                                <div><p class="lapor-title">Soal Ujian</p><span class="guru-tujuan"><?= $tipe_label ?></span></div>
                            </div>
                            <div class="text-right"><span class="status status-diproses"><span class="material-icons text-[12px]">stars</span> <?= $soal_sekarang['skor'] ?> Poin</span></div>
                        </div>
                        
                        <div class="text-[14px] text-gray-800 leading-relaxed mb-4 ck-content"><?= $soal_sekarang['pertanyaan'] ?></div>
                        
                        <?php if (!empty($soal_sekarang['gambar'])): ?>
                            <div class="mb-4"><img src="https://sman1sukorejo.sch.id/esmansa/ujian/uploads/<?= htmlspecialchars($soal_sekarang['gambar']) ?>" class="max-h-48 w-full object-contain rounded-xl border border-gray-200" onerror="this.style.display='none'"></div>
                        <?php endif; ?>
                        
                        <?php if (!empty($soal_sekarang['video_url'])): 
                            $yt_embed = str_replace("watch?v=", "embed/", $soal_sekarang['video_url']);
                            if(strpos($soal_sekarang['video_url'], "youtu.be/") !== false){ $bagian_url = explode("youtu.be/", $soal_sekarang['video_url']); $yt_embed = "https://www.youtube.com/embed/" . end($bagian_url); }
                        ?>
                            <div class="mb-4 aspect-video w-full rounded-xl overflow-hidden border border-gray-200"><iframe src="<?= htmlspecialchars($yt_embed) ?>" class="w-full h-full" frameborder="0" allowfullscreen></iframe></div>
                        <?php endif; ?>
                        
                        <div class="mt-4 area-input-jawaban">
                            <?php if ($tipe == 'pg'): ?>
                                <?php foreach (['A', 'B', 'C', 'D', 'E'] as $huruf):
                                    $opsi_teks = $soal_sekarang['opsi_' . strtolower($huruf)] ?? '';
                                    if (empty($opsi_teks)) continue;
                                ?>
                                <label class="opsi-label">
                                    <input type="radio" name="jwb_<?= $id_soal ?>" value="<?= $huruf ?>" class="w-4 h-4 text-blue-600 focus:ring-blue-500">
                                    <div class="flex-1 text-[13px]"><span class="font-bold text-gray-700 mr-1"><?= $huruf ?>.</span><span class="text-gray-700 ck-content"><?= $opsi_teks ?></span></div>
                                </label>
                                <?php endforeach; ?>
                                
                            <?php elseif ($tipe == 'pg_kompleks'): ?>
                                <?php foreach (['A', 'B', 'C', 'D', 'E'] as $huruf):
                                    $opsi_teks = $soal_sekarang['opsi_' . strtolower($huruf)] ?? '';
                                    if (empty($opsi_teks)) continue;
                                ?>
                                <label class="opsi-label">
                                    <input type="checkbox" name="jwb_<?= $id_soal ?>[]" value="<?= $huruf ?>" class="w-4 h-4 text-blue-600 rounded focus:ring-blue-500">
                                    <div class="flex-1 text-[13px]"><span class="font-bold text-gray-700 mr-1"><?= $huruf ?>.</span><span class="text-gray-700 ck-content"><?= $opsi_teks ?></span></div>
                                </label>
                                <?php endforeach; ?>
                                
                            <?php elseif ($tipe == 'benar_salah'): ?>
                                <div class="grid grid-cols-2 gap-2">
                                    <label class="opsi-label"><input type="radio" name="jwb_<?= $id_soal ?>" value="Benar" class="w-4 h-4 text-blue-600 focus:ring-blue-500"><span class="font-bold text-green-700 flex items-center gap-1 text-[13px]"><span class="material-icons text-[16px]">check_circle</span> Benar</span></label>
                                    <label class="opsi-label"><input type="radio" name="jwb_<?= $id_soal ?>" value="Salah" class="w-4 h-4 text-blue-600 focus:ring-blue-500"><span class="font-bold text-red-700 flex items-center gap-1 text-[13px]"><span class="material-icons text-[16px]">cancel</span> Salah</span></label>
                                </div>
                                
                            <?php elseif ($tipe == 'benar_salah_tabel'): 
                                $daftar = []; foreach (['a', 'b', 'c', 'd', 'e'] as $opt) { if (!empty($soal_sekarang['opsi_'.$opt])) $daftar[$opt] = $soal_sekarang['opsi_'.$opt]; }
                            ?>
                                <div class="overflow-x-auto border border-gray-200 rounded-xl">
                                    <table class="w-full text-[12px] text-left">
                                        <thead class="bg-gray-50 text-gray-700 border-b border-gray-200"><tr><th class="p-2 w-8 text-center font-bold">No</th><th class="p-2 font-bold min-w-[120px]">Pernyataan</th><th class="p-2 text-center text-green-700 font-bold">B</th><th class="p-2 text-center text-red-700 font-bold">S</th></tr></thead>
                                        <tbody class="divide-y divide-gray-100">
                                            <?php $idx=0; foreach ($daftar as $opt => $teks): ?>
                                            <tr data-baris="<?= $idx ?>">
                                                <td class="p-2 text-center text-gray-500"><?= $idx + 1 ?></td>
                                                <td class="p-2 text-gray-800 ck-content"><?= $teks ?></td>
                                                <td class="p-1 text-center"><label class="flex items-center justify-center w-full h-full py-2 cursor-pointer hover:bg-gray-50"><input type="radio" name="jwb_<?= $id_soal ?>_<?= $opt ?>" value="B" class="w-4 h-4 text-blue-600"></label></td>
                                                <td class="p-1 text-center"><label class="flex items-center justify-center w-full h-full py-2 cursor-pointer hover:bg-gray-50"><input type="radio" name="jwb_<?= $id_soal ?>_<?= $opt ?>" value="S" class="w-4 h-4 text-blue-600"></label></td>
                                            </tr>
                                            <?php $idx++; endforeach; ?>
                                        </tbody>
                                    </table>
                                </div> 
                            <?php elseif ($tipe == 'uraian'): ?>
                                <textarea name="jwb_<?= $id_soal ?>" class="w-full border border-gray-200 rounded-xl p-3 text-[13px] min-h-[120px] focus:outline-none focus:border-blue-500 focus:ring-1 bg-gray-50/50" placeholder="Ketik jawaban Anda di sini..."></textarea>
                            <?php endif; ?>
                        </div>
                        
                        <!-- Navigasi Bawah via JavaScript -->
                        <div class="flex justify-between items-center mt-6 pt-4 border-t border-dashed border-gray-200">
                            <button type="button" class="btn-nav btn-nav-outline" onclick="pindahSoal(<?= $no - 1 ?>)" <?= $no == 1 ? 'disabled' : '' ?>><span class="material-icons text-[16px]">chevron_left</span> Sblmnya</button>
                            <?php if ($no < $total_soal): ?>
                                <button type="button" class="btn-nav btn-nav-primary" onclick="pindahSoal(<?= $no + 1 ?>)">Selanjutnya <span class="material-icons text-[16px]">chevron_right</span></button>
                            <?php else: ?>
                                <button type="button" onclick="konfirmasiSubmit()" class="btn-nav btn-nav-success !mt-0 !w-auto"><span class="material-icons text-[16px]">task_alt</span> Kumpulkan</button>
                            <?php endif; ?>
                        </div>
                    </div>
                    <?php 
                    $no++;
                    endforeach; 
                    ?>
                </form>
            </div> <!-- END MAIN SOAL -->
        </div>
    </div> <!-- END APP-WRAPPER -->

    <div class="screenshot-protector"></div>

    <script>
        // ==========================================
        // 1. ENGINE OFFLINE-FIRST (LOCAL STORAGE)
        // ==========================================
        const idUjian = <?= $id_ujian ?>;
        const nis = <?= jsonAman((string) $nis) ?>; // PERBAIKAN FATAL #1: string JS dibentuk aman
        const totalSoal = <?= $total_soal ?>;
        const modeLatihan = <?= $is_latihan ? 'true' : 'false' ?>; // latihan: tanpa kode & tanpa anti-cheat
        const batasWaktuAktif = <?= $durasi_ujian_detik > 0 ? 'true' : 'false' ?>; // durasi 0 = belum diatur
        const kunciLocalStorage = 'esmansa_ujian_' + idUjian + '_' + nis;
        const kunciPending = kunciLocalStorage + '_pending';
        
        let dbAnswers = <?= $jawaban_awal_json ?>; 
        // PERBAIKAN FATAL #4: pembacaan localStorage dibungkus try/catch.
        // Sebelumnya JSON.parse() yang gagal (salinan lokal rusak) melempar
        // error di awal script, sehingga seluruh halaman ujian mati: tidak ada
        // kartu soal yang tampil dan tombol "Kumpulkan" tidak jalan.
        let jawabanLokal = dbAnswers;
        try {
            const tersimpanLokal = JSON.parse(localStorage.getItem(kunciLocalStorage));
            if (tersimpanLokal && typeof tersimpanLokal === 'object') jawabanLokal = tersimpanLokal;
        } catch (err) {
            try { localStorage.removeItem(kunciLocalStorage); } catch (e) {}
        }

        // Kalau pengiriman terakhir tidak sempat tersimpan di server, isi kunci
        // "pending" dipakai lagi supaya pekerjaan siswa tidak hilang.
        try {
            const jawabanPending = JSON.parse(localStorage.getItem(kunciPending));
            if (jawabanPending && typeof jawabanPending === 'object') {
                jawabanLokal = Object.assign({}, dbAnswers, jawabanPending);
                localStorage.setItem(kunciLocalStorage, JSON.stringify(jawabanLokal));
                localStorage.removeItem(kunciPending);
            }
        } catch (err) {}
        let soalAktif = 1;

        // Fungsi mengembalikan jawaban tersimpan ke UI form
        function pulihkanJawabanKeUI() {
            document.querySelectorAll('.container-soal').forEach(container => {
                const idSoal = container.getAttribute('data-id');
                const tipe = container.getAttribute('data-tipe');
                const jwbTersimpan = jawabanLokal[idSoal]?.jawaban || '';
                
                if(!jwbTersimpan) return;

                if (tipe === 'pg' || tipe === 'benar_salah') {
                    const rb = container.querySelector(`input[value="${jwbTersimpan}"]`);
                    if(rb) { rb.checked = true; updateStyling(rb, container); }
                } else if (tipe === 'pg_kompleks') {
                    const arr = jwbTersimpan.split(',');
                    arr.forEach(val => {
                        const cb = container.querySelector(`input[value="${val}"]`);
                        if(cb) { cb.checked = true; updateStyling(cb, container); }
                    });
                } else if (tipe === 'benar_salah_tabel') {
                    const arr = jwbTersimpan.split(',');
                    const rows = container.querySelectorAll('tbody tr');
                    rows.forEach((row, idx) => {
                        if(arr[idx]) {
                            const rb = row.querySelector(`input[value="${arr[idx]}"]`);
                            if(rb) rb.checked = true;
                        }
                    });
                } else if (tipe === 'uraian') {
                    container.querySelector('textarea').value = jwbTersimpan;
                }
            });
            perbaruiStatistikSidebar();
        }

        // Simpan setiap kali siswa ngeklik/ngetik
        function ekstrakJawaban(container, tipe) {
            if (tipe === 'pg' || tipe === 'benar_salah') {
                const cb = container.querySelector('input[type="radio"]:checked');
                return cb ? cb.value : '';
            } else if (tipe === 'pg_kompleks') {
                const cbs = container.querySelectorAll('input[type="checkbox"]:checked');
                return Array.from(cbs).map(cb => cb.value).join(',');
            } else if (tipe === 'benar_salah_tabel') {
                const rows = container.querySelectorAll('tbody tr');
                let res = [];
                rows.forEach(r => {
                    const c = r.querySelector('input[type="radio"]:checked');
                    res.push(c ? c.value : '');
                });
                return res.join(',');
            } else if (tipe === 'uraian') {
                return container.querySelector('textarea').value.trim();
            }
            return '';
        }

        document.querySelectorAll('.area-input-jawaban input, .area-input-jawaban textarea').forEach(el => {
            el.addEventListener('change', function() {
                const container = this.closest('.container-soal');
                const idSoal = container.getAttribute('data-id');
                const tipe = container.getAttribute('data-tipe');
                
                const val = ekstrakJawaban(container, tipe);
                jawabanLokal[idSoal] = { jawaban: val };
                localStorage.setItem(kunciLocalStorage, JSON.stringify(jawabanLokal));
                
                updateStyling(this, container);
                perbaruiStatistikSidebar();
            });
            if (el.tagName === 'TEXTAREA') {
                el.addEventListener('input', function() {
                    const container = this.closest('.container-soal');
                    jawabanLokal[container.getAttribute('data-id')] = { jawaban: this.value.trim() };
                    localStorage.setItem(kunciLocalStorage, JSON.stringify(jawabanLokal));
                    perbaruiStatistikSidebar();
                });
            }
        });

        function updateStyling(element, container) {
            if(element.type === 'radio' && !element.closest('table')) {
                container.querySelectorAll('.opsi-label').forEach(lbl => {
                    lbl.classList.remove('selected', 'bg-blue-50', 'border-blue-500');
                });
                element.closest('.opsi-label').classList.add('selected', 'bg-blue-50', 'border-blue-500');
            } else if(element.type === 'checkbox') {
                const lbl = element.closest('.opsi-label');
                if(element.checked) lbl.classList.add('selected', 'bg-blue-50', 'border-blue-500');
                else lbl.classList.remove('selected', 'bg-blue-50', 'border-blue-500');
            }
        }

        // ==========================================
        // 2. NAVIGASI SPA & UI UPDATE
        // ==========================================
        function pindahSoal(noTujuan) {
            if(noTujuan < 1 || noTujuan > totalSoal) return;
            document.querySelectorAll('.container-soal').forEach(el => el.style.display = 'none');
            document.getElementById('wadah_soal_' + noTujuan).style.display = 'block';
            soalAktif = noTujuan;
            perbaruiStatistikSidebar();
            window.scrollTo(0,0);
        }

        function perbaruiStatistikSidebar() {
            let terjawab = 0;
            document.querySelectorAll('.container-soal').forEach(container => {
                const no = container.getAttribute('data-no');
                const btnNav = document.getElementById('nav-item-' + no);
                const idSoal = container.getAttribute('data-id');
                
                const jwb = jawabanLokal[idSoal]?.jawaban || '';
                const isAnswered = jwb.replace(/,/g, '').length > 0; 
                
                btnNav.className = 'soal-nav-item';
                if (no == soalAktif) btnNav.classList.add('active');
                else if (isAnswered) { btnNav.classList.add('answered'); terjawab++; }
                
                if (no == soalAktif && isAnswered) terjawab++;
            });
            
            document.getElementById('terjawabCount').textContent = terjawab;
            document.getElementById('belumCount').textContent = totalSoal - terjawab;
            let prog = Math.round((terjawab / totalSoal) * 100);
            document.getElementById('progressBar').style.width = prog + '%';
            document.getElementById('progressText').textContent = prog;
        }

        function toggleNavigasi() {
            const konten = document.getElementById('konten-navigasi');
            const ikon = document.getElementById('ikon-minimize');
            konten.style.display = konten.style.display === 'none' ? 'block' : 'none';
            ikon.textContent = konten.style.display === 'none' ? 'expand_more' : 'expand_less';
        }

// ==========================================
// 3. ANTI-CHEAT SYSTEM (REBUILT)
// ==========================================
let pelanggaran = <?= $pelanggaran ?>;
const maxPelanggaran = <?= $batas_pelanggaran ?>;

// --- STATE GLOBAL ---
let isSubmitting = false;           // true dari sebelum confirm() sampai submit/batal
let lembarTerkirim = false;         // true setelah lembar benar-benar dikirim
let suppressUntil = 0;              // timestamp: abaikan pelanggaran sampai waktu ini
let lastPelanggaranTime = 0;        // debounce antar pelanggaran
let pageLoaded = false;             // hindari false positive saat page load
setTimeout(() => { pageLoaded = true; }, 1500);

const GRACE_AFTER_DIALOG   = 1500;  // ms setelah dialog submit ditutup
const DEBOUNCE_PELANGGARAN = 2000;  // jarak minimum antar pelanggaran

var timerInterval = null;           // deklarasi awal, di-set di section 5

// --- Helper: apakah pelanggaran harus diabaikan? ---
function pelanggaranDiabaikan() {
    if (modeLatihan) return true;   // latihan bebas dari anti-cheat
    if (!pageLoaded) return true;
    if (isSubmitting) return true;
    if (Date.now() < suppressUntil) return true;
    return false;
}

// --- Helper: user sedang fokus ke input? ---
function sedangInteraksiInput() {
    const a = document.activeElement;
    if (!a) return false;
    if (a.tagName === 'TEXTAREA' || a.tagName === 'INPUT') return true;
    if (a instanceof HTMLIFrameElement) return true;
    return false;
}

// --- Helper: update UI ---
function updatePelanggaranUI() {
    const el = document.getElementById('pelanggaranCount');
    if (el) el.textContent = pelanggaran + '/' + maxPelanggaran;
    const display = document.getElementById('pelanggaranDisplay');
    if (display && pelanggaran > 0) {
        display.classList.remove('text-green-200');
        display.classList.add('text-red-200');
    }
}

// --- Helper: notifikasi ---
function showNotification(message, type = '') {
    document.querySelectorAll('.toast-notification').forEach(el => el.remove());
    const toast = document.createElement('div');
    toast.className = 'toast-notification ' + type;
    toast.innerHTML = message;
    document.body.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translate(-50%, -20px)';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// --- Transport aman (beacon + keepalive) ---
const BATAS_AMAN_BEACON = 60000;
function kirimAman(fd) {
    let ukuran = 0;
    for (const pair of fd.entries()) {
        ukuran += String(pair[0]).length + String(pair[1]).length;
    }
    if (navigator.sendBeacon && ukuran <= BATAS_AMAN_BEACON) {
        if (navigator.sendBeacon(window.location.href, fd)) return;
    }
    fetch(window.location.href, { method: 'POST', body: fd, keepalive: true }).catch(()=>{});
}

// --- Autosave & ping ---
function syncJawabanSekali() {
    const fd = new FormData();
    fd.append('autosave_jawaban', '1');
    fd.append('data_jawaban', JSON.stringify(jawabanLokal));
    kirimAman(fd);
}
function kirimPing() {
    if (document.hidden) return;
    fetch(window.location.href, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'ping=1&data_jawaban=' + encodeURIComponent(JSON.stringify(jawabanLokal))
    }).catch(()=>{});
}
setInterval(kirimPing, 180000);

function cekStatusTerbaru() {
    fetch(window.location.href, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'cek_status=1'
    })
    .then(res => res.json())
    .then(data => {
        if (data.status === 'logout') {
            document.getElementById('overlayLogout').style.display = 'flex';
            document.getElementById('logoutMessage').textContent = data.message;
            if (timerInterval) clearInterval(timerInterval);
        } else if (data.pelanggaran > pelanggaran) {
            pelanggaran = data.pelanggaran;
            updatePelanggaranUI();
        }
    }).catch(()=>{});
}

// ==========================================
// ===  SATU-SATUNYA PINTU MASUK PELANGGARAN  ===
// ==========================================
function catatPelanggaran(alasan) {
    if (pelanggaranDiabaikan()) return;

    const now = Date.now();
    if (now - lastPelanggaranTime < DEBOUNCE_PELANGGARAN) return;
    lastPelanggaranTime = now;

    const fd = new FormData();
    fd.append('catat_pelanggaran', '1');
    fd.append('data_jawaban_sync', JSON.stringify(jawabanLokal));
    kirimAman(fd);

    pelanggaran++;
    updatePelanggaranUI();
    if (alasan) showNotification('⚠️ ' + alasan, 'toast-danger');
}

// --- Proteksi visual screenshot ---
function aktifkanProteksiScreenshot(alasan) {
    if (pelanggaranDiabaikan()) return;
    if (document.body.classList.contains('screenshot-mode')) return;
    document.body.classList.add('screenshot-mode');
    catatPelanggaran(alasan || 'Screenshot terdeteksi');
    setTimeout(() => document.body.classList.remove('screenshot-mode'), 1200);
}
window.activateScreenshotProtection = aktifkanProteksiScreenshot;
window.deactivateScreenshotProtection = function() {
    document.body.classList.remove('screenshot-mode');
};

// ==========================================
// 3b. DETEKTOR-DETEKTOR
// ==========================================

// Detektor 1: Tab berpindah
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        catatPelanggaran('Berpindah tab/aplikasi');
        syncJawabanSekali();
    } else {
        kirimPing();
        cekStatusTerbaru();
    }
});

// Detektor 2: Window blur
window.addEventListener('blur', function() {
    if (sedangInteraksiInput()) return;
    catatPelanggaran('Keluar dari halaman ujian');
});

// Detektor 3: Interval cek fokus
let statusFokusSebelumnya = true;
setInterval(function() {
    if (pelanggaranDiabaikan()) {
        statusFokusSebelumnya = document.hasFocus() && !document.hidden;
        return;
    }
    if (sedangInteraksiInput()) {
        statusFokusSebelumnya = true;
        return;
    }
    const sedangFokus = document.hasFocus() && !document.hidden;
    if (!sedangFokus && statusFokusSebelumnya) {
        catatPelanggaran('Jendela ujian tidak aktif');
    }
    if (sedangFokus && !statusFokusSebelumnya) {
        kirimPing();
        cekStatusTerbaru();
    }
    statusFokusSebelumnya = sedangFokus;
}, 1500);

// Detektor 4: Pagehide
window.addEventListener('pagehide', syncJawabanSekali);

// Detektor 5: PrintScreen
document.addEventListener('keydown', e => {
    if (e.key === 'F12' || (e.ctrlKey && ['u','s','c','v','x','p'].includes(e.key.toLowerCase()))) {
        e.preventDefault();
    }
    if (e.key === 'PrintScreen') {
        try { navigator.clipboard.writeText(''); } catch (err) {}
        aktifkanProteksiScreenshot('Screenshot dilarang');
    }
});

// CATATAN: detektor "Resize (split screen)" dan "Fullscreen" dihapus karena
// keduanya menghukum tindakan yang bukan kecurangan — keyboard virtual Android
// dan memutar layar HP ikut memicu resize, sehingga siswa jujur bisa terkunci
// dan butuh reset guru. Sinyal yang tersisa (pindah tab, keluar halaman, dan
// PrintScreen) jauh lebih bisa dipercaya.

// Detektor 8: iPhone touchend (screenshot)
let touchStartTime = 0;
document.addEventListener('touchstart', function() { touchStartTime = Date.now(); });
document.addEventListener('touchend', function() {
    if ((Date.now() - touchStartTime) >= 500) return;
    setTimeout(function() {
        if (pelanggaranDiabaikan()) return;
        if (sedangInteraksiInput()) return;
        if (!document.hasFocus() || document.hidden) {
            aktifkanProteksiScreenshot('Screenshot terdeteksi (iPhone)');
        }
    }, 300);
});

// Detektor "Orientasi berubah" juga dihapus: memutar HP bukan pelanggaran.

// ==========================================
// 4. SUBMIT AKHIR
// ==========================================
function konfirmasiSubmit() {
    const terjawab = parseInt(document.getElementById('terjawabCount').textContent);
    isSubmitting = true;

    let lanjut;
    if (terjawab < totalSoal) {
        lanjut = confirm(`⚠️ Masih ada ${totalSoal - terjawab} soal kosong. Yakin kumpulkan?`);
    } else {
        lanjut = confirm('✅ Yakin ingin mengumpulkan ujian?');
    }

    if (!lanjut) {
        // Grace period: beri waktu iOS/Android mengembalikan fokus webview
        // sebelum isNavigating di-reset, supaya event tertunda tidak dianggap pelanggaran.
        suppressUntil = Date.now() + GRACE_AFTER_DIALOG;
        isSubmitting = false;
        return;
    }

    kirimLembar();
}

// Kirim lembar ke server. Dipakai tombol Kumpulkan maupun pengumpulan otomatis
// saat waktu habis; hanya boleh jalan sekali.
function kirimLembar() {
    if (lembarTerkirim) return;
    lembarTerkirim = true;
    isSubmitting = true;
    suppressUntil = Date.now() + GRACE_AFTER_DIALOG;
    document.getElementById('input_data_lokal').value = JSON.stringify(jawabanLokal);

    // Jawaban disimpan ke kunci "pending", bukan langsung dibuang. Kalau
    // pengiriman gagal (jaringan putus, server error), halaman ujian memulihkan
    // jawaban ini saat dimuat ulang. Kunci pending baru dihapus di halaman hasil
    // setelah server benar-benar menyimpan lembar.
    try {
        localStorage.setItem(kunciPending, JSON.stringify(jawabanLokal));
        localStorage.removeItem(kunciLocalStorage);
    } catch (err) {}

    document.getElementById('formUjianOffline').submit();
}

// ==========================================
// 5. TIMER, BLOKIR KLIK KANAN
// ==========================================
let sisaDetik = <?= $sisa_detik ?>;
const timerElement = document.getElementById('timer');
timerInterval = setInterval(() => {
    if (sisaDetik <= 0) {
        // Durasi yang belum diisi (0 menit) diperlakukan sebagai tanpa batas
        // waktu, jadi jangan paksa kumpul — salah setelan bisa mengosongkan lembar.
        if (!batasWaktuAktif) {
            if (timerElement.textContent !== '0:00') timerElement.textContent = '0:00';
            return;
        }
        clearInterval(timerInterval);
        // Tanpa dialog konfirmasi: confirm() bisa diblokir browser (misalnya saat
        // tab tidak aktif) sehingga lembar tidak pernah terkirim sama sekali.
        kirimLembar();
        return;
    }
    timerElement.textContent = `${Math.floor(sisaDetik / 60)}:${(sisaDetik % 60).toString().padStart(2, '0')}`;
    if (sisaDetik < 300) timerElement.classList.add('timer-warning');
    sisaDetik--;
}, 1000);

document.addEventListener('contextmenu', e => e.preventDefault());
['copy', 'cut', 'paste', 'dragstart', 'drop'].forEach(evt => document.addEventListener(evt, e => e.preventDefault()));

// ==========================================
// INISIALISASI
// ==========================================
// PERBAIKAN FATAL #4 (lanjutan): pemulihan jawaban dan tampilan soal pertama
// tidak boleh mematikan halaman kalau ada data lokal yang aneh.
try { pulihkanJawabanKeUI(); } catch (err) {}
try { pindahSoal(1); } catch (err) {}
    </script>
</body>
</html>
