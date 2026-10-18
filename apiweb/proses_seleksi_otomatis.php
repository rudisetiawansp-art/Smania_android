<?php
require_once 'koneksi.php';

// 1. Kosongkan semua status menjadi Pending
mysqli_query($koneksi, "UPDATE seleksi_paket SET status_seleksi = 'Pending', id_paket_final = NULL");

// 2. Ambil kuota setiap paket
$q_semua_paket = mysqli_query($koneksi, "SELECT id_paket, kuota FROM paket_mapel");
$daftar_paket = [];
while ($row = mysqli_fetch_assoc($q_semua_paket)) {
    $daftar_paket[$row['id_paket']] = (int)$row['kuota'];
}

$rujukan = [
    'matematika_lanjut' => 'matematika', 'sejarah_lanjut' => 'ips', 'geografi' => 'ips',
    'ekonomi'           => 'ips', 'sosiologi'      => 'ips', 'fisika'   => 'ipa', 'kimia' => 'ipa',
    'biologi'           => 'ipa', 'bahasa_perancis'=> 'bahasa_inggris', 'pkwu'  => 'seni_budaya_prakarya',
    'informatika'       => 'informatika', 'kka'    => 'informatika'
];

// 3. FASE 1: PROSES PILIHAN 1
foreach ($daftar_paket as $id_p => $kuota_p) {
    // Cari rumus nilai untuk paket ini
    $q_syarat = mysqli_query($koneksi, "SELECT nama_mapel_rapor FROM syarat_mapel_paket WHERE id_paket = '$id_p'");
    $fields = [];
    if ($q_syarat) {
        while ($s = mysqli_fetch_assoc($q_syarat)) { 
            $kunci = trim($s['nama_mapel_rapor']); // Bersihkan spasi tidak terlihat
            if (isset($rujukan[$kunci]) && !empty($rujukan[$kunci])) {
                $fields[] = "n." . $rujukan[$kunci]; 
            }
        }
    }
    // Jika tidak ada syarat yang valid, gunakan rata_rata sebagai default untuk menghindari error SQL
    $rumus_nilai = (count($fields) > 0) ? "(" . implode(" + ", $fields) . ") / " . count($fields) : "n.rata_rata";

    $q_ranking = mysqli_query($koneksi, "
        SELECT s.id_seleksi FROM seleksi_paket s
        JOIN nilai_rapor_smt1 n ON s.nis = n.nis
        WHERE s.pilihan_1 = '$id_p' AND s.status_seleksi = 'Pending'
        ORDER BY $rumus_nilai DESC, n.rata_rata DESC LIMIT $kuota_p
    ");

    if ($q_ranking) { // Mencegah loop jika query gagal
        while ($lolos = mysqli_fetch_assoc($q_ranking)) {
            $id_sel = $lolos['id_seleksi'];
            mysqli_query($koneksi, "UPDATE seleksi_paket SET status_seleksi = 'Diterima_Pil1', id_paket_final = '$id_p' WHERE id_seleksi = '$id_sel'");
        }
    }
}

// 4. FASE 2: PROSES PILIHAN 2
foreach ($daftar_paket as $id_p => $kuota_p) {
    $q_terisi = mysqli_query($koneksi, "SELECT COUNT(*) as total FROM seleksi_paket WHERE id_paket_final = '$id_p'");
    $terisi = mysqli_fetch_assoc($q_terisi)['total'];
    $sisa_kuota = $kuota_p - $terisi;

    if ($sisa_kuota > 0) {
        $q_syarat = mysqli_query($koneksi, "SELECT nama_mapel_rapor FROM syarat_mapel_paket WHERE id_paket = '$id_p'");
        $fields = [];
        if ($q_syarat) {
            while ($s = mysqli_fetch_assoc($q_syarat)) { 
                $kunci = trim($s['nama_mapel_rapor']);
                if (isset($rujukan[$kunci]) && !empty($rujukan[$kunci])) {
                    $fields[] = "n." . $rujukan[$kunci]; 
                }
            }
        }
        $rumus_nilai = (count($fields) > 0) ? "(" . implode(" + ", $fields) . ") / " . count($fields) : "n.rata_rata";

        $q_ranking2 = mysqli_query($koneksi, "
            SELECT s.id_seleksi FROM seleksi_paket s
            JOIN nilai_rapor_smt1 n ON s.nis = n.nis
            WHERE s.pilihan_2 = '$id_p' AND s.status_seleksi = 'Pending'
            ORDER BY $rumus_nilai DESC, n.rata_rata DESC LIMIT $sisa_kuota
        ");

        if ($q_ranking2) { // Mencegah error jika query gagal
            while ($lolos2 = mysqli_fetch_assoc($q_ranking2)) {
                $id_sel2 = $lolos2['id_seleksi'];
                mysqli_query($koneksi, "UPDATE seleksi_paket SET status_seleksi = 'Diterima_Pil2', id_paket_final = '$id_p' WHERE id_seleksi = '$id_sel2'");
            }
        }
    }
}

// 5. FASE 3: SISA KUOTA (DIALIHKAN SISTEM)
$q_telantar = mysqli_query($koneksi, "SELECT id_seleksi FROM seleksi_paket WHERE status_seleksi = 'Pending'");
while ($sisa = mysqli_fetch_assoc($q_telantar)) {
    $id_s = $sisa['id_seleksi'];
    
    // Perbaikan tambahan: Ditambahkan ORDER BY COUNT ASC agar mendahulukan kelas yang paling sepi
    $q_kosong = mysqli_query($koneksi, "
        SELECT p.id_paket 
        FROM paket_mapel p
        LEFT JOIN seleksi_paket s ON p.id_paket = s.id_paket_final
        GROUP BY p.id_paket 
        HAVING COUNT(s.id_seleksi) < MAX(p.kuota) 
        ORDER BY COUNT(s.id_seleksi) ASC 
        LIMIT 1
    ");
    
    if ($q_kosong && $p_acak = mysqli_fetch_assoc($q_kosong)) {
        $id_acak = $p_acak['id_paket'];
        mysqli_query($koneksi, "UPDATE seleksi_paket SET 
            status_seleksi = 'Dialihkan_Sistem', 
            id_paket_final = '$id_acak' 
        WHERE id_seleksi = '$id_s'");
    }
}

// Redirect jika dipanggil dari tombol admin
if(isset($_GET['redirect']) && $_GET['redirect'] == 'admin'){
    header("location: admin_dashboard.php?pesan=seleksi_selesai&section=mapil");
}
?>