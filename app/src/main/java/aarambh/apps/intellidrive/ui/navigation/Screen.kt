package aarambh.apps.intellidrive.ui.navigation

/** Type-safe navigation routes for the app. */
sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Register : Screen("register")
    data object StudentDashboard : Screen("student_dashboard")
    data object InstructorDashboard : Screen("instructor_dashboard")
    data object ParentDashboard : Screen("parent_dashboard")
}
