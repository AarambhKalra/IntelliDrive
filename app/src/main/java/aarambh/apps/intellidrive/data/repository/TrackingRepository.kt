package aarambh.apps.intellidrive.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import aarambh.apps.intellidrive.data.model.LiveLocation

class TrackingRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    fun getLiveLocation(sessionId: String): Flow<LiveLocation?> = callbackFlow {
        val ref = database.getReference("sessions").child(sessionId).child("liveLocation")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.getValue(LiveLocation::class.java)
                trySend(location)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
