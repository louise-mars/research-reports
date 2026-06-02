package com.weathercalendar.ui.city

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercalendar.data.mock.MockData
import com.weathercalendar.data.model.City
import com.weathercalendar.data.remote.GeocodingResult
import com.weathercalendar.ui.theme.WeatherCalendarTheme

/**
 * 城市选择器 BottomSheet。
 * 搜索框 + GPS定位城市 + 收藏城市列表 + 搜索结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerSheet(
    cities: List<City>,
    searchResults: List<GeocodingResult> = emptyList(),
    isSearching: Boolean = false,
    onSearch: (String) -> Unit = {},
    onCitySelected: (City) -> Unit,
    onAddCity: (GeocodingResult) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "选择城市",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索城市...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            Spacer(Modifier.height(16.dp))

            // 搜索结果
            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                SectionLabel("搜索结果")
                Spacer(Modifier.height(8.dp))
                searchResults.take(5).forEach { result ->
                    SearchResultRow(
                        result = result,
                        onSelect = {
                            onCitySelected(
                                City(
                                    name = result.name,
                                    latitude = result.latitude,
                                    longitude = result.longitude,
                                )
                            )
                        },
                        onAdd = { onAddCity(result) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            // 当前定位
            val currentCity = cities.find { it.isCurrentLocation }
            if (currentCity != null) {
                SectionLabel("📍 当前定位")
                Spacer(Modifier.height(8.dp))
                CityRow(
                    city = currentCity,
                    onClick = { onCitySelected(currentCity) },
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
            }

            // 收藏城市
            val savedCities = cities.filter { !it.isCurrentLocation }
            if (savedCities.isNotEmpty()) {
                SectionLabel("★ 收藏城市")
                Spacer(Modifier.height(8.dp))
                savedCities.forEach { city ->
                    CityRow(
                        city = city,
                        onClick = { onCitySelected(city) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CityRow(
    city: City,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (city.isCurrentLocation) Icons.Default.LocationOn
            else Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = city.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (city.temperature != null && city.condition != null) {
            Text(
                text = "${city.temperature}°",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = city.condition.icon,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    result: GeocodingResult,
    onSelect: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            val subtitle = listOfNotNull(result.admin1, result.country).joinToString(", ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onAdd) {
            Icon(
                Icons.Default.Add,
                contentDescription = "添加到收藏",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CityPickerContentPreview() {
    WeatherCalendarTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(24.dp)) {
            SectionLabel("📍 当前定位")
            Spacer(Modifier.height(8.dp))
            CityRow(city = MockData.cities[0], onClick = {})
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionLabel("★ 收藏城市")
            Spacer(Modifier.height(8.dp))
            MockData.cities.drop(1).forEach { city ->
                CityRow(city = city, onClick = {})
            }
        }
    }
}
