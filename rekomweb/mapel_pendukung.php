<?php
/**
 * AUTO-GENERATED oleh build_mapel_pendukung.py — jangan edit manual.
 * Sumber: data/master_jurusan.csv, kolom `kurikulum_merdeka`
 * (acuan mapel pendukung SNPMB untuk Kurikulum Merdeka).
 */

$MAPEL_KELOMPOK = [
    'Seni' => ['Seni Budaya'],
    'Sejarah' => ['Sejarah'],
    'Linguistik' => ['Bahasa Indonesia', 'Bahasa Inggris'],
    'Susastra atau Sastra' => ['Bahasa Indonesia', 'Bahasa Inggris'],
    'Filsafat' => ['Sosiologi'],
    'Sosial' => ['Sosiologi'],
    'Ekonomi' => ['Ekonomi', 'Matematika'],
    'Pertahanan' => ['Pendidikan Pancasila'],
    'Psikologi' => ['Sosiologi', 'Matematika'],
    'Kimia' => ['Kimia'],
    'Ilmu atau Sains Kebumian' => ['Fisika', 'Matematika'],
    'Ilmu atau Sains Kelautan' => ['Biologi'],
    'Biologi' => ['Biologi'],
    'Biofisika' => ['Fisika'],
    'Fisika' => ['Fisika'],
    'Astronomi' => ['Fisika', 'Matematika'],
    'Komputer' => ['Matematika'],
    'Logika' => ['Matematika'],
    'Matematika' => ['Matematika'],
    'Ilmu dan Sains Pertanian' => ['Biologi'],
    'Peternakan' => ['Biologi'],
    'Ilmu atau Sains Perikanan' => ['Biologi'],
    'Arsitektur' => ['Matematika', 'Fisika'],
    'Perencanaan Wilayah' => ['Ekonomi', 'Matematika'],
    'Desain' => ['Seni Budaya', 'Matematika'],
    'Ilmu atau Sains Akuntansi' => ['Ekonomi', 'Matematika'],
    'Ilmu atau Sains Manajemen' => ['Ekonomi', 'Matematika'],
    'Logistik' => ['Ekonomi', 'Matematika'],
    'Administrasi Bisnis' => ['Ekonomi', 'Matematika'],
    'Bisnis' => ['Ekonomi', 'Matematika'],
    'Ilmu atau Sains Komunikasi' => ['Sosiologi', 'Antropologi'],
    'Pendidikan' => ['Bahasa Indonesia', 'Bahasa Inggris', 'Matematika'],
    'Teknik atau rekayasa' => ['Fisika', 'Kimia', 'Matematika'],
    'Ilmu atau Sains Lingkungan' => ['Biologi'],
    'Kehutanan' => ['Biologi'],
    'Ilmu atau Sains Kedokteran' => ['Biologi', 'Kimia'],
    'Ilmu atau Sains Kedokteran Gigi' => ['Biologi', 'Kimia'],
    'Ilmu atau Sains Veteriner' => ['Biologi', 'Kimia'],
    'Ilmu Farmasi' => ['Biologi', 'Kimia'],
    'Ilmu atau Sains Gizi' => ['Biologi', 'Kimia'],
    'Kesehatan Masyarakat' => ['Biologi'],
    'Kebidanan' => ['Biologi'],
    'Keperawatan' => ['Biologi'],
    'Kesehatan' => ['Biologi'],
    'Ilmu atau Sains Informasi' => ['Matematika'],
    'Hukum' => ['Sosiologi', 'Pendidikan Pancasila'],
    'Ilmu atau Sains Militer' => ['Sosiologi'],
    'Urusan Publik' => ['Sosiologi'],
    'Ilmu atau Sains Keolahragaan' => ['Pendidikan Jasmani, Olahraga, dan Kesehatan (PJOK)', 'Biologi'],
    'Pariwisata' => ['Ekonomi', 'Bahasa Inggris'],
    'Transportasi' => ['Matematika'],
    'Bioteknologi, Biokewirausahaan, Bioinformatika' => ['Biologi', 'Matematika'],
    'Geografi, Geografi Lingkungan, Sains Informasi Geografi' => ['Geografi', 'Matematika'],
    'Informatika Medis atau Informatika Kesehatan' => ['Biologi', 'Matematika'],
    'Konservasi Biologi, Konservasi Hewan Liar, Konservasi Hewan Liar dan Hutan, Konservasi Hutan, Konservasi Sumber Daya Alam' => ['Biologi'],
    'Teknologi Pangan, Teknologi Hasil Pertanian/Peternakan/Perikanan' => ['Kimia', 'Biologi'],
    'Sains Data' => ['Matematika'],
    'Sains Perkopian' => ['Biologi'],
    'Studi Humanitas' => ['Antropologi', 'Sosiologi']
];

$MAPEL_UMUM = ['Bahasa Indonesia', 'Bahasa Inggris', 'Matematika'];

