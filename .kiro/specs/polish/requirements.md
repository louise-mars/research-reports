# Requirements — Final Polish

## Requirement 1: Temperature Unit Support
- User can switch between °C and °F in Settings
- All temperature displays across the app must respect this preference
- Affected screens: HomeScreen (Hero Card, hourly chips, daily cards), CalendarScreen (BottomSheet)
- Default: °C

## Requirement 2: Swipe to Switch Day Focus
- User can swipe left/right on the HomeScreen to switch between today/tomorrow/day-after-tomorrow
- Swiping changes the Hero Card content (temperature, icon, gradient) to the focused day
- The corresponding day in the fusion card auto-expands
- Page indicator (3 dots) shows current focus
- Swipe animation: 300ms crossfade + gradient transition

## Requirement 3: Pull-to-Refresh
- User can pull down on HomeScreen to refresh weather data
- Uses Material 3 PullToRefreshBox component
- Shows refresh indicator while loading
- Triggers full data reload (API call, bypassing cache)

## Requirement 4: App Icon
- Proper adaptive icon for Android 8.0+
- Foreground: weather + calendar combined symbol (sun + calendar grid)
- Background: sky blue (#4FC3F7)
- Must look good on all launcher shapes (circle, squircle, rounded square)

## Requirement 5: Compilation Verification
- All source files must compile without errors
- Fix the hardcoded `LocalDate.of(2026, 4, 16)` in DailyWeatherCalendarCard to use `LocalDate.now()`
- Ensure all imports are correct and no unused imports remain
