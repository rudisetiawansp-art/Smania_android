<?php
/**
 * KONEKSI DATABASE - DAPURHOSTING (cPanel)
 * ------------------------------------------------------------
 * CARA MENGISI (5 menit):
 * 1. Login cPanel -> menu "MySQL¢î Databases"
 * 2. Buat database, contoh nama: dapurho_keketatan
 * 3. Buat user database + password, contoh: dapurho_admin
 * 4. "Add User To Database" -> pilih user & db -> centang ALL PRIVILEGES
 * 5. cPanel otomatis menambahkan prefix akun di depan nama yang Anda
 *    ketik, misal user "dapurho_admin" menjadi "dapurho_admin" atau
 *    "dapurhos_dapurho_admin" (lihat persisnya di halaman cPanel),
 *    lalu salin persis ke bawah ini.
 * 6. Import dapurhosting/keketatan_data.sql via phpMyAdmin.
 * ------------------------------------------------------------
 */

define('DB_HOST', 'localhost');
define('DB_USER', 'smansuko_smania');
define('DB_PASS', 'smansuko_smania');
define('DB_NAME', 'smansuko_smania');
define('DB_TABLE', 'keketatan_ptn');
define('DB_TABLE_SNBT', 'keketatan_ptn_snbt'); // nama tabel SNBT/UTBK (dari keketatan_snbt.sql)

function koneksi_db(): mysqli {
    mysqli_report(MYSQLI_REPORT_OFF);
    $conn = @new mysqli(DB_HOST, DB_USER, DB_PASS, DB_NAME);
    if ($conn->connect_error) {
        http_response_code(500);
        die(
            '<div style="font-family:Arial,sans-serif;max-width:520px;margin:60px auto;'
            . 'padding:24px;border:1px solid #f5c2c0;border-radius:12px;background:#fdf0ef;">'
            . '<h2 style="margin-top:0;color:#c0392b;">Koneksi Database Gagal</h2>'
            . '<p><b>' . htmlspecialchars($conn->connect_error) . '</b></p>'
            . '<p>Periksa kredensial di file <code>koneksi.php</code>:</p>'
            . '<ul>'
            . '<li>DB_USER: <code>' . htmlspecialchars(DB_USER) . '</code></li>'
            . '<li>DB_NAME: <code>' . htmlspecialchars(DB_NAME) . '</code></li>'
            . '</ul>'
            . '<p>Di cPanel, pastikan user sudah di-<i>Add To Database</i> dengan '
            . 'ALL PRIVILEGES, dan database sudah di-import '
            . '<code>keketatan_data.sql</code>.</p>'
            . '</div>'
        );
    }
    $conn->set_charset('utf8mb4');
    return $conn;
}
