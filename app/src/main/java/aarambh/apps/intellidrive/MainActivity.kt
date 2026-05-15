package aarambh.apps.intellidrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import aarambh.apps.intellidrive.ui.navigation.AppNavGraph
import aarambh.apps.intellidrive.ui.navigation.Screen
import aarambh.apps.intellidrive.ui.theme.IntelliDriveTheme
import aarambh.apps.intellidrive.ui.viewmodel.AuthUiState
import aarambh.apps.intellidrive.ui.viewmodel.AuthViewModel

import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    // Request permission launcher for notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission granted/rejected
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            IntelliDriveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authViewModel: AuthViewModel = viewModel()
                    val uiState by authViewModel.uiState.collectAsState()
                    val navController = rememberNavController()

                    // Attempt to restore an existing Firebase session on first launch
                    LaunchedEffect(Unit) {
                        authViewModel.checkExistingSession()
                    }

                    // Determine the correct start destination after session check
                    val startDestination = when {
                        uiState is AuthUiState.Success -> {
                            val role = (uiState as AuthUiState.Success).user.role
                            when (role) {
                                "parent"     -> Screen.ParentDashboard.route
                                "instructor" -> Screen.InstructorDashboard.route
                                else         -> Screen.StudentDashboard.route
                            }
                        }
                        else -> Screen.Login.route
                    }

                    // Only render the nav graph once we know where to start
                    // (avoids a brief flash of Login when a session already exists)
                    val isReady = uiState !is AuthUiState.Loading
                    if (isReady) {
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination,
                            authViewModel = authViewModel
                        )
                    }
                }
            }
        }
    }
}