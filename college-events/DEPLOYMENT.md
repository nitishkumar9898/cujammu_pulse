> **Status (19 October 2026): legacy guide only.** Do not follow these steps for production or place real university data/secrets into this setup. This guide contains superseded H2/PostgreSQL/MySQL deployment examples. The documented architecture target is MongoDB replica-set transactions, while the current backend still uses a JPA relational bridge; the target database migration and production acceptance are not complete. For the isolated synthetic professor review, use [the current local runbook](../PROFESSOR_DEMO.md). For current verified gaps and release gates, see [the implementation matrix](../CURRENT_IMPLEMENTATION_MATRIX_2026-10-18.md).

# CUJAMMU EVENTHUB — Setup & Deployment Guide

## 📋 Prerequisites

- **Java 21** (JDK): [Download](https://adoptium.net/)
- **Maven 3.9+**: [Download](https://maven.apache.org/download.cgi)
- **Git**: [Download](https://git-scm.com/)
- **Gmail Account** with App Password (for email features)
- **Google Gemini API Key** (optional, for AI chatbot)

---

## 🚀 Quick Start (Local Development)

### 1. Clone & Build

```bash
# Clone the project
git clone https://github.com/YOUR_USERNAME/cujammu-eventhub.git
cd cujammu-eventhub/college-events

# Build the project (skip tests for fast startup)
mvn clean compile -q
```

### 2. Set Environment Variables

**Windows (PowerShell):**
```powershell
$env:SPRING_MAIL_USERNAME="your_gmail@gmail.com"
$env:SPRING_MAIL_PASSWORD="your_16_char_app_password"
$env:GEMINI_API_KEY="your_gemini_api_key"
```

**Windows (CMD):**
```cmd
set SPRING_MAIL_USERNAME=your_gmail@gmail.com
set SPRING_MAIL_PASSWORD=your_16_char_app_password
set GEMINI_API_KEY=your_gemini_api_key
```

**Linux/macOS:**
```bash
export SPRING_MAIL_USERNAME=your_gmail@gmail.com
export SPRING_MAIL_PASSWORD=your_16_char_app_password
export GEMINI_API_KEY=your_gemini_api_key
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

### 4. Access the Application

| URL | Description |
|-----|------------|
| http://localhost:8080 | Home page |
| http://localhost:8080/login | Login |
| http://localhost:8080/register | Register |
| http://localhost:8080/h2-console | Blocked by the current security configuration (403) |
| http://localhost:8080/api/health | Health check API |

### Administrator access
No shared administrator credential is shipped. Never use credentials from copied setup guides. For an isolated professor review, use the dedicated `professor-demo` profile and a unique local password; production administrators must be provisioned through the university-approved identity and access process with MFA.

---

## 🧪 Running Tests

```bash
# Run all tests
mvn test

# Run only chatbot tests (50+ test cases)
mvn test -Dtest=ChatbotServiceTest

# Build + test + package
mvn clean package
```

---

## 📦 Git Setup & Push to GitHub

### Initialize Repository

```bash
cd college-events

# Initialize git repo
git init

# Create .gitignore
echo "target/
*.class
*.jar
*.log
.idea/
*.iml
*.iws
.DS_Store
uploads/
data/
.env
*.orig" > .gitignore

# Stage all files
git add .

# Commit
git commit -m "feat: CUJAMMU EVENTHUB v1.0.0 - Production-ready university event management platform

Features:
- Student/Organizer/Admin portals with Spring Security
- OTP-based registration with rate limiting
- QR code attendance scanning with Excel/CSV export
- 25 professional certificate templates with PDF generation
- AI chatbot (Gemini RAG + multilingual rule-based fallback)
- Real-time email notifications with retry queue
- Dark mode with 80+ CSS selectors
- WhatsApp-style profile picture upload
- Event lifecycle management (create/approve/cancel/postpone/complete)
- Gamification points system"
```

### Push to GitHub

```bash
# Create a new repository on GitHub, then:
git remote add origin https://github.com/YOUR_USERNAME/cujammu-eventhub.git
git branch -M main
git push -u origin main
```

---

## 🌐 Production Deployment

### Option 1: Railway (Recommended — Easiest)

1. **Connect GitHub:** Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
2. **Add MySQL plugin:** Click "+ New" → MySQL
3. **Set environment variables:** In your service → Variables:
   ```
   SPRING_PROFILES_ACTIVE=
   SPRING_DATASOURCE_URL=${{MySQL.MYSQL_URL}}
   SPRING_DATASOURCE_USERNAME=${{MySQL.MYSQL_USER}}
   SPRING_DATASOURCE_PASSWORD=${{MySQL.MYSQL_PASSWORD}}
   SPRING_MAIL_USERNAME=your_gmail@gmail.com
   SPRING_MAIL_PASSWORD=your_app_password
   GEMINI_API_KEY=your_key
   APP_BASE_URL=https://your-app.up.railway.app
   APP_RESET_LINK_HOSTS=your-app.up.railway.app
   ```
   Password-reset email requires `APP_RESET_LINK_HOSTS` to contain the exact HTTPS hostname used by `APP_BASE_URL`. Keep it blank until the approved hostname is assigned; blank intentionally disables reset-link issuance and delivery.
4. **Build command:** `mvn clean package -DskipTests`
5. **Start command:** `java -jar target/events-1.0.0.jar`

### Option 2: Render (Free Tier)

1. Go to [render.com](https://render.com) → New → Web Service
2. Connect your GitHub repo
3. **Build command:** `mvn clean package -DskipTests`
4. **Start command:** `java -jar target/events-1.0.0.jar`
5. Add environment variables in the Environment tab
6. Add a Render MySQL/PostgreSQL database

### Option 3: AWS EC2

```bash
# SSH into your EC2 instance
ssh -i your-key.pem ec2-user@your-ip

# Install Java 21
sudo yum install java-21-amazon-corretto -y

# Upload JAR (from local machine)
scp -i your-key.pem target/events-1.0.0.jar ec2-user@your-ip:~/

# Run with environment variables
export SPRING_PROFILES_ACTIVE=
export SPRING_DATASOURCE_URL=jdbc:mysql://your-rds-endpoint:3306/college_events
export SPRING_DATASOURCE_USERNAME=admin
export SPRING_DATASOURCE_PASSWORD=your_password
export SPRING_MAIL_USERNAME=your_gmail@gmail.com
export SPRING_MAIL_PASSWORD=your_app_password
export GEMINI_API_KEY=your_key
export APP_BASE_URL=https://your-domain.com
export APP_RESET_LINK_HOSTS=your-domain.com

nohup java -jar events-1.0.0.jar &
```

### MySQL Production Setup

```sql
CREATE DATABASE college_events CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'eventhub'@'%' IDENTIFIED BY 'replace-with-a-unique-secret';
GRANT ALL PRIVILEGES ON college_events.* TO 'eventhub'@'%';
FLUSH PRIVILEGES;
```

Then update your environment:
```
SPRING_PROFILES_ACTIVE=       # Remove H2 profile
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/college_events?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=eventhub
SPRING_DATASOURCE_PASSWORD=replace-with-the-same-unique-secret
```

---

## 📧 Gmail App Password Setup

1. Go to [Google Account Security](https://myaccount.google.com/security)
2. Enable **2-Step Verification** (required)
3. Go to [App Passwords](https://myaccount.google.com/apppasswords)
4. Select **Mail** → **Windows Computer** (or Other)
5. Click **Generate** → Copy the 16-character password
6. Use this as `SPRING_MAIL_PASSWORD`

---

## 🔑 API Keys & Services

| Service | Variable | Required | Purpose |
|---------|----------|----------|---------|
| Gmail SMTP | `SPRING_MAIL_USERNAME` | Recommended | OTP, notifications, certificates |
| Gmail SMTP | `SPRING_MAIL_PASSWORD` | Recommended | Gmail App Password |
| Gemini AI | `GEMINI_API_KEY` | Optional | AI chatbot intelligence |
| MySQL | `SPRING_DATASOURCE_URL` | Production | Production database |

---

## 📊 Health Check

After deployment, verify everything is working:

```bash
curl https://your-domain.com/api/health
```

Expected response:
```json
{
  "status": "UP",
  "application": "CUJAMMU EVENTHUB",
  "version": "1.0.0",
  "database": "UP",
  "email": "CONFIGURED",
  "email_retry_queue": 0,
  "chatbot_ai": "AVAILABLE"
}
```
