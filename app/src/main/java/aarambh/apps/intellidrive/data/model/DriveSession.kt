package aarambh.apps.intellidrive.data.model

data class DriveSession(
    val sessionId: String = "",
    val studentId: String = "",
    val routeId: String = "",
    val trainingDay: Int = 1,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val totalDistanceKm: Double = 0.0,
    val wasCompleted: Boolean = false
)
