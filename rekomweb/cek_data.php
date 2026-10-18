<?php
/**
 * cek_data.php - halaman pemeriksaan pasca-upload di dapurhosting.
 * Buka sekali setelah import SQL untuk memastikan data masuk,
 * lalu boleh dihapus dari server.
 */
require_once __DIR__ . '/koneksi.php';

$conn = koneksi_db();
$t = DB_TABLE;

$rows_total = 0; $rows_ptn = 0; $prodi_baru = 0; $sample = []; $ok = true;
$res = $conn->query("SELECT COUNT(*) c, COUNT(DISTINCT nama_ptn) p FROM `$t`");
if ($res) { $r = $res->fetch_assoc(); $rows_total = (int)$r['c']; $rows_ptn = (int)$r['p']; }
else { $ok = false; }

if ($ok) {
    $res2 = $conn->query("SELECT COUNT(*) c FROM `$t` WHERE peminat_thn_1 = 0 AND peminat_thn_2 = 0 AND peminat_thn_3 = 0");
    if ($res2) { $prodi_baru = (int)$res2->fetch_assoc()['c']; }
    $res3 = $conn->query("SELECT nama_ptn, nama_prodi, jenjang, kuota_terbaru, peminat_thn_1, peminat_thn_2, peminat_thn_3, rasio_keketatan
                          FROM `$t` ORDER BY rasio_keketatan DESC LIMIT 5");
    while ($res3 && ($r = $res3->fetch_assoc())) { $sample[] = $r; }
}
?>
<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Cek Data Keketatan</title>
<style>
    body { font-family: Arial, sans-serif; max-width: 640px; margin: 40px auto; padding: 0 16px; color: #333; }
    .card { border: 1px solid #e0e0e0; border-radius: 12px; padding: 20px; margin-bottom: 16px; }
    .ok { background: #e6f4ea; border-color: #137333; }
    .bad { background: #fce8e6; border-color: #c5221f; }
    table { border-collapse: collapse; width: 100%; font-size: 12px; }
    th, td { border: 1px solid #ddd; padding: 6px 8px; text-align: left; }
    th { background: #f1f3f4; }
    .num { text-align: right; }
    code { background: #f1f3f4; padding: 2px 6px; border-radius: 4px; }
</style>
</head>
<body>
    <h1>Pemeriksaan Data Keketatan</h1>

    <?php if (!$ok): ?>
        <div class="card bad">
            <b>Tabel <code><?= htmlspecialchars($t) ?></code> belum ditemukan.</b><br><br>
            Artinya file <code>keketatan_data.sql</code> belum di-import.<br>
            Caranya: cPanel &rarr; <b>phpMyAdmin</b> &rarr; pilih database Anda di panel kiri &rarr;
            tab <b>Import</b> &rarr; pilih file <code>keketatan_data.sql</code> &rarr; <b>Import</b>.
        </div>
    <?php else: ?>
        <div class="card ok">
            <b>Koneksi & tabel OK.</b><br>
            Total baris: <b><?= number_format($rows_total) ?></b> prodi dari
            <b><?= $rows_ptn ?></b> PTN akademik &middot;
            prodi baru (tanpa history): <b><?= number_format($prodi_baru) ?></b>
        </div>

        <div class="card">
            <b>5 prodi paling ketat (rasio tahun lalu):</b>
            <table>
                <tr><th>PTN</th><th>Prodi</th><th>Jenjang</th>
                    <th class="num">Kuota</th><th class="num">Peminat N-1</th><th class="num">Rasio</th></tr>
                <?php foreach ($sample as $s): ?>
                <tr>
                    <td><?= htmlspecialchars($s['nama_ptn']) ?></td>
                    <td><?= htmlspecialchars($s['nama_prodi']) ?></td>
                    <td><?= htmlspecialchars($s['jenjang']) ?></td>
                    <td class="num"><?= number_format($s['kuota_terbaru']) ?></td>
                    <td class="num"><?= number_format($s['peminat_thn_1']) ?></td>
                    <td class="num"><?= number_format((float)$s['rasio_keketatan'], 1) ?></td>
                </tr>
                <?php endforeach; ?>
            </table>
        </div>

        <div class="card">
            <b>Langkah selanjutnya:</b>
            <ol>
                <li>Pastikan <code>rekomendasi.php</code> berada di folder aplikasi siswa
                    (sejajar dengan file <code>nilai.php</code>), dan <code>koneksi.php</code>
                    satu folder dengannya.</li>
                <li>Uji buka <code>rekomendasi.php</code> dari menu aplikasi Anda.</li>
                <li>Halaman <code>cek_data.php</code> ini boleh dihapus dari server.</li>
            </ol>
        </div>
    <?php endif; ?>
</body>
</html>
