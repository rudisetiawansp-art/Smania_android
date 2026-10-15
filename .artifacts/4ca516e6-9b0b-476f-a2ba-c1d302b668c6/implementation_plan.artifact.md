# Implementation Plan - Ujian Activity & Package

Implement the `ujian` package and `Ujian` activity replicating the functionality and layout of `index.php` and exam dashboard from the web version (`https://sman1sukorejo.sch.id/smania/android/ujian_load.php` or corresponding API).

## Proposed Changes

### [NEW] Package & Classes in `id.rudissp.smania.ujian`

#### [NEW] [UjianItem.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/ujian/UjianItem.kt)
Data model representing an exam item matching the API response.

#### [NEW] [UjianResponse.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/ujian/UjianResponse.kt)
Response wrapper model for Gson parsing.

#### [NEW] [UjianAdapter.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/ujian/UjianAdapter.kt)
RecyclerView Adapter for displaying exams with filtering (tersedia, selesai, berjalan).

#### [NEW] [Ujian.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/ujian/Ujian.kt)
Main Activity for the exam list, handling Volley requests, statistics cards, filtering, and opening exams.

### Layout Resources

#### [NEW] [activity_ujian.xml](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/res/layout/activity_ujian.xml)
Main layout replicating the web design (header, stat filter cards, RecyclerView list, empty state).

#### [NEW] [item_ujian.xml](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/res/layout/item_ujian.xml)
Card layout for each exam item in the RecyclerView.

### Integration

#### [MODIFY] [AppConstants.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/AppConstants.kt)
Add `UJIAN_URL` endpoint constant.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/AndroidManifest.xml)
Register `.ujian.Ujian` activity.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/MainActivity.kt)
Wire up the Ujian menu click listener to launch `Ujian` activity.
