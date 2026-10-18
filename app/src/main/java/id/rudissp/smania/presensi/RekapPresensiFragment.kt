package id.rudissp.smania.presensi

import id.rudissp.smania.LoadingIndicator
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppConstants
import id.rudissp.smania.AppTheme
import org.json.JSONArray
import org.json.JSONException
import java.util.Calendar

data class RekapPresensia(
    val tanggal: String,
    val masuk: String,
    val pulang: String,
    val jenis: String,
    val shift: String
)

class RekapPresensiFragment : Fragment() {

    private lateinit var sharedPref: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        sharedPref = context.getSharedPreferences("PREFS", Context.MODE_PRIVATE)

        return ComposeView(context).apply {
            setContent {
                AppTheme {
                    RekapPresensiScreen(sharedPref = sharedPref)
                }
            }
        }
    }
}

@Composable
fun RekapPresensiScreen(sharedPref: SharedPreferences? = null) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val bulanMap = remember {
        mapOf(
            "Januari" to 1, "Februari" to 2, "Maret" to 3, "April" to 4,
            "Mei" to 5, "Juni" to 6, "Juli" to 7, "Agustus" to 8,
            "September" to 9, "Oktober" to 10, "November" to 11, "Desember" to 12
        )
    }

    val bulanList = remember { bulanMap.keys.toList() }

    val calendar = remember { Calendar.getInstance() }
    val bulanSekarangIndex = remember { calendar.get(Calendar.MONTH) }
    val tahunSekarang = remember { calendar.get(Calendar.YEAR).toString() }

    var selectedBulan by remember { mutableStateOf(bulanList.getOrElse(bulanSekarangIndex) { "Januari" }) }
    var selectedTahun by remember { mutableStateOf(tahunSekarang) }
    var daftarRekap by remember { mutableStateOf<List<RekapPresensia>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun ambilRekap() {
        val bulanAngka = bulanMap[selectedBulan]
        if (bulanAngka == null) {
            Toast.makeText(context, "Bulan tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedBulan.isEmpty() || selectedTahun.isEmpty()) {
            Toast.makeText(context, "Bulan dan Tahun wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        val queue = Volley.newRequestQueue(context)
        val request = object : StringRequest(
            Method.POST, AppConstants.REKAP_PRESENSI,
            { response ->
                isLoading = false
                try {
                    val jsonArray = JSONArray(response)
                    val newList = mutableListOf<RekapPresensia>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        newList.add(
                            RekapPresensia(
                                tanggal = obj.optString("tanggal", ""),
                                masuk = obj.optString("masuk", ""),
                                pulang = obj.optString("pulang", ""),
                                jenis = obj.optString("status", ""),
                                shift = obj.optString("shift", "")
                            )
                        )
                    }
                    daftarRekap = newList
                } catch (e: JSONException) {
                    Toast.makeText(context, "Data tidak valid", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                isLoading = false
                Toast.makeText(context, "Gagal koneksi: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): Map<String, String> {
                val nis = sharedPref?.getString("nis", "") ?: ""
                return mapOf(
                    "nis" to nis,
                    "bulan" to bulanAngka.toString(),
                    "tahun" to selectedTahun
                )
            }
        }

        queue.add(request)
    }

    LaunchedEffect(Unit) {
        if (sharedPref != null) {
            ambilRekap()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // PERBAIKAN: Menggunakan spacedBy(3.dp) agar antarkartu dalam grouped list menyatu rapat
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Filter Expressive Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Filter Periode Presensi",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Dropdown Bulan
                        BulanDropdownField(
                            label = "Bulan",
                            options = bulanList,
                            selectedOption = selectedBulan,
                            onOptionSelected = {
                                selectedBulan = it
                                if (sharedPref != null) ambilRekap()
                            }
                        )

                        // Input Tahun
                        OutlinedTextField(
                            value = selectedTahun,
                            onValueChange = { selectedTahun = it },
                            label = { Text("Tahun") },
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    if (sharedPref != null) ambilRekap()
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // Spasi khusus antara Card Filter dan Judul Section (20.dp)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Section Label
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Riwayat Kehadiran",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${daftarRekap.size} Hari",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Spasi khusus antara Judul Section dan Item Pertama (10.dp)
                Spacer(modifier = Modifier.height(10.dp))
            }

            // List Riwayat dalam Format Grouped List Rapat
            if (daftarRekap.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada data presensi pada periode ini",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val listSize = daftarRekap.size
                itemsIndexed(daftarRekap) { index, item ->
                    val itemShape = when {
                        listSize == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(
                            topStart = 24.dp, topEnd = 24.dp,
                            bottomStart = 4.dp, bottomEnd = 4.dp
                        )
                        index == listSize - 1 -> RoundedCornerShape(
                            topStart = 4.dp, topEnd = 4.dp,
                            bottomStart = 24.dp, bottomEnd = 24.dp
                        )
                        else -> RoundedCornerShape(4.dp)
                    }

                    RekapPresensiCard(item = item, shape = itemShape)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulanDropdownField(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RekapPresensiCard(
    item: RekapPresensia,
    shape: androidx.compose.ui.graphics.Shape
) {
    val isPiket = item.shift == "malam"
    val masukKosong = item.masuk.isEmpty() || item.masuk == "-"
    val pulangKosong = item.pulang.isEmpty() || item.pulang == "-"

    val keteranganText = when {
        isPiket -> "Piket"
        item.jenis.isNotEmpty() && !item.jenis.equals("Hadir", ignoreCase = true) -> item.jenis
        masukKosong && pulangKosong -> "Tidak Presensi"
        masukKosong || pulangKosong -> "Tidak Lengkap"
        else -> "Hadir"
    }

    val badgeBg = when {
        isPiket -> MaterialTheme.colorScheme.tertiaryContainer
        keteranganText == "Tidak Presensi" || keteranganText == "Tidak Lengkap" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val badgeTextColor = when {
        isPiket -> MaterialTheme.colorScheme.onTertiaryContainer
        keteranganText == "Tidak Presensi" || keteranganText == "Tidak Lengkap" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.tanggal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Masuk: ${if (!masukKosong) item.masuk else "-"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pulang: ${if (!pulangKosong) item.pulang else "-"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = badgeBg
            ) {
                Text(
                    text = keteranganText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RekapPresensiScreenPreview() {
    AppTheme {
        RekapPresensiScreen(sharedPref = null)
    }
}