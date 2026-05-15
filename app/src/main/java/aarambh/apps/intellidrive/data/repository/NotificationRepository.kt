package aarambh.apps.intellidrive.data.repository

import aarambh.apps.intellidrive.data.model.NotificationEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    private val notificationsCollection = firestore.collection("notifications")
    private val usersCollection = firestore.collection("users")

    suspend fun updateFcmToken(userId: String): Result<Unit> {
        return try {
            val token = messaging.token.await()
            usersCollection.document(userId).update("fcmToken", token).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveNotification(notification: NotificationEntity): Result<Unit> {
        return try {
            val document = if (notification.notifId.isEmpty()) {
                notificationsCollection.document()
            } else {
                notificationsCollection.document(notification.notifId)
            }
            val notifToSave = notification.copy(notifId = document.id)
            document.set(notifToSave).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNotificationsForUser(userId: String): Result<List<NotificationEntity>> {
        return try {
            val snapshot = notificationsCollection
                .whereEqualTo("recipientUid", userId)
                .get()
                .await()
            val notifications = snapshot.toObjects(NotificationEntity::class.java)
                .sortedByDescending { it.sentAt }
            Result.success(notifications)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
