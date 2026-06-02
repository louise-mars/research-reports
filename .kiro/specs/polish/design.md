# Design — Final Polish

## 1. Temperature Unit

### Data Flow
```
UserPrefsRepository.prefs (Flow<UserPrefs>)
    ↓
HomeViewModel observes temperatureUnit
    ↓
Converts all temperatures before emitting to UI state
    ↓
UI displays with correct symbol (°C / °F)
```

### Conversion
- °C → °F: `(celsius * 9/5) + 32`
- Applied in ViewModel, not in UI layer
- HomeUiState gains `tempUnit: TemperatureUnit` field

## 2. Swipe Day Focus

### Architecture
- HomeScreen wraps the main content in a `HorizontalPager` (3 pages)
- Each page represents a day (today, tomorrow, day after)
- The Hero Card and gradient animate based on `currentPage`
- The fusion card's `expandedIndex` syncs with `currentPage`
- Page indicator: 3 small dots below the Hero Card

### Interaction
```
[Page 0: Today] ←swipe→ [Page 1: Tomorrow] ←swipe→ [Page 2: Day After]
```
- Only the Hero Card and gradient change per page
- Hourly forecast and fusion card list remain static (shared across pages)
- Fusion card auto-expands the matching day

## 3. Pull-to-Refresh

### Component
- `PullToRefreshBox` wraps the entire HomeScreen content
- `isRefreshing` bound to `uiState.isLoading`
- `onRefresh` calls `viewModel.loadData()`
- Works in all states (success, error with cached data)

## 4. App Icon

### Adaptive Icon Structure
```
res/mipmap-anydpi-v26/ic_launcher.xml     → adaptive-icon
res/drawable/ic_launcher_foreground.xml    → vector foreground
res/values/colors.xml                      → ic_launcher_background color
```

### Foreground Design
- White sun circle (top-right area)
- White calendar grid lines (center-bottom)
- Clean, minimal, recognizable at small sizes

## 5. Code Fixes
- Replace `LocalDate.of(2026, 4, 16)` with `LocalDate.now()` in DailyWeatherCalendarCard
- Remove unused imports across all files
