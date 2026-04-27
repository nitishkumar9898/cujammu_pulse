# CUJAMMU EVENTHUB 🎓

A professional, market-ready University Event Management Platform built with Spring Boot, Thymeleaf, and AI.

## 🚀 Quick Start (Docker)

The fastest way to deploy the production-ready application.

1. **Clone & Configure**:

   ```bash
   cp .env.example .env
   # Edit .env and add your AI_API_KEY
   ```

2. **Launch**:

   ```bash
   docker-compose up -d --build
   ```

3. **Access**:
   - Web App: `http://localhost:8080`
   - Admin Login: `nitish.work098@gmail.com` / `Admin@123`

---

## ✨ Core Features

- **QR Attendance Ecosystem**: Robust scanner with manual capture, gallery upload, and zoom/torch controls.
- **AI Assistant**: Multilingual (English, Hindi, Hinglish) RAG-powered chatbot for 24/7 student support.
- **Automated Certificates**: Professional PDF generation and distribution upon attendance.
- **Real-time Notifications**: OTP-based registration and event announcements via SMTP.
- **Analytics Dashboard**: Comprehensive stats for organizers and admins.
- **Responsive Design**: Premium UI with full Dark Mode support and mobile optimization.

---

## 🛠 Tech Stack

- **Backend**: Spring Boot 3.5.14, Java 21
- **Database**: H2 (Persistent File-based)
- **Frontend**: Thymeleaf, Vanilla CSS, Bootstrap 5.3
- **AI**: Google Gemini 2.0 Flash
- **PDF/QR**: OpenHTMLtoPDF, ZXing, Html5-QRCode

---

## 🔧 Manual Setup (Local)

If you prefer running without Docker:

1. **Requirements**: JDK 21+, Maven 3.9+

2. **Build**:

   ```bash
   mvn clean install -DskipTests
   ```

3. **Run**:

   ```bash
   java -jar target/events-1.0.0.jar
   ```

---

## 🔐 Security & Hardening

- **OTP Verification**: Rate-limited registration and brute-force protection.
- **CSRF Protection**: Enabled on all forms and AJAX endpoints.
- **Audit Logging**: All critical admin/organizer actions are tracked.
- **Environment Isolation**: Sensitive credentials managed via `.env`.

---

## 👨‍💻 Developed by CUJAMMU Team

Ready for 100% bug-free deployment.
