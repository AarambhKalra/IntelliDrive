package aarambh.apps.intellidrive.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = ""   // "student" | "instructor" | "parent"
)
