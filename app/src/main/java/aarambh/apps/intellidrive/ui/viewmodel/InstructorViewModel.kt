package aarambh.apps.intellidrive.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import aarambh.apps.intellidrive.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InstructorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()

    private val _students = MutableStateFlow<List<User>>(emptyList())
    val students: StateFlow<List<User>> = _students.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun fetchMyStudents(instructorId: String) {
        if (instructorId.isEmpty()) return
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users")
                    .whereEqualTo("instructorId", instructorId)
                    .get()
                    .await()
                
                val studentList = snapshot.toObjects(User::class.java)
                _students.value = studentList
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to fetch students: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addStudentToInstructor(instructorId: String, studentEmail: String) {
        if (instructorId.isEmpty() || studentEmail.isEmpty()) return
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val emailToSearch = studentEmail.trim().lowercase()
                // Find student by email (case-insensitive search if we assume they are stored lowercase)
                // Actually, let's just find the user and then check role in code to be safer
                val snapshot = db.collection("users")
                    .whereEqualTo("email", emailToSearch)
                    .get()
                    .await()
                
                if (snapshot.isEmpty) {
                    _error.value = "Student with email $studentEmail not found"
                } else {
                    val studentDoc = snapshot.documents.first()
                    val role = studentDoc.getString("role") ?: ""
                    if (role != "student") {
                        _error.value = "User found but is not a student"
                    } else {
                        studentDoc.reference.update("instructorId", instructorId).await()
                        fetchMyStudents(instructorId)
                        _error.value = null
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to add student: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
