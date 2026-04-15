package aarambh.apps.intellidrive.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import aarambh.apps.intellidrive.BuildConfig
import aarambh.apps.intellidrive.data.model.RouteData
import aarambh.apps.intellidrive.data.repository.RouteRepository
import aarambh.apps.intellidrive.util.decodePolyline
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed interface RouteUiState {
    data object Idle    : RouteUiState
    data object Loading : RouteUiState

    data class RouteReady(
        val route: RouteData,
        /** Decoded LatLng list for the full practice loop (outbound + return). */
        val polylinePoints: List<LatLng>
    ) : RouteUiState

    data class Error(val message: String) : RouteUiState
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RouteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RouteRepository(BuildConfig.MAPS_API_KEY)

    private val _routeState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
    val routeState: StateFlow<RouteUiState> = _routeState.asStateFlow()

    /**
     * One-shot events for non-fatal Firestore save failures.
     * Instructor dashboard collects this to show a non-blocking warning snackbar
     * while still displaying the locally-generated route.
     */
    private val _saveWarning = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val saveWarning: SharedFlow<String> = _saveWarning.asSharedFlow()

    /** Active Firestore snapshot listener job (student side). */
    private var studentObserverJob: Job? = null

    // ── Training-day progression ──────────────────────────────────────────────

    /**
     * Returns the **practice loop size** range (km) appropriate for [day].
     *
     * This now governs the size of the circuit WITHIN the practice zone —
     * NOT the distance the student drives from home. The zone itself is always
     * searched at 5–20 km from the user's location regardless of the day.
     *
     *   Day  1–5  →  1–3 km   (tiny loop, empty roads, parking lots)
     *   Day  6–10 →  3–6 km   (quiet residential / industrial)
     *   Day 11–15 →  6–10 km  (mixed suburban roads)
     *   Day 16+   →  10–15 km (full urban / arterial roads)
     */
    fun getDistanceRangeForDay(day: Int): Pair<Double, Double> = when {
        day in 1..5   -> 1.0 to 3.0
        day in 6..10  -> 3.0 to 6.0
        day in 11..15 -> 6.0 to 10.0
        day > 15      -> 10.0 to 15.0
        else          -> 1.0 to 3.0  // fallback for day < 1
    }

    /**
     * Returns the difficulty target for zone selection.
     *
     * A **very low target** for early days causes [minByOrNull] to pick the
     * zone with the absolute lowest observed traffic and turn complexity —
     * the quietest, safest practice area available.
     *
     * A **high target** for advanced days selects zones with busier roads and
     * more complex manoeuvres, challenging the student progressively.
     *
     *   Day  1–5  → target  5  (find the absolute quietest zone)
     *   Day  6–10 → target 20
     *   Day 11–15 → target 40
     *   Day 16+   → target 70  (complex urban zones)
     */
    private fun targetDifficultyForDay(maxKm: Double): Double = when {
        maxKm <= 3.0  ->  5.0
        maxKm <= 6.0  -> 20.0
        maxKm <= 10.0 -> 40.0
        else          -> 70.0
    }

    // ── Instructor ────────────────────────────────────────────────────────────

    /**
     * Finds the best practice zone near [origin] for [trainingDay], generates
     * a loop route within it, saves it to Firestore under [instructorId], and
     * transitions to [RouteUiState.RouteReady].
     *
     * If the Firestore save fails (offline, quota, etc.) the route is still shown
     * locally and a warning is emitted to [saveWarning].
     */
    fun generateAndSaveRoute(
        instructorId: String,
        origin: LatLng,
        isLoop: Boolean = true,
        trainingDay: Int = 1
    ) {
        viewModelScope.launch {
            _routeState.value = RouteUiState.Loading

            val (minKm, maxKm)   = getDistanceRangeForDay(trainingDay)
            val targetDifficulty = targetDifficultyForDay(maxKm)

            repository.generateBestRoute(
                currentLocation  = origin,
                isLoop           = true,            // zone routes are always loops
                minKm            = minKm,
                maxKm            = maxKm,
                trainingDay      = trainingDay,
                targetDifficulty = targetDifficulty
            )
                .onSuccess { route ->
                    // Persist to Firestore — warn the instructor if it fails but keep
                    // the route visible locally
                    repository.saveActiveRoute(route, instructorId)
                        .onFailure { e ->
                            val msg = e.message ?: "Unknown error"
                            _saveWarning.tryEmit(
                                "Route shown locally but failed to sync to students: $msg"
                            )
                            android.util.Log.w("RouteViewModel", "Firestore save failed: $msg")
                        }

                    val outboundPoints = decodePolyline(route.encodedPolyline)
                    val returnPoints   = if (route.encodedPolylineReturn.isNotEmpty()) {
                        decodePolyline(route.encodedPolylineReturn)
                    } else emptyList()

                    _routeState.value = RouteUiState.RouteReady(
                        route, outboundPoints + returnPoints
                    )
                }
                .onFailure { e ->
                    _routeState.value = RouteUiState.Error(
                        e.message ?: "Route generation failed"
                    )
                }
        }
    }

    // ── Student ───────────────────────────────────────────────────────────────

    /**
     * Starts a real-time Firestore listener for the active route.
     * The student's map refreshes automatically whenever the instructor generates
     * a new practice zone. Idempotent — calling it while already active is a no-op.
     */
    fun startObservingStudentRoute() {
        if (studentObserverJob?.isActive == true) return

        _routeState.value = RouteUiState.Loading

        studentObserverJob = viewModelScope.launch {
            repository.observeActiveRoute().collect { result ->
                result
                    .onSuccess { route ->
                        if (route != null && route.encodedPolyline.isNotEmpty()) {
                            val outbound  = decodePolyline(route.encodedPolyline)
                            val returnPts = if (route.encodedPolylineReturn.isNotEmpty()) {
                                decodePolyline(route.encodedPolylineReturn)
                            } else emptyList()
                            _routeState.value = RouteUiState.RouteReady(
                                route, outbound + returnPts
                            )
                        } else {
                            _routeState.value = RouteUiState.Idle
                        }
                    }
                    .onFailure { e ->
                        _routeState.value = RouteUiState.Error(
                            e.message ?: "Failed to load route"
                        )
                    }
            }
        }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    fun resetRoute() {
        studentObserverJob?.cancel()
        studentObserverJob = null
        _routeState.value = RouteUiState.Idle
    }
}
