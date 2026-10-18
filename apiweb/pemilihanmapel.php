<?php
date_default_timezone_set('Asia/Jakarta');
session_start();
require_once 'koneksi.php';

if (isset($_GET['nis']) && !empty($_GET['nis'])) {
    $_SESSION['status'] = "login";
    $_SESSION['nis'] = $_GET['nis'];
    $_SESSION['tingkat'] = 'X';
}

if (!isset($_SESSION['status']) || $_SESSION['status'] != "login") {
    header("location: index.php?pesan=belum_login");
    exit();
}

if (!isset($_SESSION['tingkat']) || $_SESSION['tingkat'] != 'X') {
    echo "<script>alert('Akses ditolak! Menu ini hanya untuk siswa Kelas X.'); window.location='dashboard.php';</script>";
    exit();
}

$nis = $_SESSION['nis'];

// --- 1. AMBIL SETTING JADWAL DARI DATABASE ---
$q_setting = mysqli_query($koneksi, "SELECT * FROM setting_pendaftaran WHERE id=1");
$st = mysqli_fetch_assoc($q_setting);

$waktu_sekarang = date('Y-m-d H:i:s');
$waktu_buka     = $st['waktu_buka'];
$waktu_tutup    = $st['waktu_tutup'];

$q_nilai = mysqli_query($koneksi, "SELECT * FROM nilai_rapor_smt1 WHERE nis='$nis'");
$nilai = mysqli_fetch_assoc($q_nilai);

