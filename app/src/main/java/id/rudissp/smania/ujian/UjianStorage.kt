package id.rudissp.smania.ujian

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object UjianStorage {
    private const val PREF_NAME = "PREF_SMANIA_UJIAN"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveJawaban(context: Context, idUjian: String, nis: String, jawabanMap: Map<Int, String>) {
        val json = Gson().toJson(jawabanMap)
        getPrefs(context).edit().putString("jawaban_${idUjian}_$nis", json).apply()
    }

    fun getJawaban(context: Context, idUjian: String, nis: String): MutableMap<Int, String> {
        val json = getPrefs(context).getString("jawaban_${idUjian}_$nis", null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<Map<Int, String>>() {}.type
            val map: Map<Int, String> = Gson().fromJson(json, type)
            map.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun saveSingleJawaban(context: Context, idUjian: String, nis: String, idSoal: Int, jawaban: String) {
        val current = getJawaban(context, idUjian, nis)
        current[idSoal] = jawaban
        saveJawaban(context, idUjian, nis, current)
    }

    fun clearJawaban(context: Context, idUjian: String, nis: String) {
        getPrefs(context).edit()
            .remove("jawaban_${idUjian}_$nis")
            .remove("ragu_${idUjian}_$nis")
            .remove("pelanggaran_${idUjian}_$nis")
            .apply()
    }

    fun saveRaguRagu(context: Context, idUjian: String, nis: String, raguSet: Set<Int>) {
        val json = Gson().toJson(raguSet.toList())
        getPrefs(context).edit().putString("ragu_${idUjian}_$nis", json).apply()
    }

    fun getRaguRagu(context: Context, idUjian: String, nis: String): MutableSet<Int> {
        val json = getPrefs(context).getString("ragu_${idUjian}_$nis", null) ?: return mutableSetOf()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            val list: List<Int> = Gson().fromJson(json, type)
            list.toMutableSet()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    fun getPelanggaran(context: Context, idUjian: String, nis: String): Int {
        return getPrefs(context).getInt("pelanggaran_${idUjian}_$nis", 0)
    }

    fun setPelanggaran(context: Context, idUjian: String, nis: String, count: Int) {
        getPrefs(context).edit().putInt("pelanggaran_${idUjian}_$nis", count).apply()
    }

    fun incrementPelanggaran(context: Context, idUjian: String, nis: String): Int {
        val current = getPelanggaran(context, idUjian, nis) + 1
        getPrefs(context).edit().putInt("pelanggaran_${idUjian}_$nis", current).apply()
        return current
    }

    fun resetPelanggaran(context: Context, idUjian: String, nis: String) {
        getPrefs(context).edit().putInt("pelanggaran_${idUjian}_$nis", 0).apply()
    }

    fun setTokenVerified(context: Context, idUjian: String, nis: String, verified: Boolean) {
        getPrefs(context).edit().putBoolean("token_verified_${idUjian}_$nis", verified).apply()
    }

    fun isTokenVerified(context: Context, idUjian: String, nis: String): Boolean {
        return getPrefs(context).getBoolean("token_verified_${idUjian}_$nis", false)
    }

    fun saveHasilUjian(context: Context, idUjian: String, nis: String, hasil: HasilUjianData) {
        val json = Gson().toJson(hasil)
        getPrefs(context).edit().putString("hasil_${idUjian}_$nis", json).apply()
    }

    fun getHasilUjian(context: Context, idUjian: String, nis: String): HasilUjianData? {
        val json = getPrefs(context).getString("hasil_${idUjian}_$nis", null) ?: return null
        return try {
            Gson().fromJson(json, HasilUjianData::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
