package aarambh.apps.intellidrive.data.repository

import aarambh.apps.intellidrive.data.api.RetrofitClient
import aarambh.apps.intellidrive.data.model.DirectionsStep
import aarambh.apps.intellidrive.data.model.RouteData
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Handles practice-zone discovery, route generation, and Firestore persistence.
 *
 * ── NEW CONCEPT ──────────────────────────────────────────────────────────────
 * Routes are no longer generated FROM the user's current location.
 * Instead, the app searches for a suitable PRACTICE ZONE within 5–20 km of the
 * user and generates a loop route WITHIN that zone.
 *
 *   • Early days (Day 1–5):  finds the quietest, lowest-traffic area available.
 *   • Later days (Day 16+):  selects busier, more complex zones to build skill.
 *
 * The [RouteData.destLat]/[RouteData.destLng] stored in Firestore is the practice
 * zone starting point — not a destination the user drives to from their home.
 *
 * Flow (instructor):
 *   generateBestRoute(location) → scans 8 zones → picks best-match → saves to Firestore
 *
 * Flow (student):
 *   observeActiveRoute()        → real-time Firestore snapshot listener as a Flow
 *
 * @param mapsApiKey  Google Maps API key from BuildConfig.MAPS_API_KEY
 */
class RouteRepository(private val mapsApiKey: String) {

    private val api = RetrofitClient.directionsService
    private val db  = FirebaseFirestore.getInstance()

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val ACTIVE_ROUTE_PATH = "routes/active"
        private const val EARTH_RADIUS_KM   = 6371.0

        // ── Zone search ───────────────────────────────────────────────────────
        /** Minimum distance from the user to search for practice zones. */
        private const val ZONE_SEARCH_MIN_KM = 1.0
        /** Maximum distance from the user to search for practice zones. */
        private const val ZONE_SEARCH_MAX_KM = 10.0
        /** How many distinct zones to evaluate (evenly spread in all directions). */
        private const val ZONE_CANDIDATE_COUNT = 8

        // ── Practice loop sizing ──────────────────────────────────────────────
        /**
         * Multiplier converting desired loop leg length to radial offset for the
         * Directions API call.  A mini-dest offset of [x] km yields a round-trip
         * road distance of roughly [x / PRACTICE_RADIAL_FACTOR] km.
         * (Roads are typically 1.3–1.5× straight-line distance each way.)
         */
        private const val PRACTICE_RADIAL_FACTOR = 0.42

        /** Default target difficulty: very low so we pick the easiest zone unless overridden. */
        private const val DEFAULT_TARGET_DIFFICULTY = 5.0

