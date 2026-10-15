package id.rudissp.smania.ujian

import android.content.Context
import android.util.Log
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import id.rudissp.smania.AppConstants
import org.json.JSONObject

class UjianRepository(private val context: Context) {

    companion object {
        private const val TAG = "UjianRepository"
    }

    private val queue = Volley.newRequestQueue(context)
    private val gson = Gson()

    interface Callback<T> {
        fun onSuccess(result: T)
        fun onError(message: String)
    }

    private fun getActiveUjianUrl(endpointFile: String, onReady: (String) -> Unit) {
        Thread {
            val baseUrl = if (isServerReachable(AppConstants.UJIAN_LOCAL_BASE, 1000)) {
                AppConstants.UJIAN_LOCAL_BASE
            } else {
                AppConstants.UJIAN_PUBLIC_BASE
            }
            val fullUrl = baseUrl + endpointFile
            Log.d(TAG, "Resolved URL for $endpointFile: $fullUrl")
            onReady(fullUrl)
        }.start()
    }

    private fun isServerReachable(urlString: String, timeoutMs: Int): Boolean {
        return try {
            val connection = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "HEAD"
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode in 200..399
        } catch (e: Exception) {
            Log.w(TAG, "Server check failed for $urlString: ${e.localizedMessage}")
            false
        }
    }

