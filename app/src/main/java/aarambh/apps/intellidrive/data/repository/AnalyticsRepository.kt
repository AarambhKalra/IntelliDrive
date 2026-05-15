package aarambh.apps.intellidrive.data.repository

import aarambh.apps.intellidrive.data.model.DriveSession
import aarambh.apps.intellidrive.data.model.EventEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class AnalyticsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getRecentSessions(learnerId: String, limit: Long = 10): Result<List<DriveSession>> {
        return try {
            val snapshot = firestore.collection("sessions")
                .whereEqualTo("studentId", learnerId) // Note: using studentId based on DriveSession entity
                .get()
                .await()
            val sessions = snapshot.toObjects(DriveSession::class.java)
                .sortedByDescending { it.endTime }
                .take(limit.toInt())
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentEvents(learnerId: String, limit: Long = 50): Result<List<EventEntity>> {
        return try {
            val snapshot = firestore.collection("events")
                .whereEqualTo("learnerId", learnerId)
                .get()
                .await()
            val events = snapshot.toObjects(EventEntity::class.java)
                .sortedByDescending { it.timestamp }
                .take(limit.toInt())
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
