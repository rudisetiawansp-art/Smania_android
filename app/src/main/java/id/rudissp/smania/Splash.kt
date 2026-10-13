package id.rudissp.smania

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.BuildConfig
import id.rudissp.smania.databinding.ActivitySplashBinding
import org.json.JSONObject
import kotlin.jvm.java

class Splash : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)

        if (!isInternetAvailable()) {
            Toast.makeText(this, "Tidak ada koneksi internet", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        checkDeviceAndVersion()
    }

    private fun checkDeviceAndVersion() {
        val localVersionCode = id.rudissp.smania.BuildConfig.VERSION_CODE
        // URL backend PHP Anda
        val url = AppConstants.CEK_APP_VERSION

        val nis = prefs.getString("nis", "") ?: ""
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        if (!isLoggedIn || nis.isEmpty()) {
            continueToNextScreen()
            return
        }

        val request = @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val json = JSONObject(response)

                    if (json.has("status") && json.getString("status") == "error") {
                        val errorMessage = json.getString("message")
                        showErrorDialogAndExit(errorMessage)
                    } else {
                        val serverVersionCode = json.getInt("version_code")
                        val serverMessage = json.getString("message")
                        val downloadUrl = json.getString("url")

                        if (serverVersionCode > localVersionCode) {
                            showUpdateDialog(serverMessage, downloadUrl)
                        } else {
                            continueToNextScreen()
                        }
                    }
                } catch (e: Exception) {
                    showErrorDialogAndExit("Terjadi kesalahan saat memproses data.")
                }
            },
            { error ->
                Toast.makeText(this, "Gagal terhubung ke server, periksa koneksi anda.", Toast.LENGTH_SHORT).show()
                finish()
            }
        ) {
            // Menambahkan parameter ke dalam body request
            @SuppressLint("HardwareIds")
            override fun getParams(): MutableMap<String, String> {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val params = HashMap<String, String>()
                params["nis"] = nis
                params["device_id"] = deviceId
                return params
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showUpdateDialog(message: String, downloadUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("Update Tersedia")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Update Sekarang") { _, _ ->
                //startDownloadAndInstall(downloadUrl.toUri().toString())
                val intent = Intent(Intent.ACTION_VIEW, downloadUrl.toUri())
                startActivity(intent)
            }
            .setNegativeButton("Keluar") { _, _ ->
                finish()
            }
            .show()
    }

    private fun showErrorDialogAndExit(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ ->
                finish()
            }
            .show()
    }

    private fun continueToNextScreen() {
        Handler(Looper.getMainLooper()).postDelayed({
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)
            Log.d("SplashActivity", "Status login: $isLoggedIn")
            if (isLoggedIn) {



                startActivity(Intent(this, MainActivity::class.java))
                finish()

            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }, 1000) // delay 1 detik
    }
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


}