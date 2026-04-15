# IntelliDrive 🚗

IntelliDrive is an Android application designed to assist learner drivers through intelligent route generation, practice zone discovery, and real-time drive session tracking. With role-based access for both Students and Instructors, IntelliDrive creates a structured and progressive learning environment for driving practice.

## ✨ Key Features

* **Role-Based Dashboards:** Distinct interfaces and features tailored for Students and Instructors.
* **Intelligent Route Generation:** Dynamically generates routes via the Google Directions API, factoring in distance, duration, and traffic.
* **Practice Zone Discovery:** Identifies suitable practice areas within a 1-10km radius of the user's location, allowing for progressive difficulty based on the training day.
* **Real-time Location Tracking:** Uses Google Maps SDK to display the user's current location, track drive progress, and visualize route polylines.
* **Progress Tracking & Synchronization:** Saves drive sessions, routes, and performance data to Firebase Firestore, enabling seamless access for both students and monitoring instructors.
* **Secure Authentication:** User authentication and session management powered by Firebase Auth.

## 🛠️ Tech Stack & Architecture

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Modern, declarative UI)
* **Architecture:** MVVM (Model-View-ViewModel) pattern
* **Backend / Database:** Firebase Authentication, Cloud Firestore
* **Maps & Routing:** Google Maps SDK for Android, Google Directions API
* **Networking:** Retrofit + OkHttp for API calls
* **Asynchronous Programming:** Kotlin Coroutines & Flow

## 📂 Project Structure

- `data/`
  - `api/` : Retrofit interfaces and client configurations using Google Directions API.
  - `model/` : Data classes representing Domain entities like User, DirectionsModels, etc.
  - `repository/` : Repositories (e.g., `RouteRepository`) handling data operations between APIs, Firebase, and ViewModels.
- `ui/`
  - `screens/` : Jetpack Compose UI screens (Authentication, Map, Dashboards).
  - `viewmodel/` : ViewModels (e.g., `MapViewModel`) bridging UI composables and Data repositories, maintaining app state.
- `utils/` : Helper classes and extension functions.

## 🚀 Getting Started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- Java JDK 17+
- A Google Cloud Platform (GCP) Account with an active project, billing enabled, and following APIs activated:
  - Maps SDK for Android
  - Directions API
- A Firebase Project configured for Android

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone <repository_url>
   cd IntelliDrive
   ```

2. **Add API Keys:**
   Create or open `local.properties` in your project root and add your Google Maps API Key:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key_here
   ```

3. **Configure Firebase:**
   - Go to your Firebase Console and add a new Android Application.
   - Enter your application package name (`aarambh.apps.intellidrive`).
   - Download the `google-services.json` file.
   - Place this file in the `app/` directory of your project.

4. **Build & Run:**
   - Open the project in Android Studio.
   - Run a Gradle sync to download all dependencies.
   - Select an emulator or connected physical device and click Run.

## 📱 Role Workflows

**Student Mode:**
- Students can view their assigned training day, generate or discover intelligent practice routes tailored to their progression, and track their position live on the map. Drive history and route performance are synced with Firebase.

**Instructor Mode:**
- Instructors have access to a broader dashboard where they can track student progress, monitor live or completed sessions, and review generated routes.

## 📝 License
This project is for educational and learning purposes.
