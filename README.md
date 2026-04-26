# IntelliDrive 🚗

IntelliDrive is a modern Android application designed to assist learner drivers through intelligent route generation, practice zone discovery, and real-time drive session tracking. It provides a structured and progressive learning environment with specialized features for both Students and Parents.

## ✨ Key Features

*   **Role-Based Dashboards:** Distinct interfaces for Students (practice/learning) and Parents/Instructors (monitoring/oversight).
*   **Intelligent Route Generation:** Leverages Google Directions API to create practice routes based on distance, duration, and traffic conditions.
*   **Practice Zone Discovery:** Automatically identifies suitable training areas within 1-10km, tailored to the student's current training day and skill level.
*   **Real-time Location Tracking:** Integrated Google Maps SDK for live position tracking, route visualization via polylines, and session progress monitoring.
*   **Cloud Synchronization:** Persistent storage of drive sessions, route history, and user performance data using Firebase Firestore.
*   **Secure Authentication:** Robust user management and session persistence powered by Firebase Authentication.

## 🛠️ Tech Stack & Architecture

*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose (Modern, declarative UI components)
*   **Architecture:** MVVM (Model-View-ViewModel) for clean separation of concerns.
*   **Backend:** Firebase Authentication & Cloud Firestore.
*   **Networking:** Retrofit & OkHttp for Google Maps/Directions API integration.
*   **Concurrency:** Kotlin Coroutines & Flow (StateFlow/SharedFlow) for reactive UI updates.
*   **Dependency Management:** Gradle (Kotlin DSL).

## 📂 Project Structure

- `data/`
    - `api/`: Retrofit service definitions and API client configurations.
    - `model/`: Data entities (User, DriveSession, LiveLocation, RouteData, DirectionsModels).
    - `repository/`: Data source abstraction (AuthRepository, RouteRepository, SessionRepository).
- `ui/`
    - `screens/`: Jetpack Compose UI implementations (Auth, Dashboard).
    - `viewmodel/`: Business logic and UI state management (AuthViewModel, SessionViewModel).
    - `navigation/`: Navigation graph and route definitions.
    - `theme/`: App-wide styling, colors, and typography.
- `util/`: Utility classes and extension functions.

## 🚀 Getting Started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- Java JDK 17+
- Google Cloud Platform project with Maps SDK and Directions API enabled.
- Firebase project configured for Android.

### Installation & Setup

1.  **Clone the repository:**
    ```bash
    git clone <repository_url>
    cd IntelliDrive
    ```

2.  **Add API Keys:**
    Add your Google Maps API Key to `local.properties`:
    ```properties
    MAPS_API_KEY=your_google_maps_api_key_here
    ```

3.  **Configure Firebase:**
    Place your `google-services.json` file in the `app/` directory.

4.  **Build & Run:**
    Sync Gradle and run the app on an emulator or physical device.

## 📱 Workflows

**Student:**
Starts drive sessions, follows generated routes, and tracks progress across different training days.

**Parent/Instructor:**
Monitors student sessions in real-time and reviews practice history.

## 📝 License
This project is for educational and learning purposes.
