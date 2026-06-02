# Tasks — Final Polish

## Task 1: Temperature Unit Integration
- [x] Add `tempUnit` field to `HomeUiState`
- [x] Add temperature conversion utility function (`TemperatureConverter.kt`)
- [x] HomeViewModel reads `userPrefsRepository.prefs` and passes tempUnit to UI state
- [x] CurrentWeatherCard displays temperature with unit symbol
- [x] HourlyForecastRow displays temperature with unit
- [x] DailyWeatherCalendarCard displays temperature range with unit
- [x] ExpandableDetailsCard wind speed unaffected (always km/h)

## Task 2: Swipe Day Focus (HorizontalPager)
- [x] HorizontalPager wraps Hero Card for 3 pages (today/tomorrow/day-after)
- [x] Each page shows Hero Card with that day's weather
- [x] Gradient animates smoothly between pages (animateColorAsState, 500ms)
- [x] Page indicator (3 dots) below Hero Card
- [x] Fusion card expandedIndex syncs with pager currentPage via LaunchedEffect

## Task 3: Pull-to-Refresh
- [x] PullToRefreshBox wraps entire HomeScreen content
- [x] isRefreshing bound to uiState.isLoading
- [x] onRefresh triggers viewModel.loadData()

## Task 4: App Icon
- [x] Created proper ic_launcher_foreground.xml (sun + calendar vector)
- [x] Created adaptive icon XMLs in mipmap-anydpi-v26
- [x] Background color #4FC3F7 in colors.xml
- [x] Cleaned up old mipmap-hdpi placeholder icons

## Task 5: Code Cleanup & Compilation Fix
- [x] Fixed hardcoded date in CalendarScreen → LocalDate.now()
- [x] Fixed lambda comparison issue in DailyWeatherCalendarCard
- [x] Removed unused imports across modified files
- [x] NavHost passes tempUnit to HomeScreen
