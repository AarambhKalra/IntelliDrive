package aarambh.apps.intellidrive.data.repository

import aarambh.apps.intellidrive.data.model.EventEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EventRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val eventsCollection = firestore.collection("events")

    suspend fun saveEvent(event: EventEntity): Result<Unit> {
        return try {
            val document = if (event.eventId.isEmpty()) {
                eventsCollection.document()
            } else {
                eventsCollection.document(event.eventId)
            }
            val eventToSave = event.copy(eventId = document.id)
            document.set(eventToSave).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEventsForSession(sessionId: String): Result<List<EventEntity>> {
        return try {
            val snapshot = eventsCollection
                .whereEqualTo("sessionId", sessionId)
                .get()
                .await()
            val events = snapshot.toObjects(EventEntity::class.java)
            Result.success(events)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
