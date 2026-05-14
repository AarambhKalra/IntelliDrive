package aarambh.apps.intellidrive.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val age: Int = 0,
    val role: String = "",   // "student" | "parent" | "instructor"
    val trainingDay: Int = 1,
    val childId: String = "", // only populated for parents
    val instructorId: String = "" // only populated for students
)
