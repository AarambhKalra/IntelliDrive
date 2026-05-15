package aarambh.apps.intellidrive.util

import android.content.Context
import android.hardware.SensorManager
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import aarambh.apps.intellidrive.data.model.EventEntity
import aarambh.apps.intellidrive.data.repository.EventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionManager(
    private val context: Context,
    private val sessionId: String,
    private val learnerId: String,
    assignedRoutePolyline: List<LatLng>
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val eventRepository = EventRepository()
    private val liveLocationManager = LiveLocationManager()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Stats
    var harshBrakingCount = 0
        private set
    var overspeedCount = 0
        private set
    var routeDeviationCount = 0
        private set

    private val behaviorManager = BehaviorAnalysisManager(
        sensorManager = sensorManager,
        onHarshBrakingDetected = { magnitude ->
            harshBrakingCount++
            logEvent("harsh_braking", magnitude.toDouble())
            NotificationHelper.showNotification(context, "Alert", "Harsh braking detected!")
        },
        onOverspeedDetected = { speedKmh ->
            overspeedCount++
            logEvent("overspeed", speedKmh.toDouble())
            NotificationHelper.showNotification(context, "Alert", "Overspeed detected!")
        }
    )

    private val routeDeviationManager = RouteDeviationManager(assignedRoutePolyline) {
        routeDeviationCount++
        logEvent("route_deviation", 0.0)
        NotificationHelper.showNotification(context, "Alert", "Route deviation detected!")
    }

    fun start() {
        liveLocationManager.startSession(sessionId)
        behaviorManager.start()
    }

    fun stop() {
        behaviorManager.stop()
        liveLocationManager.stopSession()
    }

    fun onLocationUpdated(location: Location) {
        liveLocationManager.updateLocation(location)
        behaviorManager.analyzeSpeed(location.speed)
        routeDeviationManager.checkDeviation(location)
    }

    private fun logEvent(type: String, value: Double) {
        coroutineScope.launch {
            val event = EventEntity(
                sessionId = sessionId,
                learnerId = learnerId,
                eventType = type,
                timestamp = System.currentTimeMillis(),
                severity = "high", // Simplify for now
                sensorValue = value
            )
            eventRepository.saveEvent(event)
        }
    }
}
