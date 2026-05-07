
# TrustSphere (Linkguard_AI)

TrustSphere is a comprehensive anti-phishing solution designed to protect users from malicious links. The project consists of a Python-based Machine Learning backend and an Android application frontend that works in real-time to monitor and verify URLs.

## 🚀 Features

- **Real-Time Link Monitoring:** Uses Android Accessibility Services to detect and scan URLs when opened in browsers or other apps.
- **SMS Link Scanning:** Automatically intercepts and checks URLs sent via SMS messages to prevent SMS phishing (Smishing).
- **Machine Learning Analysis:** A Flask backend running a Logistic Regression model trained with TF-IDF character features to distinguish between safe and malicious domains.
- **Heuristics Engine:** Checks for common phishing indicators such as IP addresses in URLs, excessive hyphens, and deep subdomains.
- **QR Code Scanning:** Includes camera permissions to potentially scan and verify QR codes containing URLs.

## 🛠️ Technology Stack

**Backend (Python):**
- Flask (RESTful API)
- scikit-learn (Machine Learning - Logistic Regression)
- NumPy
- Term Frequency-Inverse Document Frequency (TF-IDF) feature extraction

**Frontend (Android):**
- Kotlin
- Android SDK (AccessibilityService API, SMS Receiver)
- Gradle build system

... (includes Setup instructions and Permissions details)
