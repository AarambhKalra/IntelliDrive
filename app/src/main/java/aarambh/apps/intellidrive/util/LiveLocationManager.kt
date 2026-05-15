package aarambh.apps.intellidrive.util

import android.location.Location
import com.google.firebase.database.FirebaseDatabase

class LiveLocationManager(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private var currentSessionId: String? = null

    fun startSession(sessionId: String) {
        currentSessionId = sessionId
    }

    fun stopSession() {
        currentSessionId = null
    }

    fun updateLocation(location: Location) {
        val sessionId = currentSessionId ?: return
        val ref = database.getReference("sessions").child(sessionId).child("liveLocation")
        
        val locationData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "speed" to location.speed, // in m/s
            "bearing" to location.bearing,
            "timestamp" to System.currentTimeMillis()
        )
        
        ref.setValue(locationData)
    }
}
