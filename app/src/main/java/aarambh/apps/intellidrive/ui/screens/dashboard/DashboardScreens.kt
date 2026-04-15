package aarambh.apps.intellidrive.ui.screens.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import aarambh.apps.intellidrive.data.model.RouteData
import aarambh.apps.intellidrive.ui.viewmodel.AuthUiState
import aarambh.apps.intellidrive.ui.viewmodel.AuthViewModel
import aarambh.apps.intellidrive.ui.viewmodel.MapViewModel
import aarambh.apps.intellidrive.ui.viewmodel.RouteUiState
import aarambh.apps.intellidrive.ui.viewmodel.RouteViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import kotlin.math.ceil

// ── Constants ─────────────────────────────────────────────────────────────────

private val DEFAULT_LATLNG = LatLng(20.5937, 78.9629) // centre of India

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private fun hasLocationPermission(ctx: android.content.Context): Boolean =
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

// ── Practice loop helpers ─────────────────────────────────────────────────────

/** Every student must practice this total km per session. */
private const val DAILY_PRACTICE_KM = 15.0

/**
 * Number of times the loop must be repeated to reach [DAILY_PRACTICE_KM].
 * Capped at 10 to stay within Google Maps' waypoint limit.
 */
private fun lapsNeeded(distanceKm: Double): Int =
    ceil(DAILY_PRACTICE_KM / distanceKm.coerceAtLeast(0.1))
        .toInt().coerceIn(1, 10)

/**
 * Builds a Google Maps directions URL for N full laps of [route].
 *
 * Layout for N laps:
 *   origin  → [midOut → turnAround → midRet → zoneCenter] × (N-1) → midOut → turnAround → midRet → destination (= zoneCenter)
 *
 * Passing midpoints + the turnaround as mandatory waypoints forces Maps to use the same
 * road the instructor evaluated, rather than recalculating a new shortest path.
 */
private fun buildPracticeLoopUri(route: RouteData): android.net.Uri {
    val laps       = lapsNeeded(route.distanceKm)
    val zoneCenter = "${route.destLat},${route.destLng}"
    val turnaround = "${route.turnAroundLat},${route.turnAroundLng}"

    // Decode polylines to find exact midpoints of the outbound and return legs
    val outPoints = aarambh.apps.intellidrive.util.decodePolyline(route.encodedPolyline)
    val retPoints = aarambh.apps.intellidrive.util.decodePolyline(route.encodedPolylineReturn)

    val midOut = outPoints.getOrNull(outPoints.size / 2)?.let { "${it.latitude},${it.longitude}" }
    val midRet = retPoints.getOrNull(retPoints.size / 2)?.let { "${it.latitude},${it.longitude}" }

    // Build the waypoint list
    var wpList = mutableListOf<String>()
    for (i in 1..laps) {
        if (midOut != null) wpList.add(midOut)
        wpList.add(turnaround)
        if (midRet != null) wpList.add(midRet)
        
        if (i < laps) {
            wpList.add(zoneCenter)
        }
    }

    // Google Maps Android intents technically support ~24 waypoints + origin/dest.
    // If a tiny loop requires too many laps, the URL gets too big.
    // Fall back to just the turnarounds if we exceed 20 waypoints.
    if (wpList.size > 20) {
        wpList.clear()
        for (i in 1..laps) {
            wpList.add(turnaround)
            if (i < laps) wpList.add(zoneCenter)
        }
    }

    return android.net.Uri.Builder()
        .scheme("https")
        .authority("www.google.com")
        .path("/maps/dir/")
        .appendQueryParameter("api", "1")
        .appendQueryParameter("origin", zoneCenter)
        .appendQueryParameter("destination", zoneCenter)
        .appendQueryParameter("waypoints", wpList.joinToString("|"))
        .appendQueryParameter("travelmode", "driving")
        .build()
}

// ── Shared map composable ─────────────────────────────────────────────────────

/**
 * Full-lifecycle map composable:
 *  1. Requests location permission on first composition
 *  2. Fetches and shows the current location marker
 *  3. Draws a [Polyline] + destination marker when [routePoints] is non-empty
 *  4. Auto-pans camera to fit the route bounds, or to the user's location
 *
 * @param overlay Composable lambda drawn on top of the map (buttons, cards…)
 */
