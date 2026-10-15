<?php
// verifikasi_kode.php
session_start();
if (!isset($_SESSION['status']) || $_SESSION['status'] != "login") {
    header("location: login.php");
    exit;
}

include '../koneksi.php'; // koneksi smania

// Koneksi ke esmansa
$conn_esmansa = mysqli_connect("localhost", "root", "", "smansuko_esmansa");
// SESUAIKAN dengan kredensial Anda!

$nis = $_SESSION['nis'];
$nama = $_SESSION['nama'];
$kelas = $_SESSION['kelas'];
$id_ujian = isset($_GET['id']) ? (int)$_GET['id'] : 0;

if ($id_ujian == 0) {
    header("Location: index.php");
    exit;
}

// Ambil data ujian
$query_ujian = mysqli_query($conn_esmansa, "
    SELECT * FROM ujian WHERE id = $id_ujian 
    AND FIND_IN_SET('$kelas', REPLACE(kelas, ', ', ',')) > 0
");
if (mysqli_num_rows($query_ujian) == 0) {
    die("Ujian tidak ditemukan atau tidak tersedia untuk kelas Anda.");
}
$ujian = mysqli_fetch_assoc($query_ujian);

// Cek apakah siswa sudah selesai mengerjakan
$query_cek = mysqli_query($conn_esmansa, "
    SELECT * FROM jawaban_siswa 
    WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'selesai'
");
if (mysqli_num_rows($query_cek) > 0) {
    header("Location: lihat_hasil_siswa.php?id=$id_ujian");
    exit;
}

// ============================================================
// LATIHAN: tidak perlu kode akses guru, langsung boleh dikerjakan
// ============================================================
if (($ujian['tipe'] ?? 'ujian') == 'latihan') {
    $_SESSION['kode_terverifikasi_' . $id_ujian] = true;
    header("Location: kerjakan_ujian.php?id=$id_ujian");
    exit;
}

// Cek apakah siswa sedang mengerjakan (lanjutkan tanpa kode)
$query_sedang = mysqli_query($conn_esmansa, "
    SELECT * FROM jawaban_siswa 
    WHERE id_ujian = $id_ujian AND nis = '$nis' AND status = 'sedang'
");
if (mysqli_num_rows($query_sedang) > 0) {
    // Jika sudah pernah verifikasi, langsung ke ujian
    if (isset($_SESSION['kode_terverifikasi_' . $id_ujian]) && $_SESSION['kode_terverifikasi_' . $id_ujian] === true) {
        header("Location: kerjakan_ujian.php?id=$id_ujian");
        exit;
    }
}

// ============================================================
// AMBIL KODE AKSES AKTIF
// ============================================================
$query_kode = mysqli_query($conn_esmansa, "
    SELECT * FROM kode_akses_global 
    WHERE expired_pada > NOW() 
    ORDER BY id DESC LIMIT 1
");
$kode_aktif = mysqli_fetch_assoc($query_kode);
$kode_db = $kode_aktif['kode'] ?? '';
$expired_pada = $kode_aktif['expired_pada'] ?? date('Y-m-d H:i:s');
$sisa_menit = max(0, ceil((strtotime($expired_pada) - time()) / 60));

$pesan_error = '';

// ============================================================
// PERINGATAN PELANGGARAN (tampil sebelum siswa mulai mengerjakan).
// Angka 3 HARUS sama dengan $batas_pelanggaran di kerjakan_ujian.php, dan
// daftar pemicunya mengikuti detektor di blok anti-cheat berkas tersebut:
// pindah tab, jendela tidak aktif > 1,5 detik, keluar halaman, screenshot.
// ============================================================
$batas_pelanggaran_info = 3;

// ============================================================
// PROSES VERIFIKASI
// ============================================================
if (isset($_POST['verifikasi'])) {
    $kode_input = mysqli_real_escape_string($conn_esmansa, trim($_POST['kode_akses']));
    
    // Cek apakah kode valid dan belum expired
    $query_cek_kode = mysqli_query($conn_esmansa, "
        SELECT * FROM kode_akses_global 
        WHERE kode = '$kode_input' 
        AND expired_pada > NOW()
        ORDER BY id DESC LIMIT 1
    ");
    
    if (mysqli_num_rows($query_cek_kode) > 0) {
        // Kode benar, set session
        $_SESSION['kode_terverifikasi_' . $id_ujian] = true;
        $_SESSION['kode_terverifikasi_waktu'] = time();
        // Cap waktu per ujian: dipakai kerjakan_ujian.php untuk memastikan kode
        // masih segar saat MEMULAI lembar baru (kode guru berganti tiap 5 menit).
        $_SESSION['kode_terverifikasi_waktu_' . $id_ujian] = time();
        
        // Redirect ke ujian
        header("Location: kerjakan_ujian.php?id=$id_ujian");
        exit;
    } else {
        $pesan_error = "❌ Kode akses salah atau sudah kadaluarsa!";
    }
}
?>
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Verifikasi Kode - <?= htmlspecialchars($ujian['judul_ujian']) ?></title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@200..900&display=swap" rel="stylesheet">
    <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet">
    <style>
        body { font-family: 'Source Sans 3', sans-serif; background: #fbf9f8; min-height: 100dvh; display: flex; align-items: center; justify-content: center; }
        .hero-gradient { background: linear-gradient(135deg, #850027 0%, #ac153a 100%); }
        .card-custom { border: 1px solid #e1bec0; border-radius: 16px; background: white; box-shadow: 0 8px 32px rgba(0,0,0,0.08); max-width: 440px; width: 100%; }
        .form-control-custom { border: 1px solid #e1bec0; border-radius: 8px; padding: 12px 16px; font-size: 18px; width: 100%; background: white; text-align: center; letter-spacing: 6px; font-weight: bold; text-transform: uppercase; }
        .form-control-custom:focus { border-color: #850027; box-shadow: 0 0 0 3px rgba(133, 0, 39, 0.1); outline: none; }
        .btn-primary-custom { background: linear-gradient(135deg, #850027 0%, #ac153a 100%); color: white; transition: all 0.3s ease; padding: 12px 24px; border-radius: 8px; font-weight: 600; width: 100%; }
        .btn-primary-custom:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(133, 0, 39, 0.3); }
        .btn-secondary-custom { background: #e5e7eb; color: #374151; transition: all 0.3s ease; padding: 12px 24px; border-radius: 8px; font-weight: 600; width: 100%; }
        .btn-secondary-custom:hover { background: #d1d5db; }
        .kode-display { font-size: 28px; letter-spacing: 8px; font-weight: bold; color: #850027; font-family: 'Courier New', monospace; }
        .timer-warning { color: #dc2626; animation: pulse 1s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
        .nav-bar { position: fixed; top: 0; left: 0; right: 0; z-index: 50; background: white; border-bottom: 1px solid #e1bec0; padding: 12px 20px; }
        .nav-bar .brand { font-weight: bold; font-size: 18px; color: #850027; }
    </style>
</head>
<body>
    <!-- Navbar -->
    <div class="nav-bar">
        <div class="max-w-screen-xl mx-auto flex justify-between items-center">
            <div class="flex items-center gap-3">
                <span class="text-sm text-gray-400 hidden sm:inline">Verifikasi Ujian</span>
            </div>
            <div class="flex items-center gap-3">
                <span class="text-sm font-medium"><?= htmlspecialchars($nama) ?></span>
                <span class="text-sm text-gray-400">|</span>
                <span class="text-sm font-medium"><?= htmlspecialchars($kelas) ?></span>
            </div>
        </div>
    </div>

    <div class="card-custom p-8 mx-4 mt-20">
        <div class="text-center mb-6">
            <div class="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center text-primary text-3xl mx-auto mb-3">🔐</div>
            <h1 class="text-xl font-bold text-primary">Verifikasi Kode Ujian</h1>
            <p class="text-sm text-gray-500 mt-1"><?= htmlspecialchars($ujian['judul_ujian']) ?></p>
            <p class="text-xs text-gray-400">Masukkan kode akses dari guru untuk memulai ujian</p>
        </div>

        <?php if (!empty($pesan_error)): ?>
            <div class="mb-4 p-3 bg-red-100 text-red-800 rounded-lg text-sm flex items-center gap-2">
                <span class="material-symbols-outlined text-[18px]">error</span>
                <?= $pesan_error ?>
            </div>
        <?php endif; ?>



        <!-- ============================================================
             PERINGATAN PELANGGARAN — samakan daftarnya dengan detektor pada
             blok anti-cheat kerjakan_ujian.php dan angka $batas_pelanggaran
             di berkas itu (di sini: $batas_pelanggaran_info).
             ============================================================ -->
        <div class="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-left">
            <div class="flex items-center gap-2 mb-2">
                <span class="material-symbols-outlined text-[20px] text-red-700">warning</span>
                <p class="text-[13px] font-bold text-red-800">Baca dulu: yang menyebabkan PELANGGARAN</p>
            </div>
            <ul class="text-[12px] text-red-900 space-y-1 pl-5 list-disc">
                <li><b>Berpindah tab atau aplikasi</b> (termasuk meminimalkan browser) saat ujian berjalan.</li>
                <li><b>Jendela ujian tidak aktif</b> lebih dari ±1,5 detik — misalnya membuka aplikasi atau notifikasi lain di atas ujian.</li>
                <li><b>Keluar dari halaman ujian</b> — menekan tombol back, menutup tab, atau pindah ke halaman lain.</li>
                <li><b>Screenshot</b> — menekan tombol PrintScreen atau mengambil tangkapan layar di iPhone/iPad. Layar dikaburkan sesaat sebagai proteksi.</li>
            </ul>
            <p class="text-[12px] font-bold text-red-800 mt-3">
                Pelanggaran ke-<?= (int) $batas_pelanggaran_info ?>: ujian OTOMATIS TERKUNCI dan hanya guru pengawas yang bisa membukanya (reset akses).
            </p>
            <p class="text-[11px] text-gray-600 mt-2 pt-2 border-t border-red-100">
                Tidak dihitung pelanggaran: keyboard layar, memutar layar HP, klik kanan, serta tombol pintasan (copy/paste, F12, Ctrl+S/P/U) — semuanya hanya diblokir. Pelanggaran berturut-turut dalam 2 detik dihitung satu kali.
            </p>
        </div>

        <form action="" method="POST" class="space-y-4">
            <div>
                <label class="block text-sm font-bold text-gray-600 mb-1 text-center">Masukkan Kode</label>
                <input type="text" name="kode_akses" class="form-control-custom" placeholder="XXXXXX" maxlength="10" autofocus required>
                <p class="text-xs text-gray-400 text-center mt-2">Contoh: A1B2C3</p>
            </div>
            
            <button type="submit" name="verifikasi" class="btn-primary-custom flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">check_circle</span>
                Verifikasi & Mulai Ujian
            </button>
            
            <button type="button" onclick="window.location.href='index.php'" class="btn-secondary-custom flex items-center justify-center gap-2">
                <span class="material-symbols-outlined text-[18px]">arrow_back</span>
                Kembali ke Dashboard
            </button>
        </form>
        
        <div class="mt-6 pt-4 border-t border-gray-200 text-center">
            <p class="text-xs text-gray-400">
                <span class="material-symbols-outlined text-[14px] align-middle">info</span>
            Jika tidak punya kode, hubungi guru pengajar/pengawas.
            </p>
        </div>
    </div>

    <script>
        // ============================================================
        // AUTO UPDATE KODE (AJAX)
        // ============================================================
        function updateKode() {
            fetch('ajax_get_kode.php')
                .then(response => response.json())
                .then(data => {
                    if (data.kode) {
                        document.getElementById('kodeDisplay').textContent = data.kode;
                        document.getElementById('sisaMenit').textContent = data.sisa_menit;
                        
                        const timerKode = document.getElementById('timerKode');
                        if (data.sisa_menit <= 1) {
                            timerKode.innerHTML = '⏱️ Berlaku: <span class="text-red-500 font-bold">' + data.sisa_menit + '</span> menit lagi ⚠️';
                            timerKode.className = 'text-xs text-red-500 mt-1 timer-warning';
                        } else {
                            timerKode.innerHTML = '⏱️ Berlaku: ' + data.sisa_menit + ' menit lagi';
                            timerKode.className = 'text-xs text-gray-400 mt-1';
                        }
                    }
                })
                .catch(err => console.log('Error:', err));
        }
        
        // Update setiap 30 detik
        setInterval(updateKode, 30000);
        
        // Auto focus ke input
        document.querySelector('input[name="kode_akses"]').focus();
        
        // ============================================================
        // ENTER UNTUK SUBMIT
        // ============================================================
        document.querySelector('input[name="kode_akses"]').addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                document.querySelector('form').submit();
            }
        });
    </script>
</body>
</html>