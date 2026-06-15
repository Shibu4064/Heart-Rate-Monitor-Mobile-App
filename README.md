# 🩺 PulseSync: Real-Time BLE Heart Rate Monitor & Alert Engine

PulseSync is a modern, high-performance Android application built with **Jetpack Compose** and **Material Design 3 (Elegant Dark Theme)**. It monitors, visualizes, and monitors cardiovascular telemetry via **Bluetooth Low Energy (BLE)** wearable sensors (chest straps, smart rings, health watches) and alerts you immediately when thresholds are breached.

To make the app zero-friction during debugging, deployment, and simulation, it includes an integrated **High-Fidelity Virtual Cardiac Sensor Simulator** so you can test all features—such as exercise loops and threshold breaches—without needing a physical BLE strap.

---

## 🎨 Visual Preview & Design

The app is styled using a handcrafted **Elegant Dark** theme that maximizes reading accessibility during high-intensity workouts:
- **Beating Heart Unit:** Automatically matches your current heartbeat rhythm using dynamic spring-physics scaling animations.
- **Cardio Streamer Wave:** A custom-drawn canvas graph delivering real-time rhythmic wave propagation.
- **Live Zone Distribution:** Segregates cardiovascular performance across 5 Material 3 dynamically color-coded zones:
  - 🟢 **Recovery (RESTING)** (< 60 BPM)
  - 🔵 **Warm Up (WARM-UP)** (60% - 70% Max HR)
  - 🟡 **Fat Burning (FAT-BURN)** (70% - 80% Max HR)
  - 🟠 **Aerobic (CARDIO)** (80% - 85% Max HR)
  - 🔴 **Anaerobic (PEAK)** (85%+ Max HR)

---

## 🚀 Key Features

- **Automated BLE Scanning**: Locates wellness peripherals in the vicinity advertising the generic Heart Rate GATT Service (`0x180D`).
- **Real-Time Threshold Alert Engine**:
  - Define custom upper and lower heart rate safety bounds.
  - Generates beautiful, red-themed inside-app **Safety Breach Banners**.
  - Triggers **Android Push Notifications** securely in the background, complete with vibration alarms.
- **Integrated High-Fidelity Wear Simulator (`PulseSim Band v5`)**: Pre-bundled simulated wearable that models biological active exercise waveforms (climbing, recovery, noise fluctuation).
- **Physiological Customization**: Inputs for age and weight which automatically dynamically compute your personalized maximum heart rate limit (`220 - Age`) and calculate accurate, real-time metabolic active calorie burns.
- **Cardiovascular Metrics Summary**:
  - Elapsed Workout Duration
  - Cumulative Calories Burned (kcal)
  - Active Average BPM
  - Extreme Min/Max cardiac values

---

## 🛠️ Technology Stack

- **UI Framework:** Jetpack Compose (100% Kotlin-first declaration).
- **Design System:** Material Design 3 (M3).
- **BLE Stack:** Android SDK Bluetooth GATT API with background-safe service notification bindings.
- **State Architecture:** Clean MVVM with `StateFlow` reactive collection.
- **Alarms:** `Android NotificationManager` (POST_NOTIFICATIONS channels on Android 13+).

---

## 📥 Getting Started & Requirements

### Technical Requirements
- Android SDK 24 (Android 7.0) minimum.
- SDK 36 (Android 15) Target SDK.
- BLE-compatible hardware (optional if utilizing simulator mode).

### BLE Permissions Declared
On Android 12+ (API 31+), the app targets friendly permission profiles omitting spatial scanning to preserve user privacy:
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

---

## 🧑‍💻 Development instructions

1. Clone this repository structure into Android Studio.
2. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run unit checks or screenshot regressions:
   ```bash
   ./gradlew test
   ```

<img width="422" height="685" alt="Screenshot 2026-06-15 at 8 59 14 PM" src="https://github.com/user-attachments/assets/d405e1dc-072d-4148-879f-5f4884e47b25" />
<img width="422" height="685" alt="Screenshot 2026-06-15 at 8 59 29 PM" src="https://github.com/user-attachments/assets/b85438a2-7d72-4f23-a2f6-4409278bbecc" />
<img width="422" height="685" alt="Screenshot 2026-06-15 at 8 59 59 PM" src="https://github.com/user-attachments/assets/6758a3c5-e9cb-4962-8376-84abd0f501c6" />
<img width="422" height="685" alt="Screenshot 2026-06-15 at 9 00 14 PM" src="https://github.com/user-attachments/assets/3aee43e1-c124-48bb-8269-6b7856e92081" />
<img width="422" height="685" alt="Screenshot 2026-06-15 at 9 00 40 PM" src="https://github.com/user-attachments/assets/c0138a7e-7dcc-4b00-9d49-9f5c554c2532" />
<img width="422" height="685" alt="Screenshot 2026-06-15 at 9 01 23 PM" src="https://github.com/user-attachments/assets/56a91c06-c157-40a5-8733-76ffc0ae88ef" />














