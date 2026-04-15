package aarambh.apps.intellidrive.data.model

/**
 * App-level route model.
 * Saved to Firestore at **routes/active** by the instructor,
 * and fetched by the student dashboard.
 *
 * ⚠️ This describes a PRACTICE ZONE, not a route starting from the user's location.
 *   - [destLat]/[destLng] is the practice zone starting point (5–20 km from the user).
 *   - The student first navigates TO this zone, then follows the practice loop.
 *   - [encodedPolyline] + [encodedPolylineReturn] form a loop that starts and ends
 *     at the zone starting point.
 *
 * All fields have defaults so Firestore's [toObject] can populate it
 * without a no-arg constructor annotation.
 */
data class RouteData(
    val routeId: String = "",
    val instructorId: String = "",

    /** Latitude of the practice zone starting point (NOT the user's current location). */
    val destLat: Double = 0.0,

    /** Longitude of the practice zone starting point (NOT the user's current location). */
    val destLng: Double = 0.0,

    /**
     * Latitude of the turnaround point INSIDE the zone.
     * Passed as a mandatory waypoint to Google Maps so it follows the intended
     * loop road rather than calculating its own shorter route.
     */
    val turnAroundLat: Double = 0.0,

    /**
     * Longitude of the turnaround point INSIDE the zone.
     * See [turnAroundLat].
     */
    val turnAroundLng: Double = 0.0,

    /** Always true — zone-based practice routes are loops starting and ending at the zone. */
    val isLoop: Boolean = true,

    /** Google-encoded polyline for the outbound leg within the practice zone. */
    val encodedPolyline: String = "",

    /** Google-encoded polyline for the return leg within the practice zone. */
    val encodedPolylineReturn: String = "",

    /** Total practice loop distance in km (outbound + return). */
    val distanceKm: Double = 0.0,

    /** Total estimated drive time for the loop in minutes. */
    val durationMinutes: Double = 0.0,

    /** Extra minutes caused by traffic (0 if data unavailable). */
    val trafficDelayMinutes: Double = 0.0,

    /** Number of turns in the full loop. */
    val turns: Int = 0,

    /**
     * Composite difficulty score of the practice loop.
     *
     *   difficulty = (turns × 3) + (distanceKm × 2) + (trafficDelayMinutes × 4)
     *
     * Outbound and return legs are combined before applying the formula, so the
     * combined distance/turns/traffic values are substituted directly.
     * Low difficulty = quiet roads, few turns (best for beginners).
     */
    val difficulty: Double = 0.0,

    val generatedAt: Long = 0L,
    val trainingDay: Int = 1
)
