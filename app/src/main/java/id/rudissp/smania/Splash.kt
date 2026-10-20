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
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.colorResource
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import kotlin.jvm.java

class Splash : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                SplashScreen()
            }
        }

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
            @SuppressLint("HardwareIds")
            override fun getParams(): MutableMap<String, String> {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val params = HashMap<String, String>()
                params["nis"] = nis
                params["device_id"] = deviceId
                return params
            }
        }

        VolleySingleton.getInstance(this).addToRequestQueue(request)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showUpdateDialog(message: String, downloadUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("Update Tersedia")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Update Sekarang") { _, _ ->
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
                startActivity(Intent(this, MainExpressiveActivity::class.java))
                finish()
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }, 1000)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

@Composable
fun SplashScreen(
    downloading: Boolean = false,
    downloadText: String = "",
    progress: Int = 0
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop

                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Selamat datang di \nSMANIA",
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                if (downloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = downloadText.ifEmpty { "Pengunduhan aplikasi sedang berjalan...\nmohon bersabar, bisa ngopi dulu..." },
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text(
                text = "Developed by\nKurikulum SMAN 1 Sukorejo",
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colorResource(id = R.color.md_theme_primary),
            onPrimary = colorResource(id = R.color.md_theme_onPrimary),
            primaryContainer = colorResource(id = R.color.md_theme_primaryContainer),
            onPrimaryContainer = colorResource(id = R.color.md_theme_onPrimaryContainer),
            secondary = colorResource(id = R.color.md_theme_secondary),
            onSecondary = colorResource(id = R.color.md_theme_onSecondary),
            secondaryContainer = colorResource(id = R.color.md_theme_secondaryContainer),
            onSecondaryContainer = colorResource(id = R.color.md_theme_onSecondaryContainer),
            tertiary = colorResource(id = R.color.md_theme_tertiary),
            onTertiary = colorResource(id = R.color.md_theme_onTertiary),
            tertiaryContainer = colorResource(id = R.color.md_theme_tertiaryContainer),
            onTertiaryContainer = colorResource(id = R.color.md_theme_onTertiaryContainer),
            error = colorResource(id = R.color.md_theme_error),
            onError = colorResource(id = R.color.md_theme_onError),
            errorContainer = colorResource(id = R.color.md_theme_errorContainer),
            onErrorContainer = colorResource(id = R.color.md_theme_onErrorContainer),
            background = colorResource(id = R.color.md_theme_background),
            onBackground = colorResource(id = R.color.md_theme_onBackground),
            surface = colorResource(id = R.color.md_theme_surface),
            onSurface = colorResource(id = R.color.md_theme_onSurface),
            surfaceVariant = colorResource(id = R.color.md_theme_surfaceVariant),
            onSurfaceVariant = colorResource(id = R.color.md_theme_onSurfaceVariant),
            outline = colorResource(id = R.color.md_theme_outline),
            outlineVariant = colorResource(id = R.color.md_theme_outlineVariant),
            inverseSurface = colorResource(id = R.color.md_theme_inverseSurface),
            inverseOnSurface = colorResource(id = R.color.md_theme_inverseOnSurface),
            inversePrimary = colorResource(id = R.color.md_theme_inversePrimary)
        )
    } else {
        lightColorScheme(
            primary = colorResource(id = R.color.md_theme_primary),
            onPrimary = colorResource(id = R.color.md_theme_onPrimary),
            primaryContainer = colorResource(id = R.color.md_theme_primaryContainer),
            onPrimaryContainer = colorResource(id = R.color.md_theme_onPrimaryContainer),
            secondary = colorResource(id = R.color.md_theme_secondary),
            onSecondary = colorResource(id = R.color.md_theme_onSecondary),
            secondaryContainer = colorResource(id = R.color.md_theme_secondaryContainer),
            onSecondaryContainer = colorResource(id = R.color.md_theme_onSecondaryContainer),
            tertiary = colorResource(id = R.color.md_theme_tertiary),
            onTertiary = colorResource(id = R.color.md_theme_onTertiary),
            tertiaryContainer = colorResource(id = R.color.md_theme_tertiaryContainer),
            onTertiaryContainer = colorResource(id = R.color.md_theme_onTertiaryContainer),
            error = colorResource(id = R.color.md_theme_error),
            onError = colorResource(id = R.color.md_theme_onError),
            errorContainer = colorResource(id = R.color.md_theme_errorContainer),
            onErrorContainer = colorResource(id = R.color.md_theme_onErrorContainer),
            background = colorResource(id = R.color.md_theme_background),
            onBackground = colorResource(id = R.color.md_theme_onBackground),
            surface = colorResource(id = R.color.md_theme_surface),
            onSurface = colorResource(id = R.color.md_theme_onSurface),
            surfaceVariant = colorResource(id = R.color.md_theme_surfaceVariant),
            onSurfaceVariant = colorResource(id = R.color.md_theme_onSurfaceVariant),
            outline = colorResource(id = R.color.md_theme_outline),
            outlineVariant = colorResource(id = R.color.md_theme_outlineVariant),
            inverseSurface = colorResource(id = R.color.md_theme_inverseSurface),
            inverseOnSurface = colorResource(id = R.color.md_theme_inverseOnSurface),
            inversePrimary = colorResource(id = R.color.md_theme_inversePrimary)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    AppTheme {
        SplashScreen()
    }
}
