package aarambh.apps.intellidrive.data.repository

import aarambh.apps.intellidrive.data.model.DriveSession
import aarambh.apps.intellidrive.data.model.LiveLocation
import aarambh.apps.intellidrive.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SessionRepository {

    private val db = FirebaseFirestore.getInstance()

    // ── Live Tracking ─────────────────────────────────────────────────────────

    suspend fun updateLiveLocation(location: LiveLocation): Result<Unit> = runCatching {
        db.document("live_tracking/${location.studentId}")
            .set(location)
            .await()
    }

    fun observeLiveLocation(studentId: String): Flow<Result<LiveLocation?>> = callbackFlow {
        val listener = db.document("live_tracking/$studentId")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val location = if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(LiveLocation::class.java)
                } else null
                trySend(Result.success(location))
            }
        awaitClose { listener.remove() }
    }

    // ── Drive Sessions ────────────────────────────────────────────────────────

    suspend fun saveDriveSession(session: DriveSession): Result<Unit> = runCatching {
        // Save to sessions collection
        db.document("sessions/${session.sessionId}")
            .set(session)
            .await()
    }

    suspend fun getDriveSessions(studentId: String): Result<List<DriveSession>> = runCatching {
        val snapshot = db.collection("sessions")
            .whereEqualTo("studentId", studentId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .get()
            .await()
        snapshot.toObjects(DriveSession::class.java)
    }

    // ── Training Day Progression ──────────────────────────────────────────────

    suspend fun graduateTrainingDay(studentId: String): Result<Int> = runCatching {
        val docRef = db.document("users/$studentId")
        var newDay = 1
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentUser = snapshot.toObject(User::class.java)
            val currentDay = currentUser?.trainingDay ?: 1
            newDay = currentDay + 1
            // Make sure not to exceed a max training day if desired, e.g., 30.
            if (newDay > 30) newDay = 30
            transaction.update(docRef, "trainingDay", newDay)
        }.await()
        newDay
    }
}
