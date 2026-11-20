package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationHelper {

    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun formatMapsUrl(latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lng = String.format(Locale.US, "%.6f", longitude)
        return "https://maps.google.com/?q=$lat,$lng"
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocationMapsUrl(
        context: Context,
        onResult: (url: String?, error: String?) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onResult(null, "Location permission required")
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onResult(null, "Location service unavailable")
            return
        }

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val bestLastKnown = providers.mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }

        // If a reasonably fresh fix (<= 2 minutes old) is cached, return immediately
        if (bestLastKnown != null && (System.currentTimeMillis() - bestLastKnown.time) <= 120_000L) {
            onResult(formatMapsUrl(bestLastKnown.latitude, bestLastKnown.longitude), null)
            return
        }

        val activeProvider = when {
            providers.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            providers.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            providers.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            bestLastKnown != null -> {
                onResult(formatMapsUrl(bestLastKnown.latitude, bestLastKnown.longitude), null)
                return
            }
            else -> {
                onResult(null, "Turn on GPS / Location services first")
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellation = CancellationSignal()
            var finished = false
            val handler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (!finished) {
                    finished = true
                    cancellation.cancel()
                    if (bestLastKnown != null) {
                        onResult(formatMapsUrl(bestLastKnown.latitude, bestLastKnown.longitude), null)
                    } else {
                        onResult(null, "Could not detect GPS fix. Try outdoors or paste Google Maps link.")
                    }
                }
            }
            handler.postDelayed(timeoutRunnable, 8000L)
            runCatching {
                lm.getCurrentLocation(
                    activeProvider,
                    cancellation,
                    ContextCompat.getMainExecutor(context)
                ) { loc: Location? ->
                    if (!finished) {
                        finished = true
                        handler.removeCallbacks(timeoutRunnable)
                        if (loc != null) {
                            onResult(formatMapsUrl(loc.latitude, loc.longitude), null)
                        } else if (bestLastKnown != null) {
                            onResult(formatMapsUrl(bestLastKnown.latitude, bestLastKnown.longitude), null)
                        } else {
                            onResult(null, "Location unavailable. Turn on GPS or paste Maps link.")
                        }
                    }
                }
            }.onFailure {
                if (!finished) {
                    finished = true
                    handler.removeCallbacks(timeoutRunnable)
                    if (bestLastKnown != null) {
                        onResult(formatMapsUrl(bestLastKnown.latitude, bestLastKnown.longitude), null)
                    } else {
                        onResult(null, "Could not read GPS location")
                    }
                }
            }
        } else {
            val handler = Handler(Looper.getMainLooper())
            var finished = false
            lateinit var listener: LocationListener
            val timeoutRunnable = Runnable {
                if (!finished) {
                    finished = true
                    runCatching { lm.removeUpdates(listener) }
                    if (bestLastKnown != null) {
                        onResult(formatMapsUrl(bestLastKnown.latitude, bestLastKnown.longitude), null)
                    } else {
                        onResult(null, "Could not detect GPS fix. Turn on GPS or paste Maps link.")
                    }
                }
            }
            listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!finished) {
                        finished = true
                        handler.removeCallbacks(timeoutRunnable)
                        runCatching { lm.removeUpdates(this) }
                        onResult(formatMapsUrl(location.latitude, location.longitude), null)
                    }
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
            }
            handler.postDelayed(timeoutRunnable, 8000L)
            runCatching {
                lm.requestLocationUpdates(activeProvider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure {
                if (!finished) {
                    finished = true
                    handler.removeCallbacks(timeoutRunnable)
                    if (bestLastKnown != null) {
                        onResult(formatMapsUrl(bestLastKnown.latitude, bestLastKnown.longitude), null)
                    } else {
                        onResult(null, "Could not read GPS location")
                    }
                }
            }
        }
    }

    fun openInGoogleMaps(context: Context, locationUrlOrAddress: String): Boolean {
        val raw = locationUrlOrAddress.trim()
        if (raw.isEmpty()) return false

        val primaryUri = when {
            raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true) ||
                raw.startsWith("geo:", ignoreCase = true) -> Uri.parse(raw)
            else -> Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(raw)}")
        }

        // First try opening directly in Google Maps app if available
        val mapsIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mapsIntent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Fall through to generic browser / map chooser
        } catch (_: SecurityException) {
            // Fall through
        }

        val fallbackUri = if (raw.startsWith("geo:", ignoreCase = true)) {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(raw.removePrefix("geo:"))}")
        } else {
            primaryUri
        }
        val genericIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(genericIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
