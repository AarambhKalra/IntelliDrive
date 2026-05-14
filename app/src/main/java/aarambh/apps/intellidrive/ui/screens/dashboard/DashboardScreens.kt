package aarambh.apps.intellidrive.ui.screens.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import aarambh.apps.intellidrive.data.model.User
import aarambh.apps.intellidrive.ui.viewmodel.AuthUiState
import aarambh.apps.intellidrive.ui.viewmodel.AuthViewModel
import aarambh.apps.intellidrive.ui.viewmodel.InstructorViewModel
import aarambh.apps.intellidrive.ui.viewmodel.MapViewModel
import aarambh.apps.intellidrive.ui.viewmodel.RouteUiState
import aarambh.apps.intellidrive.ui.viewmodel.RouteViewModel
import aarambh.apps.intellidrive.ui.viewmodel.SessionViewModel
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

private val DEFAULT_LATLNG = LatLng(20.5937, 78.9629)

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private fun hasLocationPermission(ctx: android.content.Context): Boolean =
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

// ── Practice loop helpers ─────────────────────────────────────────────────────

private const val DAILY_PRACTICE_KM = 15.0

private fun lapsNeeded(distanceKm: Double): Int =
    ceil(DAILY_PRACTICE_KM / distanceKm.coerceAtLeast(0.1))
        .toInt().coerceIn(1, 5)

private fun buildPracticeLoopUri(route: RouteData): android.net.Uri {
    val laps       = lapsNeeded(route.distanceKm)
    val zoneCenter = "${route.destLat},${route.destLng}"
    val turnaround = "${route.turnAroundLat},${route.turnAroundLng}"

    val wpList = mutableListOf<String>()
    for (i in 1..laps) {
        wpList.add(turnaround)
        if (i < laps) wpList.add(zoneCenter)
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

// ── Shared UI components ──────────────────────────────────────────────────────

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

    LaunchedEffect(currentLocation, routePoints) {
        if (routePoints.size >= 2) {
            try {
                val bounds = routePoints
                    .fold(LatLngBounds.Builder()) { b, p -> b.include(p) }
                    .build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120), 1000)
            } catch (_: Exception) {
                currentLocation?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 13f), 1000) }
            }
        } else {
            currentLocation?.let { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f), 1000) }
        }
    }

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
            currentLocation?.let { Marker(state = MarkerState(it), title = "Your Location") }
            if (routePoints.size >= 2) {
                Polyline(points = routePoints, color = Color(0xFF1565C0), width = 10f)
                Marker(state = MarkerState(routePoints.first()), title = "Practice Zone Start")
            }
        }
        if (isLoadingLocation) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp), strokeWidth = 2.dp)
        }
        overlay()
    }
}

