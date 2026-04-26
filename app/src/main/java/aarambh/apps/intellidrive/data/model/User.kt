package aarambh.apps.intellidrive.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "",   // "student" | "parent"
    val trainingDay: Int = 1,
    val childId: String = "" // only populated for parents
)