// Aturan deteksi kelompok dari nama prodi (urutan penting: spesifik lebih dulu).
$MAPEL_PRODI_RULES = [
    ['kedokteran gigi', 'Ilmu atau Sains Kedokteran Gigi'],
    ['kedokteran|medis', 'Ilmu atau Sains Kedokteran'],
    ['veteriner', 'Ilmu atau Sains Veteriner'],
    ['farmasi|apoteker', 'Ilmu Farmasi'],
    ['gizi', 'Ilmu atau Sains Gizi'],
    ['kebidanan', 'Kebidanan'],
    ['keperawatan', 'Keperawatan'],
    ['kesehatan masyarakat', 'Kesehatan Masyarakat'],
    ['olahraga', 'Ilmu atau Sains Keolahragaan'],
    ['kesehatan', 'Kesehatan'],
    ['bioteknologi|bioinformatika|biokewirausahaan', 'Bioteknologi, Biokewirausahaan, Bioinformatika'],
    ['teknologi pangan|pangan|boga', 'Teknologi Pangan, Teknologi Hasil Pertanian/Peternakan/Perikanan'],
    ['informatika|komputer|sistem informasi|teknologi informasi|rekayasa perangkat lunak', 'Komputer'],
    ['sains data|statistika|sains informasi|perpustakaan|arsip|aktuar', 'Ilmu atau Sains Informasi'],
    ['astronomi|astrofisika', 'Astronomi'],
    ['biofisika', 'Biofisika'],
    ['kebumian|geologi|geofisika|meteorologi|klimatologi|oceanografi|kebencanaan', 'Ilmu atau Sains Kebumian'],
    ['geografi', 'Geografi, Geografi Lingkungan, Sains Informasi Geografi'],
    ['kelautan|perikanan|pesisir|nautika|akuakultur|budidaya perairan|perairan|akuatik', 'Ilmu atau Sains Kelautan'],
    ['kehutanan|hutan|konservasi', 'Kehutanan'],
    ['peternakan', 'Peternakan'],
    ['pertanian|agronomi|agribisnis|agroteknologi|tanaman|ilmu tanah|agro|hortikultura', 'Ilmu dan Sains Pertanian'],
    ['biologi', 'Biologi'],
    ['matematika', 'Matematika'],
    ['fisika', 'Fisika'],
    ['kimia', 'Kimia'],
    ['arsitektur|interior|lanskap|pertamanan', 'Arsitektur'],
    ['perencanaan|tata kota|wilayah', 'Perencanaan Wilayah'],
    ['transportasi|penerbangan|perkeretaapian|perkapalan|logistik transportasi', 'Transportasi'],
    ['akuntansi', 'Ilmu atau Sains Akuntansi'],
    ['manajemen|bisnis|usaha', 'Ilmu atau Sains Manajemen'],
    ['administrasi', 'Administrasi Bisnis'],
    ['logistik', 'Logistik'],
    ['pariwisata|perhotelan|wisata', 'Pariwisata'],
    ['ekonomi|keuangan|perbankan|asuransi|pemasaran', 'Ekonomi'],
    ['antropologi|sosiologi|ilmu sosial|kesejahteraan|sosial|internasional', 'Sosial'],
    ['komunikasi|jurnalistik|penyiaran|humas|hubungan masyarakat|media', 'Ilmu atau Sains Komunikasi'],
    ['psikologi', 'Psikologi'],
    ['hukum', 'Hukum'],
    ['militer|pertahanan|keamanan', 'Ilmu atau Sains Militer'],
    ['urusan publik|pemerintahan|kependudukan|ketatanegaraan|politik', 'Urusan Publik'],
    ['teknik|rekayasa', 'Teknik atau rekayasa'],
    ['lingkungan', 'Ilmu atau Sains Lingkungan'],
    ['fisioterapi', 'Kesehatan'],
    ['ternak', 'Peternakan'],
    ['pajak', 'Ilmu atau Sains Akuntansi'],
    ['ppkn|pancasila', 'Pertahanan'],
    ['seni musik|seni rupa|seni tari|dkv|desain komunikasi|film|televisi|musik|tari|rupa|animasi|karawitan|teater|kriya|fotografi|busana|\bseni\b', 'Seni'],
    ['desain|dkv', 'Desain'],
    ['sastra|susastra|pengkajian|kepenulisan', 'Susastra atau Sastra'],
    ['linguistik|penerjemah', 'Linguistik'],
    ['sejarah|arkeologi', 'Sejarah'],
    ['filsafat|humanitas|kajian budaya', 'Filsafat'],
    ['pendidikan|guru|pgsd|pgpaud|paud|konseling|\bpend\b|pend\.', 'Pendidikan'],
    ['bahasa', 'Linguistik']
];

if (!function_exists('mapel_pendukung_prodi')) {
/** Kembalikan daftar mapel pendukung (Kurikulum Merdeka) untuk sebuah nama prodi. */
function mapel_pendukung_prodi($nama_prodi) {
    foreach ($GLOBALS['MAPEL_PRODI_RULES'] as $r) {
        if (preg_match('/' . $r[0] . '/i', $nama_prodi)) {
            $m = $GLOBALS['MAPEL_KELOMPOK'][$r[1]] ?? [];
            return $m ?: $GLOBALS['MAPEL_UMUM'];
        }
    }
    return $GLOBALS['MAPEL_UMUM'];
}
}
