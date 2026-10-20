package id.rudissp.smania.presensi

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentContainerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import id.rudissp.smania.VolleySingleton
import id.rudissp.smania.R
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class Presensi : AppCompatActivity() {

    lateinit var prefs: SharedPreferences
    lateinit var prefs2: SharedPreferences

    private var targetLatitude: Double = 0.0
    private var targetLongitude: Double = 0.0
    private var radius: Double = 0.0

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapController: IMapController
    private var userLocation: Location? = null
    private var userMarker: Marker? = null
    private lateinit var mapView: MapView

    private var presensiRadiusPolygon: Polygon? = null
    private var isShiftMalam: Boolean = false

    // Reactive Compose States
    private var titleName by mutableStateOf("Presensi")
    private var distanceText by mutableStateOf("Jarak dari lokasi presensi: -")
    private var statusPresensiText by mutableStateOf("")
    private var showStatusPresensiText by mutableStateOf(false)
    private var showBtnPresensiMasuk by mutableStateOf(false)
    private var showBtnPresensiPulang by mutableStateOf(false)
    private var showBtnKonfirmasiMasuk by mutableStateOf(false)
    private var showBtnKonfirmasiPulang by mutableStateOf(false)

    private var selectedTab by mutableIntStateOf(0)
    private var isPetugasIzinState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        prefs2 = this.getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs2)

        val namalengkap = prefs.getString("namalengkap", "") ?: ""
        titleName = if (namalengkap.isNotEmpty()) "Presensi $namalengkap" else "Presensi"
        isPetugasIzinState = prefs.getBoolean("is_petugas_izin", false)

        cekPetugasIzin(prefs.getString("nis", "") ?: "")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
        mapController = mapView.controller
        mapController.setZoom(20.0)

        setContent {
            AppTheme {
                PresensiScreen(
                    title = titleName,
                    selectedTab = selectedTab,
                    isPetugasIzin = isPetugasIzinState,
                    distanceText = distanceText,
                    statusPresensiText = statusPresensiText,
                    showStatusPresensiText = showStatusPresensiText,
                    showBtnPresensiMasuk = showBtnPresensiMasuk,
                    showBtnPresensiPulang = showBtnPresensiPulang,
                    showBtnKonfirmasiMasuk = showBtnKonfirmasiMasuk,
                    showBtnKonfirmasiPulang = showBtnKonfirmasiPulang,
                    sharedPref = prefs,
                    onTabSelected = { tabIndex ->
                        if (tabIndex == 2 && !isPetugasIzinState) {
                            Toast.makeText(this, "Anda bukan petugas izin", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedTab = tabIndex
                        }
                    },
                    onPresensiMasukClick = { presensi("masuk") },
                    onPresensiPulangClick = { presensi("pulang") },
                    onKonfirmasiMasukClick = { dialogkonfirmasimasuk() },
                    onKonfirmasiPulangClick = { dialogkonfirmasipulang() },
                    onRefreshClick = { getUserLocation() },
                    onBack = { finish() },
                    mapViewInstance = mapView,
                    fragmentContainerProvider = {
                        AndroidView(
                            factory = { context ->
                                FragmentContainerView(context).apply {
                                    id = View.generateViewId()
                                    supportFragmentManager.beginTransaction()
                                        .replace(id, IzinFragment())
                                        .commit()
                                }
                            },
                            update = { containerView ->
                                supportFragmentManager.beginTransaction()
                                    .replace(containerView.id, IzinFragment())
                                    .commit()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        }

        ambilLokasiPresensiDariServer()
    }

    private fun setupTargetMarker() {
        val geoPoint = GeoPoint(targetLatitude, targetLongitude)
        drawCircle(mapView, geoPoint, radius)
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
            )
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(true)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && location.isMock) || location.isFromMockProvider) {
                    Toast.makeText(this@Presensi, "Terdeteksi pake fake GPS!!!", Toast.LENGTH_LONG).show()
                    return
                }

                userLocation = location
                updateMap(location)
                checkDistanceAndUpdateUI()
                fusedLocationClient.removeLocationUpdates(this)
                val nis = prefs.getString("nis", "") ?: ""
                cekStatusPresensiDariServer(nis)
            }
        }, this.mainLooper)
    }

    private fun updateMap(location: Location) {
        val userLat = location.latitude
        val userLng = location.longitude

        userMarker?.let { mapView.overlays.remove(it) }
        userMarker = Marker(mapView).apply {
            position = GeoPoint(userLat, userLng)
            title = "Lokasi Anda"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        mapView.overlays.add(userMarker)
        mapController.setCenter(GeoPoint(userLat, userLng))
        mapView.invalidate()
    }

    private fun checkDistanceAndUpdateUI() {
        val location = userLocation
        if (location == null) {
            Toast.makeText(this, "Lokasi pengguna belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        val distance = getDistance(location.latitude, location.longitude, targetLatitude, targetLongitude)
        distanceText = "Jarak dari lokasi presensi: ${"%.2f".format(distance)} meter"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (distance < radius) {
            if (isShiftMalam) {
                showBtnPresensiMasuk = (hour in 18..21)
                showBtnPresensiPulang = (hour in 5..7)
            } else {
                showBtnPresensiMasuk = (hour in 5..11)
                showBtnPresensiPulang = (hour in 12..22)
            }
        } else {
            showBtnPresensiMasuk = false
            showBtnPresensiPulang = false
            Toast.makeText(this, "Anda terlalu jauh dari lokasi presensi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val loc1 = Location("").apply { latitude = lat1; longitude = lon1 }
        val loc2 = Location("").apply { latitude = lat2; longitude = lon2 }
        return loc1.distanceTo(loc2).toDouble()
    }

    private fun cekStatusPresensiDariServer(nis: String) {
        val tanggal = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val stringRequest = object : StringRequest(
            Method.POST, AppConstants.CEK_PRESENSI,
            { response ->
                try {
                    val json = JSONObject(response)
                    val sudahMasuk = json.getBoolean("masuk")
                    val sudahPulang = json.getBoolean("pulang")

                    if (sudahMasuk && sudahPulang) {
                        statusPresensiText = "Anda sudah presensi masuk dan pulang hari ini."
                        showBtnKonfirmasiMasuk = true
                        showBtnKonfirmasiPulang = true
                        showBtnPresensiMasuk = false
                        showBtnPresensiPulang = false
                    } else if (sudahMasuk) {
                        statusPresensiText = "Anda sudah presensi masuk hari ini."
                        showBtnKonfirmasiMasuk = true
                        showBtnPresensiMasuk = false
                    } else if (sudahPulang) {
                        statusPresensiText = "Anda sudah presensi pulang hari ini."
                        showBtnKonfirmasiPulang = true
                        showBtnPresensiPulang = false
                    }

                    if (sudahMasuk || sudahPulang) {
                        showStatusPresensiText = true
                    }

                } catch (_: JSONException) {
                }
            },
            { error ->
                Log.e("CekStatus", "Gagal koneksi: ${error.message}")
            }
        ) {
            override fun getParams(): Map<String, String> {
                return mapOf("nis" to nis, "tanggal" to tanggal)
            }
        }

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
    }

    private fun presensi(jenis: String) {
        val latitude = userLocation?.latitude ?: 0.0
        val longitude = userLocation?.longitude ?: 0.0
        val nis = prefs.getString("nis", "") ?: ""
        val shift = if (isShiftMalam) "malam" else "pagi"

        prefs.edit {
            putBoolean(
                if (jenis == "masuk") "sudah_presensi_masuk" else "sudah_presensi_pulang",
                true
            )
        }

        kirimPresensiKeServer(nis, jenis, latitude, longitude, shift)
    }

    private fun kirimPresensiKeServer(
        nis: String,
        jenis: String,
        latitude: Double,
        longitude: Double,
        shift: String
    ) {
        val queue = VolleySingleton.getInstance(this).requestQueue

        val sdfTanggal = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        val sdfJam = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTanggal = sdfTanggal.format(Date())
        val currentJam = sdfJam.format(Date())

        val tanggalParam = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val jamParam = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

        val stringRequest = object : StringRequest(
            Method.POST, AppConstants.PRESENSI_URL,
            { response ->
                try {
                    val json = JSONObject(response)
                    val status = json.getString("status")

                    if (status == "sukses") {
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Presensi Berhasil")
                            .setMessage("Anda berhasil melakukan presensi pada $currentTanggal pukul $currentJam")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        Toast.makeText(this, json.getString("pesan"), Toast.LENGTH_SHORT).show()
                    }
                } catch (_: JSONException) {
                    Toast.makeText(this, "Berhasil simpan, tapi respon error. Cek Database.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            { error ->
                Log.e("Presensi", "Error: ${error.message}", error)
                Toast.makeText(this, "Gagal koneksi: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): Map<String, String> {
                return mapOf(
                    "nis" to nis,
                    "jenis" to jenis,
                    "latitude" to latitude.toString(),
                    "longitude" to longitude.toString(),
                    "tanggal" to tanggalParam,
                    "jam" to jamParam,
                    "shift" to shift
                )
            }
        }

        stringRequest.retryPolicy = DefaultRetryPolicy(
            10_000,
            DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(stringRequest)
    }

    private fun drawCircle(mapView: MapView, center: GeoPoint, radius: Double) {
        val circlePoints = mutableListOf<GeoPoint>()
        val numPoints = 360
        for (i in 0 until numPoints) {
            val angle = i * (PI / 180)
            val latOffset = radius * sin(angle) / 100000
            val lonOffset = radius * cos(angle) / 100000

            val newPoint = GeoPoint(center.latitude + latOffset, center.longitude + lonOffset)
            circlePoints.add(newPoint)
        }

        presensiRadiusPolygon?.let { mapView.overlays.remove(it) }

        presensiRadiusPolygon = Polygon(mapView).apply {
            points = circlePoints
            fillColor = 0x220000FF
            strokeColor = -0xffff01 // Solid blue border
        }

        mapView.overlays.add(presensiRadiusPolygon)
    }

    private fun dialogkonfirmasimasuk() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Konfirmasi Presensi")
            .setMessage("Anda sudah melakukan presensi masuk hari ini. Apakah Anda ingin melakukan presensi lagi?")
            .setPositiveButton("Ya") { _, _ -> presensi("masuk") }
            .show()
    }

    private fun dialogkonfirmasipulang() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Konfirmasi Presensi")
            .setMessage("Anda sudah melakukan presensi pulang hari ini. Apakah Anda ingin melakukan presensi lagi?")
            .setPositiveButton("Ya") { _, _ -> presensi("pulang") }
            .show()
    }

    private fun ambilLokasiPresensiDariServer() {
        val stringRequest = StringRequest(
            Request.Method.GET, AppConstants.PRESENSI_PRENGATURAN,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val status = jsonObject.getString("status")

                    if (status == "sukses") {
                        targetLatitude = jsonObject.getString("latitude").toDoubleOrNull() ?: 0.0
                        targetLongitude = jsonObject.getString("longitude").toDoubleOrNull() ?: 0.0
                        radius = jsonObject.getString("radius").toDoubleOrNull() ?: 0.0

                        setupTargetMarker()
                        getUserLocation()
                        val nis = prefs.getString("nis", "") ?: ""
                        cekStatusPresensiDariServer(nis)
                    } else {
                        Toast.makeText(this, "Data pengaturan presensi belum diatur di server.", Toast.LENGTH_LONG).show()
                    }

                } catch (e: JSONException) {
                    Log.e("AmbilLokasi", "Error parsing JSON: ${e.message}")
                    Toast.makeText(this, "Format data server tidak sesuai.", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("AmbilLokasi", "Network error: ${error.message}")
                Toast.makeText(this, "Gagal terhubung ke server. Periksa koneksi internet.", Toast.LENGTH_SHORT).show()
            }
        )

        VolleySingleton.getInstance(this).addToRequestQueue(stringRequest)
    }

    private fun cekPetugasIzin(nis: String) {
        val url = AppConstants.BASE_URL + "presensi_cek_petugas_izin.php?nis=$nis"
        val queue = VolleySingleton.getInstance(this).requestQueue
        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "sukses") {
                        val isPetugas = json.getBoolean("is_petugas")
                        isPetugasIzinState = isPetugas
                        if (isPetugas) {
                            val kelas = json.getString("kelas")
                            prefs.edit {
                                putString("kelas_petugas", kelas)
                                putBoolean("is_petugas_izin", true)
                            }
                            Toast.makeText(this, "Anda adalah petugas izin kelas $kelas", Toast.LENGTH_SHORT).show()
                        } else {
                            prefs.edit {
                                putBoolean("is_petugas_izin", false)
                                remove("kelas_petugas")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CekPetugas", "Error: ${e.message}")
                }
            },
            { error ->
                Log.e("CekPetugas", "Gagal koneksi: ${error.message}")
            }
        )
        queue.add(request)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresensiScreen(
    title: String,
    selectedTab: Int,
    isPetugasIzin: Boolean,
    distanceText: String,
    statusPresensiText: String,
    showStatusPresensiText: Boolean,
    showBtnPresensiMasuk: Boolean,
    showBtnPresensiPulang: Boolean,
    showBtnKonfirmasiMasuk: Boolean,
    showBtnKonfirmasiPulang: Boolean,
    sharedPref: SharedPreferences? = null,
    onTabSelected: (Int) -> Unit,
    onPresensiMasukClick: () -> Unit,
    onPresensiPulangClick: () -> Unit,
    onKonfirmasiMasukClick: () -> Unit,
    onKonfirmasiPulangClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onBack: () -> Unit,
    mapViewInstance: MapView? = null,
    fragmentContainerProvider: @Composable (Int) -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = (selectedTab == 0),
                    onClick = { onTabSelected(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "Presensi",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Presensi", fontWeight = FontWeight.SemiBold) }
                )
                NavigationBarItem(
                    selected = (selectedTab == 1),
                    onClick = { onTabSelected(1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.ListAlt,
                            contentDescription = "Rekap",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Rekap", fontWeight = FontWeight.SemiBold) }
                )
                if (isPetugasIzin) {
                    NavigationBarItem(
                        selected = (selectedTab == 2),
                        onClick = { onTabSelected(2) },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = "Izin"
                            )
                        },
                        label = { Text("Izin", fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    // Tab Presensi (Map + Expressive Floating Panel)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Expressive Map Container Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(28.dp))
                            ) {
                                if (mapViewInstance != null) {
                                    AndroidView(
                                        factory = { mapViewInstance },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Map View Preview",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Presensi Expressive Control Panel
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Lokasi & Jarak Information Badge
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MyLocation,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = distanceText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                if (showStatusPresensiText && statusPresensiText.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = statusPresensiText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                // Interactive Primary Action Buttons
                                if (showBtnPresensiMasuk) {
                                    Button(
                                        onClick = onPresensiMasukClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text("Presensi Masuk", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (showBtnPresensiPulang) {
                                    Button(
                                        onClick = onPresensiPulangClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text("Presensi Pulang", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (showBtnKonfirmasiMasuk) {
                                    Button(
                                        onClick = onKonfirmasiMasukClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    ) {
                                        Text("Ulangi Presensi Masuk", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (showBtnKonfirmasiPulang) {
                                    Button(
                                        onClick = onKonfirmasiPulangClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    ) {
                                        Text("Ulangi Presensi Pulang", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Refresh Button
                                OutlinedButton(
                                    onClick = onRefreshClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text("Refresh Lokasi", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // Tab Rekap
                    RekapPresensiScreen(sharedPref = sharedPref)
                }

                2 -> {
                    // Tab Izin
                    fragmentContainerProvider(2)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PresensiScreenPreview() {
    AppTheme {
        PresensiScreen(
            title = "Presensi Rudi SSP",
            selectedTab = 0,
            isPetugasIzin = true,
            distanceText = "Jarak dari lokasi presensi: 12.50 meter",
            statusPresensiText = "Anda sudah presensi masuk hari ini.",
            showStatusPresensiText = true,
            showBtnPresensiMasuk = false,
            showBtnPresensiPulang = false,
            showBtnKonfirmasiMasuk = true,
            showBtnKonfirmasiPulang = false,
            onTabSelected = {},
            onPresensiMasukClick = {},
            onPresensiPulangClick = {},
            onKonfirmasiMasukClick = {},
            onKonfirmasiPulangClick = {},
            onRefreshClick = {},
            onBack = {}
        )
    }
}