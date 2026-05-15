package aarambh.apps.intellidrive.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import aarambh.apps.intellidrive.MainActivity
import aarambh.apps.intellidrive.util.NotificationHelper
import aarambh.apps.intellidrive.util.SessionManager

class SessionForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var sessionManager: SessionManager? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val EXTRA_LEARNER_ID = "EXTRA_LEARNER_ID"
        
        // This would typically come from an Intent extra or database, but passing a large polyline via intent is not ideal
        // In a real scenario, you'd fetch the route points here or pass them properly
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    sessionManager?.onLocationUpdated(location)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                val learnerId = intent.getStringExtra(EXTRA_LEARNER_ID) ?: return START_NOT_STICKY
                
                startForegroundService(sessionId)
                
                sessionManager = SessionManager(this, sessionId, learnerId, emptyList()) // Using empty list for now
                sessionManager?.start()

                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                    .setMinUpdateIntervalMillis(1000L)
                    .build()
                
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                sessionManager?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService(sessionId: String) {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val notification: Notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID_SESSION)
            .setContentTitle("IntelliDrive Session Active")
            .setContentText("Tracking your driving session...")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Replace with your icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
