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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
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
import id.rudissp.smania.R
import id.rudissp.smania.databinding.ActivityPresensiBinding
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

    lateinit var binding: ActivityPresensiBinding
    lateinit var prefs: SharedPreferences
    lateinit var prefs2: SharedPreferences

    //ini lokasi skj
    //private val targetLatitude = -7.09058002110094
    //private val targetLongitude = 110.04542030195047
    private var targetLatitude: Double = 0.0
    private var targetLongitude: Double = 0.0
    private var radius: Double = 0.0
    //lokasi rumahku
    //private val targetLatitude = -6.930033714644384
    //private val targetLongitude = 110.20138506062047

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapController: IMapController
    private var userLocation: Location? = null
    private var userMarker: Marker? = null
    private lateinit var mapView: MapView

    private var presensiRadiusPolygon: Polygon? = null
    private var isShiftMalam: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPresensiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("PREFS", MODE_PRIVATE)
        prefs2 = this.getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs2)

        cekPetugasIzin(prefs.getString("nis", "") ?: "")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController = mapView.controller
        mapController.setZoom(20.0)

        val namalengkap = prefs.getString("namalengkap", "")
        binding.topAppBar.title = "Presensi $namalengkap"
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }

        binding.fragmentContainer.visibility = View.GONE

        var isInitialSetup = true

        binding.shiftToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isInitialSetup) return@addOnButtonCheckedListener

            if (isChecked) {
                when (checkedId) {
                    R.id.btnShiftPagi -> {
                        isShiftMalam = false
                        Toast.makeText(this, "Shift Pagi Aktif", Toast.LENGTH_SHORT).show()
                        checkDistanceAndUpdateUI()
                    }

                    R.id.btnShiftMalam -> {
                        isShiftMalam = true
                        Toast.makeText(this, "Shift Malam Aktif", Toast.LENGTH_SHORT).show()
                        checkDistanceAndUpdateUI()
                    }
                }
            }
        }
        binding.shiftToggleGroup.post {
            binding.shiftToggleGroup.check(R.id.btnShiftPagi) // <- Set tombol Shift Pagi sebagai default
            isInitialSetup = false
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.presensi -> {
                    binding.fragmentContainer.removeAllViews()
                    binding.fragmentContainer.visibility = View.GONE
                }

                R.id.rekap -> {
                    binding.fragmentContainer.visibility = View.VISIBLE
                    loadFragment(RekapPresensiFragment())
                }

                R.id.izin -> {
                    if (!prefs.getBoolean("is_petugas_izin", false)) {
                        Toast.makeText(this, "Anda bukan petugas izin", Toast.LENGTH_SHORT).show()
                        return@setOnItemSelectedListener false
                    } else {
                        binding.fragmentContainer.visibility = View.VISIBLE
                        loadFragment(IzinFragment())
                    }
                }


            }
            true
        }

