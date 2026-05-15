package aarambh.apps.intellidrive.util

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

class RouteDeviationManager(
    private val assignedRoutePolyline: List<LatLng>,
    private val onDeviationDetected: () -> Unit
) {
    private val DEVIATION_TOLERANCE_METERS = 200.0
    private var deviationStartTime: Long = 0
    private val DEVIATION_DURATION_THRESHOLD_MS = 10000L // 10 seconds
    private var isDeviating = false

    fun checkDeviation(currentLocation: Location) {
        if (assignedRoutePolyline.isEmpty()) return

        val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
        
        // Check if the current location is on the path within the tolerance
        val isOnPath = PolyUtil.isLocationOnPath(currentLatLng, assignedRoutePolyline, true, DEVIATION_TOLERANCE_METERS)

        if (!isOnPath) {
            if (!isDeviating) {
                isDeviating = true
                deviationStartTime = System.currentTimeMillis()
            } else {
                val duration = System.currentTimeMillis() - deviationStartTime
                if (duration > DEVIATION_DURATION_THRESHOLD_MS) {
                    onDeviationDetected()
                    // Reset to avoid continuous spamming for the same deviation
                    isDeviating = false
                }
            }
        } else {
            isDeviating = false
        }
    }
}