// ── Student Dashboard ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: AuthViewModel,
    mapViewModel: MapViewModel = viewModel(),
    routeViewModel: RouteViewModel = viewModel(),
    sessionViewModel: SessionViewModel = viewModel(),
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val routeState by routeViewModel.routeState.collectAsState()
    val userName = (uiState as? AuthUiState.Success)?.user?.name ?: "Student"
    val studentId = (uiState as? AuthUiState.Success)?.user?.uid ?: ""
    val trainingDay = (uiState as? AuthUiState.Success)?.user?.trainingDay ?: 1
    val isGenerating = routeState is RouteUiState.Loading
    val snackbarHostState = remember { SnackbarHostState() }
    var dayDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) { if (uiState is AuthUiState.Idle) onSignOut() }

    LaunchedEffect(routeState) {
        if (routeState is RouteUiState.Error) {
            snackbarHostState.showSnackbar((routeState as RouteUiState.Error).message)
            routeViewModel.resetRoute()
        }
    }

    LaunchedEffect(Unit) {
        sessionViewModel.sessionCompletedEvent.collect { newDay ->
            viewModel.refreshUser()
            routeViewModel.resetRoute()
            snackbarHostState.showSnackbar("Session complete! Graduated to Day $newDay 🎉")
        }
    }

    LaunchedEffect(studentId) { if (studentId.isNotEmpty()) mapViewModel.startLiveTracking(studentId) }
    DisposableEffect(studentId) { onDispose { mapViewModel.stopLiveTracking() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Student Dashboard")
                        Text("Day $trainingDay • ID: $studentId", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = { TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            WelcomeHeader(name = userName)
            MapWithLocation(
                mapViewModel = mapViewModel,
                modifier = Modifier.weight(1f),
                routePoints = (routeState as? RouteUiState.RouteReady)?.polylinePoints ?: emptyList(),
                overlay = {
                    if (routeState is RouteUiState.RouteReady) {
                        val route = (routeState as RouteUiState.RouteReady).route
                        RouteInfoCard(route = route, modifier = Modifier.align(Alignment.TopCenter))
                    }
                    if (isGenerating) {
                        Card(modifier = Modifier.align(Alignment.Center), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(0.92f))) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Searching for practice zone…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (routeState is RouteUiState.RouteReady) {
                            val route = (routeState as RouteUiState.RouteReady).route
                            val context = LocalContext.current
                            Button(onClick = {
                                sessionViewModel.startSession(studentId, route.routeId, route.trainingDay)
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, buildPracticeLoopUri(route)))
                            }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("▶ Start Practice Loop") }
                            OutlinedButton(onClick = { sessionViewModel.completeSession(studentId) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("✅ Complete Session") }
                        }
                        OutlinedButton(onClick = { dayDropdownExpanded = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text("Current Level: Day $trainingDay (Tap to change)")
                            DropdownMenu(expanded = dayDropdownExpanded, onDismissRequest = { dayDropdownExpanded = false }) {
                                (1..30).forEach { day ->
                                    DropdownMenuItem(text = { Text("Day $day") }, onClick = { viewModel.updateTrainingDay(day); dayDropdownExpanded = false })
                                }
                            }
                        }
                        Button(onClick = {
                            val loc = mapViewModel.currentLocation.value
                            if (loc != null) routeViewModel.generateAndSaveRoute(studentId, loc, true, trainingDay)
                        }, enabled = !isGenerating, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text(if (isGenerating) "Searching..." else if (routeState is RouteUiState.RouteReady) "Find New Zone" else "Find Practice Zone")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun RouteInfoCard(route: RouteData, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)), elevation = CardDefaults.cardElevation(6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Practice Zone", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            RouteInfoRow(icon = "📅", label = "Training Day", value = "Day ${route.trainingDay}")
            RouteInfoRow(icon = "📏", label = "Loop Distance", value = "${route.distanceKm} km")
            RouteInfoRow(icon = "🔁", label = "Laps Needed", value = "${lapsNeeded(route.distanceKm)}")
            RouteInfoRow(icon = "⏱", label = "Est. Time", value = "${(route.durationMinutes * lapsNeeded(route.distanceKm)).toInt()} min")
        }
    }
}

@Composable
private fun RouteInfoRow(icon: String, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$icon $label", style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

// ── Instructor Dashboard ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorDashboardScreen(
    viewModel: AuthViewModel,
    instructorViewModel: InstructorViewModel = viewModel(),
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val instructorName = (uiState as? AuthUiState.Success)?.user?.name ?: "Instructor"
    val instructorId = (uiState as? AuthUiState.Success)?.user?.uid ?: ""
    val students by instructorViewModel.students.collectAsState()
    val isLoading by instructorViewModel.isLoading.collectAsState()
    val error by instructorViewModel.error.collectAsState()
    var showAddStudentDialog by remember { mutableStateOf(false) }

    var selectedStudent by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(uiState) { if (uiState is AuthUiState.Idle) onSignOut() }
    LaunchedEffect(instructorId) { if (instructorId.isNotEmpty()) instructorViewModel.fetchMyStudents(instructorId) }

    if (selectedStudent != null) {
        InstructorLiveTrackingScreen(
            student = selectedStudent!!,
            onBack = { selectedStudent = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Instructor Dashboard") },
                    actions = { TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") } }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddStudentDialog = true }) { Text("+", fontSize = 24.sp) }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                WelcomeHeader(name = instructorName)
                if (isLoading && students.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else if (students.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No students linked yet.", color = Color.Gray) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(students.size) { index -> 
                            StudentListItem(
                                student = students[index],
                                onClick = { selectedStudent = students[index] }
                            ) 
                        }
                    }
                }
            }
            if (showAddStudentDialog) {
                AddStudentDialog(
                    onDismiss = { showAddStudentDialog = false; instructorViewModel.clearError() },
                    onAdd = { email -> instructorViewModel.addStudentToInstructor(instructorId, email); showAddStudentDialog = false },
                    error = error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructorLiveTrackingScreen(
    student: User,
    onBack: () -> Unit,
    sessionViewModel: SessionViewModel = viewModel()
) {
    val liveLocation by sessionViewModel.liveLocation.collectAsState()

    LaunchedEffect(student.uid) {
        sessionViewModel.startObservingStudent(student.uid)
    }
    DisposableEffect(student.uid) {
        onDispose { sessionViewModel.stopObservingStudent() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking: ${student.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // Use a simple text back button if Icons.Default.ArrowBack is missing or just for speed
                        Text("←", fontSize = 24.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val loc = liveLocation
            if (loc != null) {
                val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f) }
                LaunchedEffect(loc) { cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude))) }
                Box(Modifier.weight(1f)) {
                    GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
                        Marker(state = MarkerState(LatLng(loc.latitude, loc.longitude)), title = student.name, snippet = "Speed: ${loc.speedKmh.toInt()} km/h")
                    }
                }
            } else {
                Box(Modifier.weight(1f), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No active practice session. Waiting for student to start driving...", 
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentListItem(student: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(student.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(student.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Text("Day ${student.trainingDay}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AddStudentDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit, error: String?) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Student") },
        text = {
            Column {
                Text("Enter student email to link.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onAdd(email) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Parent Dashboard ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(
    viewModel: AuthViewModel,
    sessionViewModel: SessionViewModel = viewModel(),
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val userName = (uiState as? AuthUiState.Success)?.user?.name ?: "Parent"
    val childId = (uiState as? AuthUiState.Success)?.user?.childId ?: ""
    val liveLocation by sessionViewModel.liveLocation.collectAsState()

    LaunchedEffect(uiState) { if (uiState is AuthUiState.Idle) onSignOut() }
    LaunchedEffect(childId) { if (childId.isNotEmpty()) sessionViewModel.startObservingStudent(childId) }
    DisposableEffect(childId) { onDispose { sessionViewModel.stopObservingStudent() } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Parent Dashboard") }, actions = { TextButton(onClick = { viewModel.signOut() }) { Text("Sign Out") } })
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            WelcomeHeader(name = userName)
            val loc = liveLocation
            if (loc != null) {
                val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f) }
                LaunchedEffect(loc) { cameraPositionState.animate(CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude))) }
                Box(Modifier.weight(1f)) {
                    GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
                        Marker(state = MarkerState(LatLng(loc.latitude, loc.longitude)), title = "Student Location", snippet = "Speed: ${loc.speedKmh.toInt()} km/h")
                    }
                }
            } else {
                Box(Modifier.weight(1f), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        Text(
                            "No active session found. Tracking will begin once the student starts practice.", 
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
    }
}