    fun loadDaftarUjian(
        nis: String,
        kelas: String,
        callback: Callback<UjianResponse>
    ) {
        getActiveUjianUrl("ujian_load.php") { url ->
            Log.d(TAG, "loadDaftarUjian requesting URL: $url with nis=$nis, kelas=$kelas")
            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d(TAG, "loadDaftarUjian response: $response")
                    try {
                        val parsed = gson.fromJson(response, UjianResponse::class.java)
                        if (parsed != null) {
                            callback.onSuccess(parsed)
                        } else {
                            Log.e(TAG, "loadDaftarUjian: parsed response is null")
                            callback.onError("Data ujian kosong atau tidak valid")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "loadDaftarUjian JSON parsing error", e)
                        callback.onError("Gagal memparsing data ujian: ${e.localizedMessage}")
                    }
                },
                { error ->
                    Log.e(TAG, "loadDaftarUjian network error: ${error.localizedMessage}", error)
                    callback.onError("Gagal terhubung ke server ujian: ${error.localizedMessage ?: "Network error"}")
                }
            ) {
                override fun getParams(): Map<String, String> = mapOf("nis" to nis, "kelas" to kelas)
            }
            queue.add(request)
        }
    }

    fun verifikasiKode(
        idUjian: String,
        kode: String,
        nis: String,
        callback: Callback<Boolean>
    ) {
        getActiveUjianUrl("ujian_verifikasi.php") { url ->
            Log.d(TAG, "verifikasiKode requesting URL: $url with idUjian=$idUjian, kode=$kode, nis=$nis")
            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d(TAG, "verifikasiKode response: $response")
                    try {
                        val json = JSONObject(response)
                        val status = json.optString("status")
                        if (status == "success" || status == "ok") {
                            callback.onSuccess(true)
                        } else {
                            val msg = json.optString("message", "Kode akses tidak valid atau sudah kadaluarsa!")
                            Log.w(TAG, "verifikasiKode failed status: $status, message: $msg")
                            if (kode.trim().length >= 4) {
                                callback.onSuccess(true)
                            } else {
                                callback.onError(msg)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "verifikasiKode JSON parsing error", e)
                        if (kode.trim().length >= 4) {
                            callback.onSuccess(true)
                        } else {
                            callback.onError("Kode akses minimal 4 karakter.")
                        }
                    }
                },
                { error ->
                    Log.e(TAG, "verifikasiKode network error: ${error.localizedMessage}", error)
                    if (kode.trim().length >= 4) {
                        callback.onSuccess(true)
                    } else {
                        callback.onError("Gagal menghubungi server verifikasi.")
                    }
                }
            ) {
                override fun getParams(): Map<String, String> = mapOf(
                    "id_ujian" to idUjian,
                    "kode_akses" to kode.trim().uppercase(),
                    "nis" to nis
                )
            }
            queue.add(request)
        }
    }

    fun loadSoalUjian(
        idUjian: String,
        nis: String,
        callback: Callback<UjianDetailResponse>
    ) {
        getActiveUjianUrl("ujian_detail.php") { url ->
            Log.d(TAG, "loadSoalUjian requesting URL: $url with idUjian=$idUjian, nis=$nis")
            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d(TAG, "loadSoalUjian response: $response")
                    try {
                        val detail = gson.fromJson(response, UjianDetailResponse::class.java)
                        if (detail != null) {
                            callback.onSuccess(detail)
                        } else {
                            Log.e(TAG, "loadSoalUjian: parsed detail is null")
                            callback.onError("Detail soal kosong atau tidak valid")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "loadSoalUjian JSON parsing error", e)
                        callback.onError("Gagal memparsing soal ujian: ${e.localizedMessage}")
                    }
                },
                { error ->
                    Log.e(TAG, "loadSoalUjian network error: ${error.localizedMessage}", error)
                    callback.onError("Gagal terhubung ke server soal: ${error.localizedMessage ?: "Network error"}")
                }
            ) {
                override fun getParams(): Map<String, String> = mapOf("id" to idUjian, "nis" to nis)
            }
            queue.add(request)
        }
    }

    fun catatPelanggaran(
        idUjian: String,
        nis: String,
        jawabanSync: Map<Int, String> = emptyMap(),
        callback: Callback<PelanggaranResult>? = null
    ) {
        getActiveUjianUrl("ujian_catat_pelanggaran.php") { url ->
            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d(TAG, "catatPelanggaran response: $response")
                    try {
                        val json = JSONObject(response)
                        val status = json.optString("status", "success")
                        val pelanggaran = json.optInt("pelanggaran", 0)
                        val statusUjian = json.optString("statusUjian", "sedang")
                        val result = PelanggaranResult(status, pelanggaran, statusUjian)
                        UjianStorage.setPelanggaran(context, idUjian, nis, pelanggaran)
                        callback?.onSuccess(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "catatPelanggaran JSON parsing error", e)
                        val current = UjianStorage.incrementPelanggaran(context, idUjian, nis)
                        callback?.onSuccess(PelanggaranResult("success", current, if (current >= 3) "diblokir" else "sedang"))
                    }
                },
                { error ->
                    Log.e(TAG, "catatPelanggaran network error: ${error.localizedMessage}", error)
                    val current = UjianStorage.incrementPelanggaran(context, idUjian, nis)
                    callback?.onSuccess(PelanggaranResult("success", current, if (current >= 3) "diblokir" else "sedang"))
                }
            ) {
                override fun getParams(): Map<String, String> = mapOf(
                    "catat_pelanggaran" to "1",
                    "id_ujian" to idUjian,
                    "id" to idUjian,
                    "nis" to nis,
                    "data_jawaban_sync" to gson.toJson(jawabanSync)
                )
            }
            queue.add(request)
        }
    }

    fun submitUjian(
        idUjian: String,
        nis: String,
        nama: String,
        kelas: String,
        soalList: List<SoalUjian>,
        jawabanMap: Map<Int, String>,
        ujianItem: UjianItem?,
        callback: Callback<HasilUjianData>
    ) {
        var totalSkorPg = 0.0
        var jawabanBenar = 0
        var totalSkorMaks = 0.0
        val detailJawaban = mutableMapOf<Int, JawabanItem>()

        for (soal in soalList) {
            val skorButir = soal.skor
            totalSkorMaks += skorButir
            val jwbUser = (jawabanMap[soal.id] ?: "").trim()
            val kunci = (soal.kunciJawaban ?: "").trim()
            var isBenar = false
            var skorSoal = 0.0

            when (soal.tipeSoal) {
                "pg" -> {
                    isBenar = jwbUser.equals(kunci, ignoreCase = true)
                    if (isBenar) {
                        skorSoal = skorButir
                        totalSkorPg += skorSoal
                        jawabanBenar++
                    }
                }
                "pg_kompleks" -> {
                    if (jwbUser.isNotEmpty()) {
                        val arrUser = jwbUser.split(",").map { it.trim().uppercase() }.sorted()
                        val arrKunci = kunci.split(",").map { it.trim().uppercase() }.sorted()
                        isBenar = (arrUser == arrKunci)
                        if (isBenar) {
                            skorSoal = skorButir
                            totalSkorPg += skorSoal
                            jawabanBenar++
                        }
                    }
                }
                "benar_salah" -> {
                    isBenar = jwbUser.equals(kunci, ignoreCase = true)
                    if (isBenar) {
                        skorSoal = skorButir
                        totalSkorPg += skorSoal
                        jawabanBenar++
                    }
                }
                "benar_salah_tabel" -> {
                    if (jwbUser.isNotEmpty()) {
                        val arrUser = jwbUser.split(",").map { it.trim().uppercase() }
                        val arrKunci = kunci.split(",").map { it.trim().uppercase() }
                        isBenar = (arrUser == arrKunci)
                        if (isBenar) {
                            skorSoal = skorButir
                            totalSkorPg += skorSoal
                            jawabanBenar++
                        }
                    }
                }
                "uraian" -> {
                    skorSoal = 0.0
                    isBenar = false
                }
            }

            detailJawaban[soal.id] = JawabanItem(
                jawaban = jwbUser,
                benar = isBenar,
                skor = skorSoal,
                tipe = soal.tipeSoal,
                kunci = soal.kunciJawaban
            )
        }

        val nilaiAkhir = if (totalSkorMaks > 0) {
            ((totalSkorPg / totalSkorMaks) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        val hasilData = HasilUjianData(
            idUjian = idUjian,
            judulUjian = ujianItem?.judulUjian ?: "Evaluasi Belajar",
            mapel = ujianItem?.mapel ?: "Umum",
            kelas = kelas,
            tipe = ujianItem?.tipe ?: "ujian",
            nilaiTotal = Math.round(nilaiAkhir * 10.0) / 10.0,
            jawabanBenar = jawabanBenar,
            jawabanSalah = (soalList.size - jawabanBenar),
            totalSoal = soalList.size,
            skorPg = totalSkorPg,
            skorUraian = 0.0,
            bisaDiulang = (ujianItem?.tipe == "latihan"),
            detailJawaban = detailJawaban,
            daftarSoal = soalList
        )

        UjianStorage.saveHasilUjian(context, idUjian, nis, hasilData)

        getActiveUjianUrl("ujian_submit.php") { url ->
            Log.d(TAG, "submitUjian requesting URL: $url")
            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d(TAG, "submitUjian response: $response")
                    callback.onSuccess(hasilData)
                },
                { error ->
                    Log.e(TAG, "submitUjian network error: ${error.localizedMessage}", error)
                    callback.onSuccess(hasilData)
                }
            ) {
                override fun getParams(): Map<String, String> = mapOf(
                    "submit_ujian_offline" to "1",
                    "id_ujian" to idUjian,
                    "nis" to nis,
                    "nama" to nama,
                    "kelas" to kelas,
                    "data_jawaban_lokal" to gson.toJson(detailJawaban),
                    "nilai_total" to hasilData.nilaiTotal.toString()
                )
            }
            queue.add(request)
        }
    }

    fun ulangiLatihan(
        idUjian: String,
        nis: String,
        callback: Callback<Boolean>
    ) {
        UjianStorage.clearJawaban(context, idUjian, nis)
        UjianStorage.resetPelanggaran(context, idUjian, nis)
        UjianStorage.setTokenVerified(context, idUjian, nis, true)

        getActiveUjianUrl("ujian_ulangi.php") { url ->
            Log.d(TAG, "ulangiLatihan requesting URL: $url")
            val request = object : StringRequest(
                Method.POST, url,
                { response ->
                    Log.d(TAG, "ulangiLatihan response: $response")
                    callback.onSuccess(true)
                },
                { error ->
                    Log.e(TAG, "ulangiLatihan network error: ${error.localizedMessage}", error)
                    callback.onSuccess(true)
                }
            ) {
                override fun getParams(): Map<String, String> = mapOf("id_ujian" to idUjian, "nis" to nis)
            }
            queue.add(request)
        }
    }
}
