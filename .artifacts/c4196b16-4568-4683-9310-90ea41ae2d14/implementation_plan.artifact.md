# Migration of Kaldik.kt to Jetpack Compose with Material 3 Expressive

Migrate the academic calendar activity (`Kaldik.kt`) and its associated XML layouts (`activity_kaldik.xml`, `calendar_day_layout.xml`, `item_agenda_harian.xml`) and adapter (`AgendaHarianAdapter.kt`) to Jetpack Compose with Material 3 Expressive design.

## User Review Required

> [!IMPORTANT]
> - **Dependency Update**: We will add `com.kizitonwose.calendar:compose:2.10.1` to support the Jetpack Compose version of Kizitonwose calendar.
> - **Design System**: Applying Material 3 Expressive guidelines (vibrant color roles, expressive card shapes, loading indicators, and modern typography).

## Open Questions

None. The API data source (Google Calendar API via Volley) and business logic will be preserved while updating the presentation layer to declarative Compose.

## Proposed Changes

### Build Configuration
#### [MODIFY] [build.gradle.kts](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/build.gradle.kts)
- Add Kizitonwose Compose calendar dependency (`com.kizitonwose.calendar:compose:2.10.1`).

### Kaldik Feature (`id.rudissp.smania.kaldik`)
#### [MODIFY] [Kaldik.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/kaldik/Kaldik.kt)
- Convert `Kaldik` activity to use Jetpack Compose `setContent` with Material 3 Expressive theme.
- Implement `KaldikScreen` composable featuring:
  - Material 3 Expressive TopAppBar.
  - Month navigation header with expressive icon buttons.
  - Kizitonwose Compose `HorizontalCalendar` with custom day cell styling (event dots, selected date pill indicator with Expressive styling).
  - Selected date agenda header.
  - `LazyColumn` for daily agenda items using Expressive Material 3 Cards.
- Retain Volley API data fetching and agenda parsing logic, feeding state into a Compose `State` or `MutableState`.

#### [DELETE] [AgendaHarianAdapter.kt](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/java/id/rudissp/smania/kaldik/AgendaHarianAdapter.kt)
- Replaced by Compose `LazyColumn`.

#### [DELETE] [activity_kaldik.xml](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/res/layout/activity_kaldik.xml)
- Replaced by `KaldikScreen` Composable.

#### [DELETE] [calendar_day_layout.xml](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/res/layout/calendar_day_layout.xml)
- Replaced by Compose day cell layout.

#### [DELETE] [item_agenda_harian.xml](file:///C:/Users/rsspv/Documents/AndroidStudio/Smania/app/src/main/res/layout/item_agenda_harian.xml)
- Replaced by Compose agenda item card.

## Verification Plan

### Automated Tests
- Build project using `./gradlew assembleDebug` to verify compilation.

### Manual Verification
- Deploy to an Android device/emulator and verify the Academic Calendar opens, fetches calendar events via Volley, renders the calendar grid correctly, allows month switching, and displays daily agendas upon tapping a date.
