package aarambh.apps.intellidrive.services

import aarambh.apps.intellidrive.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Alert"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["message"] ?: ""

        if (message.isNotEmpty()) {
            NotificationHelper.createChannels(this)
            NotificationHelper.showNotification(this, title, message)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FirebaseFirestore.getInstance().collection("users")
                        .document(uid)
                        .update("fcmToken", token)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
