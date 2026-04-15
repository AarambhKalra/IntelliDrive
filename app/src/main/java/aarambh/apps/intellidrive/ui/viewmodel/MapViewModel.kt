package aarambh.apps.intellidrive.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
}
