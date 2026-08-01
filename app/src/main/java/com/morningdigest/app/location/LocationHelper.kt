package com.morningdigest.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Resolves the device's current GPS/network location into a short, human
 * readable label (e.g. "Oslo, Norway") for the dashboard greeting. Uses only
 * platform APIs - no Play Services / FusedLocationProvider dependency - to
 * keep the existing lightweight dependency footprint.
 */
object LocationHelper {

    private const val LOCATION_TIMEOUT_MILLIS = 8000L
    private const val FRESH_ENOUGH_MILLIS = 10 * 60 * 1000L

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Returns a short "City, Country" label for the device's current location, or null if it can't be resolved. */
    suspend fun getCurrentLocationLabel(context: Context): String? {
        if (!hasLocationPermission(context)) return null
        val location = getLocation(context) ?: return null
        return reverseGeocode(context, location.latitude, location.longitude)
    }

    private suspend fun getLocation(context: Context): Location? = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return@withContext null

        // A recent last-known fix is instant and good enough - no need to wait on a fresh GPS lock.
        val lastKnown = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < FRESH_ENOUGH_MILLIS) {
            return@withContext lastKnown
        }

        // Otherwise ask for one fresh fix, bounded by a timeout so the UI never hangs.
        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            requestSingleUpdate(manager, providers.first())
        }
        fresh ?: lastKnown
    }

    @Suppress("DEPRECATION")
    private suspend fun requestSingleUpdate(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location)
                }
            }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@withContext null
                val locality = address.locality ?: address.subAdminArea ?: address.adminArea
                val country = address.countryName
                listOfNotNull(locality, country).joinToString(", ").ifBlank { null }
            }.getOrNull()
        }
}