//        setupTargetMarker()
//        getUserLocation()
//        val nis = prefs.getString("nis", "") ?: ""
//        cekStatusPresensiDariServer(nis)

        ambilLokasiPresensiDariServer()

        binding.btnPresensi.setOnClickListener { presensi("masuk") }
        binding.btnPresensiPulang.setOnClickListener { presensi("pulang") }
        binding.btnRefresh.setOnClickListener { getUserLocation() }
        binding.btnKonfirmasiPresensiMasuk.setOnClickListener { dialogkonfirmasimasuk() }
        binding.btnKonfirmasiPresensiPulang.setOnClickListener { dialogkonfirmasipulang() }

    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun setupTargetMarker() {
        val geoPoint = GeoPoint(targetLatitude, targetLongitude)
        drawCircle(mapView, geoPoint, radius)
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
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
                    Toast.makeText(this@Presensi, "Terdeteksi pake fake GPS!!!", Toast.LENGTH_LONG)
                        .show()
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
            //icon = ContextCompat.getDrawable(this@PresensiActivity, R.drawable.ic_rekap)
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

        val distance =
            getDistance(location.latitude, location.longitude, targetLatitude, targetLongitude)
        binding.tvDistance.text = "Jarak dari lokasi presensi: ${"%.2f".format(distance)} meter"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (distance < radius) {
            if (isShiftMalam) {
                // Shift malam: masuk 18–21, pulang 05–07
                binding.btnPresensi.visibility = if (hour in 18..21) View.VISIBLE else View.GONE
                binding.btnPresensiPulang.visibility = if (hour in 5..7) View.VISIBLE else View.GONE
            } else {
                // Shift pagi: masuk 06–10, pulang 11–22
                binding.btnPresensi.visibility = if (hour in 5..11) View.VISIBLE else View.GONE
                binding.btnPresensiPulang.visibility =
                    if (hour in 12..22) View.VISIBLE else View.GONE
            }
        } else {
            binding.btnPresensi.visibility = View.GONE
            binding.btnPresensiPulang.visibility = View.GONE
            Toast.makeText(this, "Anda terlalu jauh dari lokasi presensi", Toast.LENGTH_SHORT)
                .show()
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
                        binding.tvstatuspresensi.text =
                            "Anda sudah presensi masuk dan pulang hari ini."
                        binding.btnKonfirmasiPresensiMasuk.visibility = View.VISIBLE
                        binding.btnKonfirmasiPresensiPulang.visibility = View.VISIBLE
                        binding.btnPresensi.visibility = View.GONE
                        binding.btnPresensiPulang.visibility = View.GONE
                    } else if (sudahMasuk) {
                        binding.tvstatuspresensi.text = "Anda sudah presensi masuk hari ini."
                        binding.btnKonfirmasiPresensiMasuk.visibility = View.VISIBLE
                        binding.btnPresensi.visibility = View.GONE
                    } else if (sudahPulang) {
                        binding.tvstatuspresensi.text = "Anda sudah presensi pulang hari ini."
                        binding.btnKonfirmasiPresensiPulang.visibility = View.VISIBLE
                        binding.btnPresensiPulang.visibility = View.GONE
                    }

                    if (sudahMasuk || sudahPulang) {
                        binding.tvstatuspresensi.visibility = View.VISIBLE
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

        Volley.newRequestQueue(this).add(stringRequest)
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
        val queue = Volley.newRequestQueue(this)

        // Mengambil waktu saat ini untuk ditampilkan di dialog
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
                        // Membuat Dialog Sukses
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Presensi Berhasil")
                            .setMessage("Anda berhasil melakukan presensi pada $currentTanggal pukul $currentJam")
                            .setPositiveButton("OK") { _, _ ->
                                finish() // Menutup PresensiActivity
                            }
                            .setCancelable(false) // User harus menekan OK
                            .show()
                    } else {
                        Toast.makeText(this, json.getString("pesan"), Toast.LENGTH_SHORT).show()
                    }
                } catch (_: JSONException) {
                    // Jika index unik di DB belum dihapus, biasanya lari ke sini karena respon bukan JSON
                    Toast.makeText(
                        this,
                        "Berhasil simpan, tapi respon error. Cek Database.",
                        Toast.LENGTH_SHORT
                    ).show()
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
            val angle = i * (PI / 180) // Convert degree to radians
            val latOffset = radius * sin(angle) / 100000 // Adjust the scaling
            val lonOffset = radius * cos(angle) / 100000 // Adjust the scaling

            val newPoint = GeoPoint(center.latitude + latOffset, center.longitude + lonOffset)
            circlePoints.add(newPoint)
        }

        // Menghapus radius lama jika ada
        presensiRadiusPolygon?.let {
            mapView.overlays.remove(it)
        }

        // Membuat polygon baru untuk radius
        presensiRadiusPolygon = Polygon(mapView)
        presensiRadiusPolygon?.points = circlePoints
        presensiRadiusPolygon?.fillColor = 0x220000FF // Semi-transparent blue
        presensiRadiusPolygon?.strokeColor = 0xFF000FF // Solid blue border

        // Menambahkan polygon baru ke peta
        mapView.overlays.add(presensiRadiusPolygon)
    }

    private fun dialogkonfirmasimasuk() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Konfirmasi Presensi")
            .setMessage(
                "Anda sudah melakukan presensi masuk hari ini." +
                        " Apakah Anda ingin melakukan presensi lagi?"
            )
            .setPositiveButton("Ya") { _, _ ->
                presensi("masuk")
            }
            .show()

    }

    private fun dialogkonfirmasipulang() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Konfirmasi Presensi")
            .setMessage(
                "Anda sudah melakukan presensi pulang hari ini. " +
                        "Apakah Anda ingin melakukan presensi lagi?"
            )
            .setPositiveButton("Ya") { _, _ ->
                presensi("pulang")
            }
            .show()

    }

    private fun ambilLokasiPresensiDariServer() {
        val stringRequest = StringRequest(
            com.android.volley.Request.Method.GET, AppConstants.PRESENSI_PRENGATURAN,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val status = jsonObject.getString("status")

                    if (status == "sukses") {
                        // Jika sukses, ambil data dan konversi ke Double
                        targetLatitude = jsonObject.getString("latitude").toDoubleOrNull() ?: 0.0
                        targetLongitude = jsonObject.getString("longitude").toDoubleOrNull() ?: 0.0
                        radius = jsonObject.getString("radius").toDoubleOrNull() ?: 0.0

                        // Lanjutkan proses memunculkan marker dan cek lokasi
                        setupTargetMarker()
                        getUserLocation()
                        val nis = prefs.getString("nis", "") ?: ""
                        cekStatusPresensiDariServer(nis)
                    } else {
                        // Jika status gagal dari PHP
                        Toast.makeText(
                            this,
                            "Data pengaturan presensi belum diatur di server.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                } catch (e: JSONException) {
                    Log.e("AmbilLokasi", "Error parsing JSON: ${e.message}")
                    Toast.makeText(this, "Format data server tidak sesuai.", Toast.LENGTH_SHORT)
                        .show()
                }
            },
            { error ->
                Log.e("AmbilLokasi", "Network error: ${error.message}")
                Toast.makeText(
                    this,
                    "Gagal terhubung ke server. Periksa koneksi internet.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        Volley.newRequestQueue(this).add(stringRequest)
    }

    fun cekPetugasIzin(nis: String) {
        val url = AppConstants.BASE_URL + "presensi_cek_petugas_izin.php?nis=$nis"
        val queue = Volley.newRequestQueue(this)
        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.getString("status") == "sukses") {
                        val isPetugas = json.getBoolean("is_petugas")
                        if (isPetugas) {
                            val kelas = json.getString("kelas")
                            // Simpan ke SharedPreferences
                            prefs.edit {
                                putString("kelas_petugas", kelas)
                                putBoolean("is_petugas_izin", true)
                            }
                            Toast.makeText(
                                this,
                                "Anda adalah petugas izin kelas $kelas",
                                Toast.LENGTH_SHORT
                            ).show()
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