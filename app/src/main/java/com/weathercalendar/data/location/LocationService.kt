package com.weathercalendar.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.weathercalendar.data.remote.NominatimApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
)

@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nominatimApi: NominatimApi,
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): Result<LocationResult> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext Result.failure(SecurityException("缺少定位权限"))
        }

        try {
            val location = getLastOrCurrentLocation()
                ?: return@withContext Result.failure(Exception("无法获取位置"))

            val cityName = reverseGeocode(location.first, location.second)

            Result.success(
                LocationResult(
                    latitude = location.first,
                    longitude = location.second,
                    cityName = cityName,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private suspend fun getLastOrCurrentLocation(): Pair<Double, Double>? {
        // 先尝试 lastLocation（快速）
        val lastLocation = suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location.latitude to location.longitude)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }

        if (lastLocation != null) return lastLocation

        // fallback: 请求一次当前位置
        return suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        cont.resume(location.latitude to location.longitude)
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    /**
     * 使用 Nominatim (OpenStreetMap) 反向地理编码。
     * 替代 Android Geocoder，在国内可正常使用。
     */
    private suspend fun reverseGeocode(lat: Double, lon: Double): String {
        return try {
            val response = nominatimApi.reverseGeocode(latitude = lat, longitude = lon)
            val address = response.address
            // 优先取 city，其次 town、village、county
            address?.city
                ?: address?.town
                ?: address?.village
                ?: address?.county
                ?: "未知位置"
        } catch (_: Exception) {
            "未知位置"
        }
    }
}
