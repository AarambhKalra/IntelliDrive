package aarambh.apps.intellidrive.data.model

data class EventEntity(
    val eventId: String = "",
    val sessionId: String = "",
    val learnerId: String = "",
    val eventType: String = "", // "harsh_braking", "overspeed", "route_deviation"
    val timestamp: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val severity: String = "medium", // "low", "medium", "high"
    val sensorValue: Double = 0.0
)
