# Water Meter Test

An Android utility for testing and configuring water meter hardware via Bluetooth Classic (SPP/BLE).

## Overview
This project facilitates field testing by providing a direct interface to send hex commands to water meters. It handles the complexities of Bluetooth RFCOMM connections, including pairing management and connection retries.

## Key Features

### 🔌 Connectivity
- **Dual-Mode Bluetooth**: Seamless support for both **Classic (SPP)** and **Low Energy (BLE)** devices.
- **Smart Connection Engine**:
  - Auto-detection of device capabilities (SPP vs BLE).
  - Robust retry logic with multiple fallback strategies (Secure, Insecure, Reflection).
  - Race-condition prevention for stable connections.

### 🛠️ Dynamic Command System
- **Remote Configuration**: Fetches available commands and permissions from a remote API.
- **Smart Payloads**:
  - **Placeholders**: Dynamic injection of values (e.g., `{MeterID}`).
  - **Validation**: Regex-based input validation per field.
  - **Transformations**: Automatic conversion of complex types (e.g., IP to Hex, Integer to 4-byte Hex).
  - **Checksums**: Auto-calculation of `CheckSum8 Modulo 256`.

### 🔐 Security & Session
- **Secure Authentication**: Token-based login system.
- **Auto-Refresh**: Permissions are automatically verified and refreshed every 3 hours.
- **Session Management**: Automatic logout on session expiry.

### 📱 Modern UI/UX
- **Material Design 3**: Clean, modern interface with Dark Mode support.
- **Edge-to-Edge**: Immersive layout handling system bars correctly.
- **Real-time Feedback**: Visual indicators for connection status and data transmission.

## Technical Details
- **Architecture**: MVVM with Repository pattern.
- **Async**: Kotlin Coroutines for non-blocking Bluetooth operations.
- **DI**: Hilt for dependency injection.
- **Compatibility**: Supports Android 12+ permissions (Scan/Connect) with backward compatibility.

## Getting Started
1. Clone the repo.
2. Open in Android Studio (Ladybug or newer recommended).
3. Build and run on a physical device (Bluetooth required).

## Author
**Pappu Singha**

## License
Internal use only.