        /** Maneuver strings counted as a turn in the difficulty score. */
        private val TURN_MANEUVERS = setOf(
            "turn-left", "turn-right",
            "turn-slight-left", "turn-slight-right",
            "turn-sharp-left", "turn-sharp-right",
            "uturn-left", "uturn-right",
            "ramp-left", "ramp-right",
            "fork-left", "fork-right",
            "roundabout-left", "roundabout-right",
            "merge"
        )
    }

    // ── Internal candidate model ──────────────────────────────────────────────

    private data class RouteCandidate(
        val encodedPolyline: String,
        val encodedPolylineReturn: String,
        /** The zone starting point that becomes [RouteData.destLat]/[RouteData.destLng]. */
        val zoneCenter: LatLng,
        /** The turnaround point inside the zone that becomes [RouteData.turnAroundLat]/[RouteData.turnAroundLng]. */
        val miniDest: LatLng,
        val distanceKm: Double,
        val durationMinutes: Double,
        val trafficDelayMinutes: Double,
        val turns: Int,
        val difficulty: Double
    )

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /**
     * Returns [ZONE_CANDIDATE_COUNT] candidate zone centres, evenly spread across
     * all compass directions, each at a random distance in
     * [ZONE_SEARCH_MIN_KM]..[ZONE_SEARCH_MAX_KM] from [userLocation].
     *
     * Spreading evenly ensures we probe quiet suburbs, industrial estates, and
     * semi-rural roads in every direction — not just one cluster of similar zones.
     */
    private fun zoneSearchCandidates(userLocation: LatLng): List<LatLng> {
        return (0 until ZONE_CANDIDATE_COUNT).map { i ->
            val angle  = (2 * Math.PI * i) / ZONE_CANDIDATE_COUNT  // evenly spaced
            val distKm = Random.nextDouble(ZONE_SEARCH_MIN_KM, ZONE_SEARCH_MAX_KM)
            offsetLatLng(userLocation, distKm, angle)
        }
    }

    /**
     * Returns one random practice turn-around point within
     * [minRadiusKm]..[maxRadiusKm] of [zoneCenter].  This is the mini-destination
     * inside the practice zone that defines the loop length.
     */
    private fun randomPracticeDest(
        zoneCenter: LatLng,
        minRadiusKm: Double,
        maxRadiusKm: Double
    ): LatLng {
        val angle  = Random.nextDouble(0.0, 2 * Math.PI)
        val distKm = Random.nextDouble(minRadiusKm.coerceAtLeast(0.3), maxRadiusKm.coerceAtLeast(0.5))
        return offsetLatLng(zoneCenter, distKm, angle)
    }

    /** Applies a bearing/distance offset using the flat-earth approximation. */
    private fun offsetLatLng(origin: LatLng, distKm: Double, angleRad: Double): LatLng {
        val dLat = Math.toDegrees(distKm / EARTH_RADIUS_KM) * cos(angleRad)
        val dLng = Math.toDegrees(
            distKm / (EARTH_RADIUS_KM * cos(Math.toRadians(origin.latitude)))
        ) * sin(angleRad)
        return LatLng(origin.latitude + dLat, origin.longitude + dLng)
    }

    // ── Turn counting ─────────────────────────────────────────────────────────

    private fun countTurns(steps: List<DirectionsStep>): Int =
        steps.count { step -> step.maneuver?.let { it in TURN_MANEUVERS } ?: false }

    // ── Single Directions API call ────────────────────────────────────────────

    private suspend fun fetchCandidate(origin: LatLng, dest: LatLng): RouteCandidate? {
        return try {
            val response = api.getDirections(
                origin        = "${origin.latitude},${origin.longitude}",
                destination   = "${dest.latitude},${dest.longitude}",
                departureTime = "now",
                apiKey        = mapsApiKey
            )
            if (response.status != "OK" || response.routes.isEmpty()) return null

            val route = response.routes.first()
            val leg   = route.legs.firstOrNull() ?: return null

            val distanceKm      = leg.distance.value / 1000.0
            val durationMin     = leg.duration.value / 60.0
            val trafficDelayMin = leg.durationInTraffic?.let { t ->
                maxOf(0.0, (t.value - leg.duration.value) / 60.0)
            } ?: 0.0
            val turns      = countTurns(leg.steps)
            val difficulty = (turns * 3) + (distanceKm * 2) + (trafficDelayMin * 4)

            RouteCandidate(
                encodedPolyline       = route.overviewPolyline.points,
                encodedPolylineReturn = "",     // filled by caller for loops
                zoneCenter            = origin, // caller sets the meaningful center
                miniDest              = dest,   // added to fix compile error
                distanceKm            = distanceKm,
                durationMinutes       = durationMin,
                trafficDelayMinutes   = trafficDelayMin,
                turns                 = turns,
                difficulty            = difficulty
            )
        } catch (e: Exception) {
            null    // skip this zone on network/API failure
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Discovers the best practice zone near [currentLocation] and generates a
     * loop route within that zone suited to [trainingDay].
     *
     * Algorithm:
     *   1. Produce [ZONE_CANDIDATE_COUNT] zone centres spread evenly around the user
     *      at 5–20 km in all compass directions.
     *   2. For each zone, call the Directions API for outbound + return legs of a
     *      practice loop whose size is governed by [minKm]..[maxKm].
     *   3. Score each zone's loop by:
     *          difficulty = (turns × 3) + (distanceKm × 2) + (trafficDelayMin × 4)
     *   4. Select the zone whose difficulty is closest to [targetDifficulty].
     *      • Low target (early days)  → picks the quietest, least-trafficked area.
     *      • High target (later days) → picks a busier, more complex road network.
     *
     * The returned [RouteData.destLat]/[RouteData.destLng] is the zone starting
     * point that students navigate TO before starting the practice loop.
     *
     * @param minKm            Minimum practice loop size in km.
     * @param maxKm            Maximum practice loop size in km.
     * @param targetDifficulty Day-scaled difficulty target (default = 5 = find easiest).
     */
    suspend fun generateBestRoute(
        currentLocation: LatLng,
        isLoop: Boolean,                                    // kept for API compat, always true now
        minKm: Double,
        maxKm: Double,
        trainingDay: Int,
        targetDifficulty: Double = DEFAULT_TARGET_DIFFICULTY
    ): Result<RouteData> = runCatching {

        // Radial offset for the mini-dest inside the zone.
        // PRACTICE_RADIAL_FACTOR converts the desired physical road loop to a
        // straight-line search radius for the Directions API call.
        val miniDestMinRadius = (minKm * PRACTICE_RADIAL_FACTOR).coerceAtLeast(0.3)
        val miniDestMaxRadius = (maxKm * PRACTICE_RADIAL_FACTOR).coerceAtLeast(0.6)

        // Step 1 — candidate zone centres in 8 directions, 5–20 km out
        val zoneCenters = zoneSearchCandidates(currentLocation)

        // Step 2 — evaluate each zone with a real Directions API loop
        val candidates = zoneCenters.mapNotNull { zoneCenter ->
            val miniDest = randomPracticeDest(zoneCenter, miniDestMinRadius, miniDestMaxRadius)

            // Outbound: zone centre → mini destination
            val out = fetchCandidate(zoneCenter, miniDest) ?: return@mapNotNull null
            // Return:   mini destination → zone centre
            val ret = fetchCandidate(miniDest, zoneCenter) ?: return@mapNotNull null

            RouteCandidate(
                encodedPolyline       = out.encodedPolyline,
                encodedPolylineReturn = ret.encodedPolyline,
                zoneCenter            = zoneCenter,
                miniDest              = miniDest,
                distanceKm            = out.distanceKm + ret.distanceKm,
                durationMinutes       = out.durationMinutes + ret.durationMinutes,
                trafficDelayMinutes   = out.trafficDelayMinutes + ret.trafficDelayMinutes,
                turns                 = out.turns + ret.turns,
                difficulty            = out.difficulty + ret.difficulty
            )
        }

        if (candidates.isEmpty()) {
            error(
                "Could not evaluate any practice zones. " +
                "Check your Maps API key and Internet connection."
            )
        }

        // Step 3 — select zone whose difficulty best matches the training-day target
        val best = candidates.minByOrNull { abs(it.difficulty - targetDifficulty) }!!

        fun Double.round1() = (this * 10).roundToInt() / 10.0

        RouteData(
            routeId               = UUID.randomUUID().toString(),
            destLat               = best.zoneCenter.latitude,
            destLng               = best.zoneCenter.longitude,
            turnAroundLat         = best.miniDest.latitude,
            turnAroundLng         = best.miniDest.longitude,
            isLoop                = true,
            encodedPolyline       = best.encodedPolyline,
            encodedPolylineReturn = best.encodedPolylineReturn,
            distanceKm            = best.distanceKm.round1(),
            durationMinutes       = best.durationMinutes.round1(),
            trafficDelayMinutes   = best.trafficDelayMinutes.round1(),
            turns                 = best.turns,
            difficulty            = best.difficulty.round1(),
            generatedAt           = System.currentTimeMillis(),
            trainingDay           = trainingDay
        )
    }

    /**
     * Persists [route] (with [instructorId] stamped) to Firestore.
     * Overwrites any previously active route.
     */
    suspend fun saveActiveRoute(route: RouteData, instructorId: String): Result<Unit> =
        runCatching {
            db.document(ACTIVE_ROUTE_PATH)
                .set(route.copy(instructorId = instructorId))
                .await()
        }

    /**
     * Returns a cold [Flow] that emits the active route whenever Firestore updates
     * it (real-time snapshot listener). Emits [Result.success](null) if no route
     * has been generated yet, or [Result.failure] on a listener error.
     *
     * The listener is automatically removed when the collecting coroutine is cancelled.
     */
    fun observeActiveRoute(): Flow<Result<RouteData?>> = callbackFlow {
        val listener = db.document(ACTIVE_ROUTE_PATH)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val route = if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(RouteData::class.java)
                } else null
                trySend(Result.success(route))
            }
        awaitClose { listener.remove() }
    }
}