@Composable
private fun MapWithLocation(
    mapViewModel: MapViewModel,
    modifier: Modifier = Modifier,
    routePoints: List<LatLng> = emptyList(),
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val currentLocation by mapViewModel.currentLocation.collectAsState()
    val isLoadingLocation by mapViewModel.isLoadingLocation.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DEFAULT_LATLNG, 4f)
    }

    // Animate camera to fit route, or to user location if no route yet
    LaunchedEffect(currentLocation, routePoints) {
        if (routePoints.size >= 2) {
            try {
                val bounds = routePoints
                    .fold(LatLngBounds.Builder()) { b, p -> b.include(p) }
                    .build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 120), 1000
                )
            } catch (_: Exception) {
                currentLocation?.let {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 13f), 1000)
                }
            }
        } else {
            currentLocation?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f), 1000)
            }
        }
    }

    // Request permission → fetch location
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) mapViewModel.fetchLocation()
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) mapViewModel.fetchLocation()
        else permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isTrafficEnabled = routePoints.isNotEmpty())
        ) {
            // Current location marker
            currentLocation?.let {
                Marker(state = MarkerState(it), title = "Your Location")
            }

            // Practice loop polyline
            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints,
                    color = Color(0xFF1565C0),   // deep blue
                    width = 10f
                )
                // Mark the practice zone starting point
                Marker(
                    state = MarkerState(routePoints.first()),
                    title = "Practice Zone Start",
                    snippet = "Navigate here first"
                )
            }
        }

        // Location loading indicator (top-centre)
        if (isLoadingLocation) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                strokeWidth = 2.dp
            )
        }

        overlay()
    }
}

// ── Route info card ───────────────────────────────────────────────────────────

@Composable
private fun RouteInfoCard(
    route: RouteData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "Practice Zone",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Text(
                "Drive to the zone start, then follow the loop",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            RouteInfoRow(icon = "📅", label = "Training Day",    value = "Day ${route.trainingDay}")
            RouteInfoRow(icon = "📏", label = "Loop Distance",   value = "%.1f km".format(route.distanceKm))
            RouteInfoRow(icon = "🔁", label = "Daily laps",      value = "${lapsNeeded(route.distanceKm)} laps ≈ ${DAILY_PRACTICE_KM.toInt()} km")
            RouteInfoRow(icon = "⏱",  label = "Duration/lap",   value = "%.0f min".format(route.durationMinutes))
            RouteInfoRow(icon = "🚦", label = "Traffic delay",   value = "%.0f min".format(route.trafficDelayMinutes))
            RouteInfoRow(icon = "↩",  label = "Turns",          value = "${route.turns}")
            RouteInfoRow(icon = "⚡", label = "Difficulty",     value = "%.1f".format(route.difficulty))
        }
    }
}

@Composable
private fun RouteInfoRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$icon  $label", style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

// ── No route placeholder ──────────────────────────────────────────────────────

@Composable
private fun NoRouteCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🗺️", fontSize = 36.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Waiting for instructor\nto generate a route…",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Welcome header ────────────────────────────────────────────────────────────

