package com.weathercalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weathercalendar.data.repository.TemperatureUnit
import com.weathercalendar.data.repository.UserPrefs
import com.weathercalendar.data.repository.UserPrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefsRepository: UserPrefsRepository,
) : ViewModel() {

    val uiState: StateFlow<UserPrefs> = userPrefsRepository.prefs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPrefs(),
        )

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { userPrefsRepository.setTemperatureUnit(unit) }
    }

    fun setUseLocation(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setUseLocation(enabled) }
    }
}
