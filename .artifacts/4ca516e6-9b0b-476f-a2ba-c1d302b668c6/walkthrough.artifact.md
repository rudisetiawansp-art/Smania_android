# Walkthrough - Ujian Feature Implementation

Implemented the `ujian` package and online exam features corresponding to the web version (`index.php` and `kerjakan_ujian.php`).

## Changes

### [NEW] Package & Classes (`id.rudissp.smania.ujian`)
- **`UjianItem.kt`**: Data model representing individual exams.
- **`UjianResponse.kt`**: Wrapper data class for JSON API response parsing.
- **`UjianAdapter.kt`**: RecyclerView adapter displaying exam items with category badges and actions.
- **`Ujian.kt`**: Main Activity for listing exams with interactive filter cards (Tersedia, Selesai, Berjalan), statistics counts, and empty states.
- **`KerjakanUjianActivity.kt`**: WebView-based activity loading `kerjakan_ujian.php` with JavaScript and DOM storage enabled.

### Layout & Resources
- **`activity_ujian.xml`**: Dashboard layout matching Material Design 3 guidelines with filter cards and RecyclerView.
- **`item_ujian.xml`**: Card layout for individual exams.
- **`activity_kerjakan_ujian.xml`**: Layout containing WebView and ProgressBar for taking exams.
- **`icm_ujian.xml`**: Vector drawable icon for the Ujian menu item.

### Integration
- **`AppConstants.kt`**: Added `UJIAN_URL`.
- **`AndroidManifest.xml`**: Registered `Ujian` and `KerjakanUjianActivity`.
- **`activity_main.xml` & `MainActivity.kt`**: Added the "Ujian Online" menu card and click listener.

## Verification Results

### Automated Tests
- **Build**: Successfully built `app:assembleDebug` with zero errors.
