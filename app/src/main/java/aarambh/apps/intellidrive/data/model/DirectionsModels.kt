package aarambh.apps.intellidrive.data.model

import com.google.gson.annotations.SerializedName

// ── Top-level response ────────────────────────────────────────────────────────

data class DirectionsResponse(
    val status: String = "",
    val routes: List<DirectionsRoute> = emptyList()
)

// ── Route → Leg → Step hierarchy ──────────────────────────────────────────────

data class DirectionsRoute(
    val legs: List<DirectionsLeg> = emptyList(),
    @SerializedName("overview_polyline")
    val overviewPolyline: OverviewPolyline = OverviewPolyline()
)

data class DirectionsLeg(
    val distance: ValueText = ValueText(),
    val duration: ValueText = ValueText(),
    /** Only present when departure_time=now and traffic model is available. */
    @SerializedName("duration_in_traffic")
    val durationInTraffic: ValueText? = null,
    val steps: List<DirectionsStep> = emptyList()
)

data class DirectionsStep(
    /** e.g. "turn-left", "turn-right", "ramp-left", "uturn-right", null = straight */
    val maneuver: String? = null
)

// ── Primitives ────────────────────────────────────────────────────────────────

/** Shared shape for distance (metres) / duration (seconds) fields. */
data class ValueText(
    val text: String = "",
    val value: Int = 0         // metres for distance; seconds for duration
)

data class OverviewPolyline(
    val points: String = ""    // Google encoded polyline string
)
