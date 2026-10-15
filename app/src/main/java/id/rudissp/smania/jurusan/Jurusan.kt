package id.rudissp.smania.jurusan

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import id.rudissp.smania.AppTheme
import id.rudissp.smania.R

data class JurusanItem(
    val kelompokProgramStudi: String,
    val rumpunIlmu: String,
    val kurikulumMerdeka: String
)

class Jurusan : AppCompatActivity() {

    private var originalList by mutableStateOf<List<JurusanItem>>(emptyList())
    private var searchQuery by mutableStateOf("")
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                val filteredList = remember(originalList, searchQuery) {
                    if (searchQuery.isBlank()) {
                        originalList
                    } else {
                        val pattern = searchQuery.trim().lowercase()
                        originalList.filter { item ->
                            item.kurikulumMerdeka.lowercase().contains(pattern) ||
                                    item.kelompokProgramStudi.lowercase().contains(pattern)
                        }
                    }
                }

                JurusanScreen(
                    jurusanList = filteredList,
                    searchQuery = searchQuery,
                    isLoading = isLoading,
                    onSearchQueryChange = { searchQuery = it },
                    onBack = { finish() }
                )
            }
        }

        fetchDataFromPhp()
    }

    private fun fetchDataFromPhp() {
        isLoading = true
        val url = "https://sman1sukorejo.sch.id/esmansa/api_jurusan_all.php"
        val queue = Volley.newRequestQueue(this)

        val jsonArrayRequest = JsonArrayRequest(
            Request.Method.GET, url, null,
            { response ->
                isLoading = false
                val newList = mutableListOf<JurusanItem>()
                try {
                    for (i in 0 until response.length()) {
                        val item = response.getJSONObject(i)
                        newList.add(
                            JurusanItem(
                                kelompokProgramStudi = item.getString("kelompok_program_studi"),
                                rumpunIlmu = item.getString("rumpun_ilmu"),
                                kurikulumMerdeka = item.getString("kurikulum_merdeka")
                            )
                        )
                    }
                    originalList = newList
                } catch (e: Exception) {
                    Log.e("JSON_ERROR", "Error parsing data: ${e.message}")
                }
            },
            { error ->
                isLoading = false
                Toast.makeText(this, "Gagal mengambil data: ${error.message}", Toast.LENGTH_SHORT).show()
                Log.e("VOLLEY_ERROR", error.toString())
            }
        )

        queue.add(jsonArrayRequest)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JurusanScreen(
    jurusanList: List<JurusanItem>,
    searchQuery: String,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Cari jurusan atau mapel...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Text("Jurusan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Kepmen 102/M/2025", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            onSearchQueryChange("")
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Search"
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(jurusanList) { item ->
                    JurusanCardItem(item = item)
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
}

@Composable
private fun JurusanCardItem(item: JurusanItem) {
    val isMerdeka = item.kurikulumMerdeka.equals("Ya", ignoreCase = true)
    val chipBg = if (isMerdeka) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val chipText = if (isMerdeka) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icm_lms),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.kelompokProgramStudi,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.rumpunIlmu,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Mapel Pendukung:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = chipBg,
                contentColor = chipText
            ) {
                Text(
                    text = item.kurikulumMerdeka,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun JurusanScreenPreview() {
    AppTheme {
        JurusanScreen(
            jurusanList = listOf(
                JurusanItem(
                    kelompokProgramStudi = "Kedokteran",
                    rumpunIlmu = "Ilmu Kesehatan",
                    kurikulumMerdeka = "Biologi, Kimia"
                ),
                JurusanItem(
                    kelompokProgramStudi = "Teknik Informatika",
                    rumpunIlmu = "Ilmu Komputer",
                    kurikulumMerdeka = "Matematika Tingkat Lanjut, Fisika"
                ),
                JurusanItem(
                    kelompokProgramStudi = "Hukum",
                    rumpunIlmu = "Ilmu Sosial",
                    kurikulumMerdeka = "Sosiologi, PPKn"
                )
            ),
            searchQuery = "",
            isLoading = false,
            onSearchQueryChange = {},
            onBack = {}
        )
    }
}
