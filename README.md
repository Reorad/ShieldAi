# 🛡️ ShieldAI

> [cite_start]*"We shield you and your money"* [cite: 22, 23, 24]

## 📖 About the Project
[cite_start]Shield AI is a mobile application designed to verify messages and predominantly the links within them to prevent phishing attacks[cite: 25, 26]. Developed as a Hackathon MVP, it provides an accessible, real-time layer of security against SMS scams, social engineering, and malicious URLs. 

Instead of relying solely on standard databases, Shield AI brings the protection directly to the user's screen through an intuitive floating widget, allowing users to scan suspicious texts without constantly switching apps.

## ✨ Key Features
* **System-Wide Floating Widget:** Powered by Android `WindowManager` and Foreground Services, the ShieldAI widget sits seamlessly on top of other apps for instant access.
* **On-Device Machine Learning:** Utilizes **Google MediaPipe** and **TensorFlow Lite** models (`sms_spam_model.tflite`, `phishing_model.tflite`) for local text classification. This ensures zero-latency scanning and guarantees user privacy, as messages are analyzed directly on the device.
* **Advanced URL Verification:** Automatically extracts links from input text and queries the **Google Safe Browsing REST API** asynchronously via **OkHttp** to detect malware or social engineering threats.
* **Typosquatting & Contextual Detection:** Capable of catching deceptive links hidden inside seemingly harmless messages. [cite_start]For example, it can identify a malicious `https:discorcl.com/minecraft` link embedded within a long, friendly Minecraft community invite, protecting users from falling for the `discorcl.com` vs `discord.com` trick[cite: 19, 20, 21].

## 🛠️ Built With
* **Language:** Kotlin
* **Framework:** Android SDK
* **Machine Learning:** TensorFlow Lite, Google MediaPipe
* **Networking:** OkHttp
* **APIs:** Google Safe Browsing API

## 🚀 Getting Started

### Prerequisites
* Android Studio (latest version)
* An Android device or emulator running Android 8.0 (API level 26) or higher.

### Installation
1. Clone the repository:
   ```bash
   git clone [https://github.com/yourusername/ShieldAI.git](https://github.com/yourusername/ShieldAI.git)
