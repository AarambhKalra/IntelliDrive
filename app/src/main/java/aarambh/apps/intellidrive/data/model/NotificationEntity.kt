package aarambh.apps.intellidrive.data.model

data class NotificationEntity(
    val notifId: String = "",
    val recipientUid: String = "",
    val sessionId: String = "",
    val eventType: String = "",
    val message: String = "",
    val sentAt: Long = 0L,
    val isRead: Boolean = false
)
