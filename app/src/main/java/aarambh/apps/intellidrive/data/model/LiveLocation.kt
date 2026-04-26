package aarambh.apps.intellidrive.data.model

data class LiveLocation(
    val studentId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f
)
