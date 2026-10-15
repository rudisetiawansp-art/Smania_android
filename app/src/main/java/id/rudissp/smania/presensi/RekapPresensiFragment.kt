package id.rudissp.smania.presensi

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
import androidx.compose.foundation.lazy.items
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
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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

            Spacer(modifier = Modifier.height(8.dp))

            // Input Tahun
            OutlinedTextField(
                value = selectedTahun,
                onValueChange = { selectedTahun = it },
                label = { Text("Tahun") },
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tanggal",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Masuk",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Pulang",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Ket.",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            // List Riwayat
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(daftarRekap) { item ->
                    RekapPresensiRow(item = item)
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
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
private fun RekapPresensiRow(item: RekapPresensia) {
    val isPiket = item.shift == "malam"
    val textColor = if (isPiket) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val keteranganText = if (isPiket) "Piket" else item.jenis

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.tanggal,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = item.masuk,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = item.pulang,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = keteranganText,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RekapPresensiScreenPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            val sampleItems = listOf(
                RekapPresensia("2026-05-01", "07:00", "15:30", "Hadir", "pagi"),
                RekapPresensia("2026-05-02", "18:00", "06:00", "Piket", "malam"),
                RekapPresensia("2026-05-03", "07:15", "15:30", "Terlambat", "pagi")
            )
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tanggal", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("Masuk", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("Pulang", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("Ket.", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
            sampleItems.forEach { item ->
                RekapPresensiRow(item = item)
            }
        }
    }
}
