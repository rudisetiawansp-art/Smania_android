package id.rudissp.smania

import android.R.attr.password
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import id.rudissp.smania.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    lateinit var binding: ActivityLoginBinding
    private val queue by lazy { Volley.newRequestQueue(this) }

    override fun onCreate(savedInstanceState: Bundle?) {enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btSignIn.setOnClickListener {
            val nis = binding.etUser.text.toString()
            val pass= binding.etPass.text.toString()

            // Validasi input
            if (nis.isNotEmpty() && pass.isNotEmpty()) {
                login(nis, pass)
            } else {
                Toast.makeText(
                    this,
                    "Username atau password tidak boleh kosong",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }


    }

    @SuppressLint("HardwareIds")
    private fun login(nis: String, pass: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val stringRequest = object : StringRequest(Method.POST, AppConstants.LOGIN_URL,
            Response.Listener { response ->
                try {
                    val json = JSONObject(response)
                    val status = json.getString("status")

                    if (status == "success") {
                        getSharedPreferences("PREFS", MODE_PRIVATE).edit {
                            putBoolean("is_logged_in", true)
                            putString("user_id", json.getString("user_id"))
                            putString("nis", json.getString("nis"))
                            putString("namalengkap", json.getString("nama"))
                            putString("level", json.getString("level"))
                            putString("pass", pass)
                            putString("tingkat", json.optString("tingkat", ""))
                            putString("kelas", json.optString("kelas", ""))
                            putString("guruwali", json.optString("guruwali", ""))
                            putString("device_id", json.optString("device_id", ""))
                            putString("foto", json.optString("foto"))
                        }

                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, "Login gagal: ${error.message}", Toast.LENGTH_SHORT).show()
            }) {

            override fun getParams(): Map<String, String> {
                return mapOf(
                    "nis" to nis,
                    "pass" to pass,
                    "device_id" to deviceId
                )
            }
        }

        Volley.newRequestQueue(this).add(stringRequest)
    }

}