// --- 2. CEK APAKAH SAAT INI DI LUAR JADWAL ---
if ($waktu_sekarang < $waktu_buka || $waktu_sekarang > $waktu_tutup) {
    // 1. CEK HASIL FINAL (Hanya jika waktu sudah tutup)
    $hasil_final = null;
    if ($waktu_sekarang > $waktu_tutup) {
        $nis = $_SESSION['nis'];
        $q_final = mysqli_query($koneksi, "
            SELECT s.*, p.nama_paket 
            FROM seleksi_paket s 
            LEFT JOIN paket_mapel p ON s.id_paket_final = p.id_paket 
            WHERE s.nis = '$nis'
        ");
        $hasil_final = mysqli_fetch_assoc($q_final);
    }
    ?>
    <!DOCTYPE html>
    <html lang="id">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Pemilihan Mapel - Smania</title>
        <link rel="stylesheet" href="css/light.css">
        <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
        <style>
            body { background: #f4f7f9; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; font-family: 'Roboto', sans-serif; }
            .box { background: white; padding: 30px; border-radius: 24px; text-align: center; box-shadow: 0 10px 30px rgba(0,0,0,0.1); width: 90%; max-width: 400px; border: 1px solid #eee; }
            .icon { font-size: 60px; margin-bottom: 15px; }
            h2 { margin: 0 0 10px; color: #333; font-size: 20px; }
            p { color: #666; line-height: 1.5; font-size: 14px; }
            
            /* Style Kartu Hasil */
            .card { background: var(--md-sys-color-surface-container-high); border-radius: 24px; padding: 20px; margin-bottom: 20px; border: 1px solid var(--md-sys-color-outline-variant); }
        
            .hasil-card { background: #e8f5e9; border: 2px solid #2e7d32; padding: 20px; border-radius: 16px; margin-top: 20px; }
            .paket-nama { color: #2e7d32; font-size: 22px; font-weight: 900; margin: 5px 0; }
            .badge-final { background: #2e7d32; color: white; padding: 4px 12px; border-radius: 20px; font-size: 11px; font-weight: bold; }
            
            .waktu { background: #f5f5f5; padding: 12px; border-radius: 12px; font-weight: bold; color: #1a73e8; margin-top: 20px; font-size: 13px; }
            .btn-back { display: inline-block; margin-top: 25px; text-decoration: none; color: #1a73e8; font-weight: bold; font-size: 14px; }
            .grid-nilai { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 15px; }
        .item-nilai { background: #f5f5f5; padding: 10px; border-radius: 12px; text-align: center; }
        .item-nilai span { font-size: 11px; color: #666; display: block; margin-bottom: 4px; }
        .item-nilai b { font-size: 18px; color: var(--md-sys-color-primary); }
        .rata-rata-box { grid-column: span 3; background: #e3f2fd; color: #1565c0; margin-top: 5px; }
        
            
        </style>
    </head>
    <body>
        <div class="box">
            <?php if ($waktu_sekarang < $waktu_buka): ?>
                <div class="icon">🔒</div>
                <h2>Akses Belum Dibuka</h2>
                <p>Halaman pemilihan paket mata pelajaran belum bisa diakses.</p>
                <div class="waktu">
                    Dibuka pada:<br>
                    <?php echo date('d M Y, H:i', strtotime($waktu_buka)); ?> WIB
                </div>
                
                <div class="card"style="margin-bottom: 20px">
            <h3 style="margin: 0 0 5px 0; font-size: 16px; color: var(--md-sys-color-primary);">Silahkan lakukan pengecekan Data Nilai Rapor Anda</h3>
            <p style="margin: 0; font-size: 12px; color: #666;">Berdasarkan nilai Semester 1</p>
            
            <?php if($nilai): ?>
            <div class="grid-nilai">
                <div class="item-nilai"><span>Agama</span><b><?php echo $nilai['pendidikan_agama']; ?></b></div>
                <div class="item-nilai"><span>Pancasila</span><b><?php echo $nilai['pendidikan_pancasila']; ?></b></div>
                <div class="item-nilai"><span>B. Indo</span><b><?php echo $nilai['bahasa_indonesia']; ?></b></div>
                <div class="item-nilai"><span>B. Ing</span><b><?php echo $nilai['bahasa_inggris']; ?></b></div>
                <div class="item-nilai"><span>Matematika</span><b><?php echo $nilai['matematika']; ?></b></div>
                <div class="item-nilai"><span>IPA</span><b><?php echo $nilai['ipa']; ?></b></div>
                <div class="item-nilai"><span>IPS</span><b><?php echo $nilai['ips']; ?></b></div>
                <div class="item-nilai"><span>PJOK</span><b><?php echo $nilai['pjok']; ?></b></div>
                <div class="item-nilai"><span>Informatika</span><b><?php echo $nilai['informatika']; ?></b></div>
                <div class="item-nilai"><span>Senbud Prakarya</span><b><?php echo $nilai['seni_budaya_prakarya']; ?></b></div>
                <div class="item-nilai"><span>B. Jawa</span><b><?php echo $nilai['bahasa_jawa']; ?></b></div>
                
                <div class="item-nilai rata-rata-box">
                    <span style="color: #1565c0;">Rata-Rata Keseluruhan</span>
                    <b style="font-size: 24px;"><?php echo number_format($nilai['rata_rata'], 1); ?></b>
                </div>
            </div>
            <?php else: ?>
                <p style="color: red; font-size: 12px; margin-top: 10px;">Data nilai belum tersedia.</p>
            <?php endif; ?>
        </div>
            <?php else: ?>
                <div class="icon">🎉</div>
                <h2>Pengumuman Final</h2>
                <p>Pendaftaran telah ditutup. Berikut adalah hasil penempatan resmi Anda:</p>
                
                <?php if ($hasil_final && $hasil_final['id_paket_final']): ?>
                    <div class="hasil-card">
                        <span class="badge-final">HASIL RESMI</span>
                        <div class="paket-nama"><?php echo $hasil_final['nama_paket']; ?></div>
                        <small style="color: #2e7d32; font-weight: bold;">
                            Jalur: <?php echo str_replace('_', ' ', $hasil_final['status_seleksi']); ?>
                        </small>
                    </div>
                    <p style="font-size: 11px; margin-top: 15px; font-style: italic;">
                        *Silakan hubungi Guru BK untuk proses selanjutnya.
                    </p>
                <?php else: ?>
                    <div class="waktu" style="color: #d32f2f;">
                        Data Anda tidak ditemukan atau Anda tidak melakukan pemilihan hingga batas waktu berakhir.
                    </div>
                <?php endif; ?>
            <?php endif; ?>

            <a href="dashboard.php" class="btn-back">
                <span class="material-icons" style="vertical-align: middle; font-size: 18px;">arrow_back</span> Kembali ke Dashboard
            </a>
        </div>
    </body>
    </html>
    <?php
    exit(); 
}

// --- JIKA DALAM JADWAL, LANJUTKAN KODE DI BAWAH ---
// (Kode pemilihanmapel.php yang sebelumnya tetap di sini)




// --- CEK JADWAL ---
$q_jam = mysqli_query($koneksi, "SELECT * FROM setting_pendaftaran WHERE id=1");
$jam = mysqli_fetch_assoc($q_jam);
$skrg = date('Y-m-d H:i:s');

$is_buka = ($skrg >= $jam['waktu_buka'] && $skrg <= $jam['waktu_tutup']);
// ------------------

// ==========================================
// MESIN PEMERINGKATAN OTOMATIS (2 JAM SEKALI)
// ==========================================
$file_cache_waktu = 'waktu_seleksi_terakhir.txt';
$waktu_sekarang = time();
$waktu_terakhir = file_exists($file_cache_waktu) ? (int)file_get_contents($file_cache_waktu) : 0;
$selisih_waktu = $waktu_sekarang - $waktu_terakhir;

if ($selisih_waktu > 7200) {
    include 'proses_seleksi_otomatis.php'; 
    
    // Simpan waktu terbaru ke dalam file
    file_put_contents($file_cache_waktu, $waktu_sekarang);
    $waktu_terakhir = $waktu_sekarang;
}

$teks_update_terakhir = date('d M Y, H:i', $waktu_terakhir) . " WIB";
$teks_update_berikutnya = date('d M Y, H:i', $waktu_terakhir + 7200) . " WIB";

// 2. Cek Apakah Siswa Sudah Memilih Sebelumnya (PINDAHKAN KE SINI)
$q_cek_pilihan = mysqli_query($koneksi, "SELECT * FROM seleksi_paket WHERE nis='$nis'");
$sudah_memilih = mysqli_fetch_assoc($q_cek_pilihan);

// Ambil Status Nasib Siswa Saat Ini (BARU DICEK DI SINI)
$status_sementara = null;
if ($sudah_memilih) {
    // Ambil nama paket tempat dia 'hinggap' sementara
    $id_paket_final = $sudah_memilih['id_paket_final'];
    $q_paket_final = mysqli_query($koneksi, "SELECT nama_paket FROM paket_mapel WHERE id_paket='$id_paket_final'");
    $paket_final = mysqli_fetch_assoc($q_paket_final);
    $nama_paket_sementara = $paket_final ? $paket_final['nama_paket'] : "Menunggu Pemeringkatan";
}

// 3. Ambil Daftar Paket Mapel
$q_paket = mysqli_query($koneksi, "SELECT * FROM paket_mapel");

// ==========================================
// KAMUS PEMETAAN (MAPPING) UNTUK TAMPILAN
// ==========================================
$nama_peminatan = [
    'matematika_lanjut' => 'Matematika Lanjut',
    'sejarah_lanjut'    => 'Sejarah Lanjut',
    'geografi'          => 'Geografi',
    'ekonomi'           => 'Ekonomi',
    'sosiologi'         => 'Sosiologi',
    'fisika'            => 'Fisika',
    'kimia'             => 'Kimia',
    'biologi'           => 'Biologi',
    'bahasa_perancis'   => 'B. Perancis',
    'pkwu'              => 'Seni, Budaya dan Prakarya',
    'informatika'       => 'Informatika',
    'kka'               => 'Koding & AI'
];

$nama_rujukan = [
    'matematika_lanjut' => 'Matematika',
    'sejarah_lanjut'    => 'IPS',
    'geografi'          => 'IPS',
    'ekonomi'           => 'IPS',
    'sosiologi'         => 'IPS',
    'fisika'            => 'IPA',
    'kimia'             => 'IPA',
    'biologi'           => 'IPA',
    'bahasa_perancis'   => 'B. Inggris',
    'pkwu'              => 'Seni Prakarya',
    'informatika'       => 'Informatika',
    'kka'               => 'Informatika'
];
?>
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Smania - Pemilihan Mapel</title>
    
    <link rel="stylesheet" href="css/light.css"> 
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
    
    <style>
        body { 
            background-color: var(--md-sys-color-background); 
            color: var(--md-sys-color-on-background);
            font-family: 'Roboto', sans-serif;
            margin: 0 auto; max-width: 480px; min-height: 100vh;
            padding-bottom: 90px;
        }
        .header-top {
            padding: 20px; background-color: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary);
            border-bottom-left-radius: 24px; border-bottom-right-radius: 24px;
            display: flex; align-items: center; gap: 15px; box-shadow: 0 4px 10px rgba(0,0,0,0.1);
        }
        .header-top h2 { margin: 0; font-size: 18px; }
        .content-area { padding: 20px; }
        .card { background: var(--md-sys-color-surface-container-high); border-radius: 24px; padding: 20px; margin-bottom: 20px; border: 1px solid var(--md-sys-color-outline-variant); }
        .grid-nilai { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 15px; }
        .item-nilai { background: #f5f5f5; padding: 10px; border-radius: 12px; text-align: center; }
        .item-nilai span { font-size: 11px; color: #666; display: block; margin-bottom: 4px; }
        .item-nilai b { font-size: 18px; color: var(--md-sys-color-primary); }
        .rata-rata-box { grid-column: span 3; background: #e3f2fd; color: #1565c0; margin-top: 5px; }
        .table-paket { width: 100%; border-collapse: collapse; margin-top: 10px; }
        .table-paket th { font-size: 12px; text-align: center; padding: 10px; border-bottom: 2px solid #ddd; }
        .table-paket td { padding: 15px 5px; border-bottom: 1px solid #eee; font-size: 14px; text-align: center; vertical-align: top;}
        .table-paket td:first-child { text-align: left; }
        .detail-paket { background: #f9f9f9; padding: 8px; border-radius: 8px; margin-top: 8px; border: 1px dashed #ccc; }
        .detail-paket p { margin: 0 0 4px 0; font-size: 11px; color: #555; line-height: 1.4; }
        .badge-mapel { color: var(--md-sys-color-primary); font-weight: bold; }
        .badge-nilai { color: #e65100; font-weight: bold; }
        
        .btn-submit {
            background-color: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary);
            border: none; border-radius: 100px; padding: 16px; width: 100%; font-weight: bold; font-size: 16px;
            cursor: pointer; margin-top: 10px;
        }
        .btn-submit:disabled { background: #ccc !important; cursor: not-allowed; }
        .alert-success { background: #e8f5e9; color: #2e7d32; padding: 12px; border-radius: 12px; margin-bottom: 15px; font-size: 13px; display: flex; align-items: center; gap: 8px; border: 1px solid #c8e6c9; }
    </style>
</head>
<body class="light">

    <div class="header-top">
        <a href="dashboard.php" style="color: inherit;"><span class="material-icons">arrow_back</span></a>
        <h2>Pemilihan Paket Mapel</h2>
    </div>

    <div class="content-area">
        
        <div class="card" style="background: #1a73e8; color: white; border: none; text-align: center;">
    <p style="margin: 0; font-size: 12px; opacity: 0.9;">Waktu Tersisa Untuk Memilih/Mengubah:</p>
    <div id="countdown" style="font-size: 24px; font-weight: bold; margin: 5px 0;">
        00 : 00 : 00 : 00
    </div>
    <p id="tgl-tutup" style="margin: 0; font-size: 10px; opacity: 0.8;">
        Hingga: <?php echo date('d M Y, H:i', strtotime($st['waktu_tutup'])); ?> WIB
    </p>
</div>

<script>
    // Ambil waktu tutup dari PHP ke format JavaScript
    const waktuTutup = new Date("<?php echo date('Y-m-d H:i:s', strtotime($st['waktu_tutup'])); ?>").getTime();

    const x = setInterval(function() {
        const sekarang = new Date().getTime();
        const selisih = waktuTutup - sekarang;

        // Kalkulasi Hari, Jam, Menit, Detik
        const hari = Math.floor(selisih / (1000 * 60 * 60 * 24));
        const jam = Math.floor((selisih % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const menit = Math.floor((selisih % (1000 * 60 * 60)) / (1000 * 60));
        const detik = Math.floor((selisih % (1000 * 60)) / 1000);

        // Tampilkan hasil di elemen id="countdown"
        document.getElementById("countdown").innerHTML = 
            (hari > 0 ? hari + " <small>hr</small> " : "") + 
            jam.toString().padStart(2, '0') + " : " + 
            menit.toString().padStart(2, '0') + " : " + 
            detik.toString().padStart(2, '0');

        // Jika waktu habis, reload halaman untuk mengunci akses
        if (selisih < 0) {
            clearInterval(x);
            document.getElementById("countdown").innerHTML = "WAKTU HABIS";
            setTimeout(function(){ location.reload(); }, 2000);
        }
    }, 1000);
</script>
        
        <div class="card" style="background: #fff8e1; border-left: 5px solid #ffb300; padding: 15px;">
            <h3 style="margin: 0 0 10px 0; font-size: 14px; color: #f57c00; display: flex; align-items: center; gap: 5px;">
                <span class="material-icons" style="font-size: 18px;">info</span> Aturan Seleksi
            </h3>
            <ul style="margin: 0; padding-left: 20px; font-size: 12px; color: #555; line-height: 1.6;">
                <li><b>Prioritas Nilai:</b> Murni menggunakan peringkat nilai dari mapel yang disyaratkan paket.</li>
                <li><b>Pemutus Seri:</b> Jika nilai imbang, diurutkan berdasarkan <b>Rata-Rata Keseluruhan</b>.</li>
                <li><b>Otomatisasi:</b> Jika tergeser dari Pilihan 1, sistem otomatis mengadu nilai Anda di Pilihan 2.</li>
                <li><b>Otomatisasi:</b> Jika tergeser dari Pilihan 1 dan 2, sistem otomatis Mengalihkan ke Paket yang belum terisi</li>
            </ul>
        </div>

        <div class="card">
            <h3 style="margin: 0 0 5px 0; font-size: 16px; color: var(--md-sys-color-primary);">Data Nilai Rapor Anda</h3>
            <p style="margin: 0; font-size: 12px; color: #666;">Berdasarkan nilai Semester 1</p>
            
            <?php if($nilai): ?>
            <div class="grid-nilai">
                <div class="item-nilai"><span>Agama</span><b><?php echo $nilai['pendidikan_agama']; ?></b></div>
                <div class="item-nilai"><span>Pancasila</span><b><?php echo $nilai['pendidikan_pancasila']; ?></b></div>
                <div class="item-nilai"><span>B. Indo</span><b><?php echo $nilai['bahasa_indonesia']; ?></b></div>
                <div class="item-nilai"><span>B. Ing</span><b><?php echo $nilai['bahasa_inggris']; ?></b></div>
                <div class="item-nilai"><span>Matematika</span><b><?php echo $nilai['matematika']; ?></b></div>
                <div class="item-nilai"><span>IPA</span><b><?php echo $nilai['ipa']; ?></b></div>
                <div class="item-nilai"><span>IPS</span><b><?php echo $nilai['ips']; ?></b></div>
                <div class="item-nilai"><span>PJOK</span><b><?php echo $nilai['pjok']; ?></b></div>
                <div class="item-nilai"><span>Informatika</span><b><?php echo $nilai['informatika']; ?></b></div>
                <div class="item-nilai"><span>Senbud Prakarya</span><b><?php echo $nilai['seni_budaya_prakarya']; ?></b></div>
                <div class="item-nilai"><span>B. Jawa</span><b><?php echo $nilai['bahasa_jawa']; ?></b></div>
                
                <div class="item-nilai rata-rata-box">
                    <span style="color: #1565c0;">Rata-Rata Keseluruhan</span>
                    <b style="font-size: 24px;"><?php echo number_format($nilai['rata_rata'], 1); ?></b>
                </div>
            </div>
            <?php else: ?>
                <p style="color: red; font-size: 12px; margin-top: 10px;">Data nilai belum tersedia.</p>
            <?php endif; ?>
        </div>

        <div class="card">
            <h3 style="margin: 0 0 15px 0; font-size: 16px; color: var(--md-sys-color-primary);">Pilih Paket Kelas</h3>
            
            <?php if(!$nilai): ?>
                <p style="font-size: 13px; color: #666; text-align: center;">Belum bisa memilih, nilai rapor kosong.</p>
            <?php else: ?>
                
                <?php if($sudah_memilih): ?>
                    <div class="alert-success">
                        <span class="material-icons">check_circle</span>
                        <div>Anda sudah menyimpan pilihan. Anda masih dapat mengubahnya selama masa pendaftaran dibuka.</div>
                    </div>
                <?php endif; ?>

                <form action="pemilihan_proses.php" method="POST" id="formPilih">
                    <table class="table-paket">
                        <tr>
                            <th>Daftar Paket</th>
                            <th>P 1</th>
                            <th>P 2</th>
                        </tr>
                        <?php while($p = mysqli_fetch_assoc($q_paket)): 
                            
                            $idp = $p['id_paket'];
                            $q_s = mysqli_query($koneksi, "SELECT nama_mapel_rapor FROM syarat_mapel_paket WHERE id_paket='$idp'");
                            
                            $arr_mapel = [];
                            $arr_nilai = [];
                            
                            while($s = mysqli_fetch_assoc($q_s)) {
                                $kunci = $s['nama_mapel_rapor'];
                                $arr_mapel[] = $nama_peminatan[$kunci];
                                $arr_nilai[] = $nama_rujukan[$kunci];
                            }
                            
                            $str_mapel = implode(", ", $arr_mapel);
                            $jml = count($arr_nilai);
                            $str_nilai = ($jml > 1) ? "(" . implode(" + ", $arr_nilai) . ") / $jml" : implode("", $arr_nilai);
                            
                            if(empty($str_mapel)){
                                $str_mapel = "Belum diatur admin";
                                $str_nilai = "-";
                            }
                        ?>
                        <tr>
                            <td>
                                <b style="font-size: 15px; color: #333;"><?php echo $p['nama_paket']; ?></b> (Kuota : <?php echo $p['kuota']; ?> Siswa)
                                <div class="detail-paket">
                                    <p><span class="badge-mapel">Mapel:</span> <?php echo $str_mapel; ?></p>
                                    <p><span class="badge-nilai">Nilai Seleksi:</span> <?php echo $str_nilai; ?></p>
                                </div>
                            </td>
                            <td>
                                <input type="radio" name="pilihan_1" value="<?php echo $p['id_paket']; ?>" required class="radio-pil1" 
                                <?php echo ($sudah_memilih && $sudah_memilih['pilihan_1'] == $p['id_paket']) ? 'checked' : ''; ?>>
                            </td>
                            <td>
                                <input type="radio" name="pilihan_2" value="<?php echo $p['id_paket']; ?>" required class="radio-pil2" 
                                <?php echo ($sudah_memilih && $sudah_memilih['pilihan_2'] == $p['id_paket']) ? 'checked' : ''; ?>>
                            </td>
                        </tr>
                        <?php endwhile; ?>
                    </table>
                    <p id="pesan-error" style="color: red; font-size: 12px; text-align: center; display: none;">Pilihan 1 dan Pilihan 2 tidak boleh sama!</p>
                    <button type="submit" class="btn-submit" id="btn-kirim">
                        <?php echo ($sudah_memilih) ? "Perbarui Pilihan Saya" : "Simpan Pilihan"; ?>
                    </button>
                </form>
            <?php endif; ?>
        </div>

        <?php if($sudah_memilih): 
            $id_pf = $sudah_memilih['id_paket_final'];
            $q_pf = mysqli_query($koneksi, "SELECT nama_paket FROM paket_mapel WHERE id_paket='$id_pf'");
            $pf = mysqli_fetch_assoc($q_pf);
            $tampil_paket = ($pf) ? $pf['nama_paket'] : "Menunggu Antrean Sistem...";

            $status_code = $sudah_memilih['status_seleksi'];
            $bg = "#9E9E9E"; $txt = "Sedang Dihitung";
            if($status_code == 'Diterima_Pil1'){ $bg="#4CAF50"; $txt="Diterima (Pilihan 1)"; }
            elseif($status_code == 'Diterima_Pil2'){ $bg="#2196F3"; $txt="Tergeser ke Pilihan 2"; }
            elseif($status_code == 'Dialihkan_Sistem'){ $bg="#FF9800"; $txt="Dialihkan Acak (Penuh)"; }
        ?>
        <div class="card" style="border-left: 5px solid #2196f3; background: #e3f2fd;">
            <h3 style="margin: 0 0 15px 0; font-size: 16px; color: #1565c0; display: flex; align-items: center; gap: 8px;">
                <span class="material-icons">hourglass_top</span> Status Pengumuman
            </h3>
            <div style="background: #fff; border-radius: 16px; padding: 15px; border: 1px solid #bbdefb;">
                <p style="margin: 0; font-size: 13px;">Posisi sementara Anda:</p>
                <h2 style="margin: 5px 0 10px 0; color: #1565c0; font-size: 20px;"><?php echo $tampil_paket; ?></h2>
                <span style="color:#fff; background:<?php echo $bg; ?>; padding:5px 12px; border-radius:20px; font-size:11px; font-weight:bold;"><?php echo $txt; ?></span>
                <div style="background:#f5f5f5; padding:10px; border-radius:10px; font-size:10px; color:#777; margin-top:15px;">
                    Seleksi Terakhir: <?php echo $teks_update_terakhir; ?><br>
                    Seleksi Berikutnya: <?php echo $teks_update_berikutnya; ?>
                </div>
            </div>
        </div>
        <?php endif; ?>
        
            </div>
        </div>

    </div>

<script>
    const form = document.getElementById('formPilih');
    const pil1 = document.querySelectorAll('.radio-pil1');
    const pil2 = document.querySelectorAll('.radio-pil2');
    const pesanError = document.getElementById('pesan-error');
    const btnKirim = document.getElementById('btn-kirim');

    function cekPilihan() {
        let val1 = document.querySelector('input[name="pilihan_1"]:checked')?.value;
        let val2 = document.querySelector('input[name="pilihan_2"]:checked')?.value;

        if (val1 && val2 && val1 === val2) {
            pesanError.style.display = 'block';
            btnKirim.disabled = true;
            btnKirim.style.background = '#ccc';
        } else {
            pesanError.style.display = 'none';
            btnKirim.disabled = false;
            btnKirim.style.background = 'var(--md-sys-color-primary)';
        }
    }

    cekPilihan();
    pil1.forEach(radio => radio.addEventListener('change', cekPilihan));
    pil2.forEach(radio => radio.addEventListener('change', cekPilihan));
</script>

</body>
</html>