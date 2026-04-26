package aarambh.apps.intellidrive.data.repository

import aarambh.apps.intellidrive.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Handles all Firebase Auth and Firestore operations.
 * Returns [Result] so callers can handle success/failure cleanly.
 */
class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ── Auth state ──────────────────────────────────────────────────────────

    /** Returns the currently signed-in Firebase UID, or null if not signed in. */
    val currentUserId: String?
        get() = auth.currentUser?.uid

    val isLoggedIn: Boolean
        get() = auth.currentUser != null

    // ── Registration ────────────────────────────────────────────────────────

    /**
     * Creates a new account with email/password, then writes the user document
     * to Firestore at users/{uid}.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: String,
        childId: String = ""
    ): Result<User> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw Exception("Registration failed (no UID)")

        val user = User(
            uid = uid,
            name = name,
            email = email,
            role = role,
            trainingDay = 1,
            childId = childId
        )

        firestore.collection("users").document(uid).set(user).await()
        user
    }

    // ── Login ────────────────────────────────────────────────────────────────

    /**
     * Signs in with email/password and returns the Firestore [User] document
     * so the caller knows the role for navigation.
     */
    suspend fun login(email: String, password: String): Result<User> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("UID was null after login")
        fetchUser(uid)
    }

    // ── Fetch user ───────────────────────────────────────────────────────────

    /** Reads the Firestore user document for [uid]. */
    suspend fun fetchUser(uid: String): User {
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toObject(User::class.java)
            ?: error("User document not found for uid=$uid")
    }

    // ── Sign out ─────────────────────────────────────────────────────────────

    fun signOut() = auth.signOut()
}