@Composable
private fun WelcomeHeader(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Welcome, $name!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

// ── Student Dashboard ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: AuthViewModel,
    mapViewModel: MapViewModel = viewModel(),
    routeViewModel: RouteViewModel = viewModel(),
    onSignOut: () -> Unit
) {
    val uiState   by viewModel.uiState.collectAsState()
    val routeState by routeViewModel.routeState.collectAsState()
    val userName  = (uiState as? AuthUiState.Success)?.user?.name ?: "Student"

    // Sign-out navigation
    LaunchedEffect(uiState) { if (uiState is AuthUiState.Idle) onSignOut() }

    // Subscribe to real-time route updates so the map refreshes automatically
    // whenever the instructor generates a new route while this screen is open.
    LaunchedEffect(Unit) { routeViewModel.startObservingStudentRoute() }

    val routePoints = (routeState as? RouteUiState.RouteReady)?.polylinePoints ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Dashboard") },
                actions = { TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            WelcomeHeader(name = userName)

            MapWithLocation(
                mapViewModel = mapViewModel,
                modifier = Modifier.weight(1f),
                routePoints = routePoints,
                overlay = {
                    when (routeState) {
                        is RouteUiState.RouteReady -> {
                            val route = (routeState as RouteUiState.RouteReady).route
                            val context = LocalContext.current
                            RouteInfoCard(
                                route = route,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                            // Two-button layout at the bottom ─────────────────
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        // Step 2: start the multi-lap practice loop
                                        // Passes turnaround as a mandatory waypoint so
                                        // Google Maps follows the evaluated road
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            buildPracticeLoopUri(route)
                                        )
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Text("▶  Start Practice Loop (${lapsNeeded(route.distanceKm)} laps)")
                                }
                                OutlinedButton(
                                    onClick = {
                                        // Step 1: navigate to the practice zone
                                        val uri = "google.navigation:q=${route.destLat},${route.destLng}"
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(uri)
                                        )
                                        intent.setPackage("com.google.android.apps.maps")
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text("📍  Navigate to Practice Zone")
                                }
                            }
                        }
                        is RouteUiState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        is RouteUiState.Idle -> {
                            NoRouteCard(modifier = Modifier.align(Alignment.Center))
                        }
                        is RouteUiState.Error -> {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = (routeState as RouteUiState.Error).message,
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

// ── Instructor Dashboard ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorDashboardScreen(
    viewModel: AuthViewModel,
    mapViewModel: MapViewModel = viewModel(),
    routeViewModel: RouteViewModel = viewModel(),
    onSignOut: () -> Unit
) {
    val uiState    by viewModel.uiState.collectAsState()
    val routeState by routeViewModel.routeState.collectAsState()
    val userName   = (uiState as? AuthUiState.Success)?.user?.name ?: "Instructor"
    val instructorId = (uiState as? AuthUiState.Success)?.user?.uid ?: ""

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) { if (uiState is AuthUiState.Idle) onSignOut() }

    // Show route-generation errors in the snackbar
    LaunchedEffect(routeState) {
        if (routeState is RouteUiState.Error) {
            snackbarHostState.showSnackbar(
                message = (routeState as RouteUiState.Error).message,
                duration = SnackbarDuration.Long
            )
            routeViewModel.resetRoute()
        }
    }

    // Show non-fatal Firestore save warnings (route shown locally but not synced)
    LaunchedEffect(Unit) {
        routeViewModel.saveWarning.collect { warning ->
            snackbarHostState.showSnackbar(
                message = warning,
                duration = SnackbarDuration.Long
            )
        }
    }

    val routePoints = (routeState as? RouteUiState.RouteReady)?.polylinePoints ?: emptyList()
    val isGenerating = routeState is RouteUiState.Loading

    var trainingDay by remember { mutableStateOf(1) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Instructor Dashboard") },
                actions = { TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            WelcomeHeader(name = userName)

            MapWithLocation(
                mapViewModel = mapViewModel,
                modifier = Modifier.weight(1f),
                routePoints = routePoints,
                overlay = {
                    // Route info card (top)
                    if (routeState is RouteUiState.RouteReady) {
                        RouteInfoCard(
                            route = (routeState as RouteUiState.RouteReady).route,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }

                    // Loading spinner (centre, during generation)
                    if (isGenerating) {
                        Card(
                            modifier = Modifier.align(Alignment.Center),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(0.92f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Searching for practice zone…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // Bottom Controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Training Day selector card (shown before generation)
                        if (routeState !is RouteUiState.RouteReady && !isGenerating) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        "Zones are searched 1–10 km from your location.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Training Day: $trainingDay", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { if (trainingDay > 1) trainingDay-- }) {
                                                Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                            }
                                            Text("$trainingDay", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            IconButton(onClick = { if (trainingDay < 30) trainingDay++ }) {
                                                Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Two-button layout — Navigate to Zone + Start Loop
                        if (routeState is RouteUiState.RouteReady) {
                            val route = (routeState as RouteUiState.RouteReady).route
                            Button(
                                onClick = {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        buildPracticeLoopUri(route)
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp).height(50.dp)
                            ) {
                                Text("▶  Start Practice Loop (${lapsNeeded(route.distanceKm)} laps)")
                            }
                            OutlinedButton(
                                onClick = {
                                    val uri = "google.navigation:q=${route.destLat},${route.destLng}"
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(uri)
                                    )
                                    intent.setPackage("com.google.android.apps.maps")
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp)
                            ) {
                                Text("📍  Navigate to Practice Zone")
                            }
                        }

                        // Generate Practice Zone button
                        Button(
                            onClick = {
                                val location = mapViewModel.currentLocation.value
                                if (location == null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Waiting for GPS location…")
                                    }
                                } else {
                                    routeViewModel.generateAndSaveRoute(instructorId, location, true, trainingDay)
                                }
                            },
                            enabled = !isGenerating,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text(
                                if (isGenerating) "Searching for practice zone…"
                                else if (routeState is RouteUiState.RouteReady) "Find New Practice Zone"
                                else "Find Practice Zone"
                            )
                        }
                    }
                }
            )
        }
    }
}

// ── Parent Dashboard ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: AuthViewModel,
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val userName = (uiState as? AuthUiState.Success)?.user?.name ?: "Parent"

    LaunchedEffect(uiState) { if (uiState is AuthUiState.Idle) onSignOut() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parent Dashboard") },
                actions = { TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            WelcomeHeader(name = userName)

            // Live tracking is not yet implemented — show a placeholder instead
            // of an idle GoogleMap so we don't waste resources or request permissions.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📍", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Live Tracking",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Coming Soon",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "You'll be able to follow your student's\ndrive in real time here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
