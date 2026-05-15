package aarambh.apps.intellidrive.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/**
 * Holds the user's current location for Map screens.
 *
 * Extends [AndroidViewModel] so it can hold the Application context safely
 * (no Activity/Fragment context leaks).
 *
 * Call [fetchLocation] only AFTER the caller has verified location permission
 * is granted — the @SuppressLint is intentional.
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(application)

    // ── Current location ──────────────────────────────────────────────────────

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    // ── Loading state ─────────────────────────────────────────────────────────

    private val _isLoadingLocation = MutableStateFlow(false)
    val isLoadingLocation: StateFlow<Boolean> = _isLoadingLocation.asStateFlow()

    private val sessionRepository = aarambh.apps.intellidrive.data.repository.SessionRepository()

    // ── Location fetch ────────────────────────────────────────────────────────

    /**
     * Fetches the current location using [CurrentLocationRequest] with high accuracy.
     * Unlike [lastLocation], this actively acquires a fresh fix, so it works on devices
     * that have never had a GPS lock (emulators, fresh boots).
     * Permission **must** be granted before calling this.
     */
    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        _isLoadingLocation.value = true

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            // Accept a cached fix up to 30 s old before forcing a new one
            .setMaxUpdateAgeMillis(30_000L)
            // Wait up to 10 s for a fresh fix before giving up
            .setDurationMillis(10_000L)
            .build()

        fusedLocationClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                _isLoadingLocation.value = false
                location?.let {
                    _currentLocation.value = LatLng(it.latitude, it.longitude)
                }
            }
            .addOnFailureListener {
                _isLoadingLocation.value = false
            }
    }

    private var locationCallback: com.google.android.gms.location.LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startLiveTracking(studentId: String) {
        if (locationCallback != null) return
        Log.d("MapViewModel", "Starting live tracking for student: $studentId")

        // Push initial known location immediately to unblock Parent Dashboard
        // in case the device is stationary (e.g. testing on an emulator).
        // Uses .first() to wait if fetchLocation() hasn't returned the GPS lock yet.
        viewModelScope.launch {
            Log.d("MapViewModel", "Waiting for first valid location...")
            val currentLoc = _currentLocation.first { it != null }
            if (currentLoc != null) {
                Log.d("MapViewModel", "Found location: $currentLoc. Syncing to Firestore for parent.")
                sessionRepository.updateLiveLocation(
                    aarambh.apps.intellidrive.data.model.LiveLocation(
                        studentId = studentId,
                        latitude = currentLoc.latitude,
                        longitude = currentLoc.longitude,
                        timestamp = System.currentTimeMillis(),
                        speed = 0f,
                        bearing = 0f
                    )
                ).onFailure { Log.e("MapViewModel", "Failed to sync initial location", it) }
            }
        }

        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { loc ->
                    Log.d("MapViewModel", "New location update: ${loc.latitude}, ${loc.longitude}")
                    _currentLocation.value = LatLng(loc.latitude, loc.longitude)
                    
                    // Sync to Firestore
                    viewModelScope.launch {
                        sessionRepository.updateLiveLocation(
                            aarambh.apps.intellidrive.data.model.LiveLocation(
                                studentId = studentId,
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                timestamp = System.currentTimeMillis(),
                                speed = loc.speed,
                                bearing = loc.bearing
                            )
                        ).onFailure { Log.e("MapViewModel", "Failed to sync live location", it) }
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request, 
            locationCallback!!, 
            android.os.Looper.getMainLooper()
        )
    }

    fun stopLiveTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
}
