package aarambh.apps.intellidrive.ui.screens.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import aarambh.apps.intellidrive.ui.viewmodel.TrackingUiState
import aarambh.apps.intellidrive.ui.viewmodel.TrackingViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ParentTrackingScreen(
    learnerId: String,
    viewModel: TrackingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(learnerId) {
        viewModel.startTracking(learnerId)
    }

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is TrackingUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is TrackingUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is TrackingUiState.Success -> {
                    if (state.location != null) {
                        val latLng = LatLng(state.location.latitude, state.location.longitude)
                        val cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(latLng, 15f)
                        }
                        
                        LaunchedEffect(latLng) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                        }

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState
                        ) {
                            Marker(
                                state = MarkerState(position = latLng),
                                title = "Learner Location",
                                snippet = "Speed: ${"%.1f".format(state.location.speed * 3.6)} km/h"
                            )
                        }

                        // Info Overlay
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (state.hasActiveSession) "Live Tracking" else "Last Known Location",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (state.hasActiveSession) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Speed: ${"%.1f".format(state.location.speed * 3.6)} km/h")
                                val date = Date(state.location.timestamp)
                                val format = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                                Text(text = "Last Updated: ${format.format(date)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text("No location data available.", modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}
