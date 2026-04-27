# CUJAMMU EVENTHUB — Production Deployment Guide 🚀

## 1. Environment Configuration

Create a `.env` file in the root directory (or set these in your cloud provider's dashboard):

```env
# Database Configuration (PostgreSQL/MySQL recommended for production)
DB_URL=jdbc:postgresql://your-db-host:5432/eventhub
DB_USER=postgres
DB_PASS=your_secure_password

# Email Configuration (SMTP)
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your-email@gmail.com
EMAIL_PASS=your-app-specific-password
EMAIL_FROM=noreply@cujammu.ac.in

# AI Chatbot (Gemini API)
GEMINI_API_KEY=your_gemini_api_key
CHATBOT_ENABLED=true

# App Settings
APP_NAME="CUJAMMU EVENTHUB"
BASE_URL=https://events.cujammu.ac.in
UPLOAD_DIR=/app/uploads
```

## 2. Docker Deployment (Recommended)

We provide a multi-stage `Dockerfile` and `docker-compose.yml` for zero-configuration deployment.

### Build & Run locally

```bash
docker-compose up --build -d
```

## 3. Cloud Deployment (Railway / Render / Heroku)

1. **Repository**: Push this code to a Private GitHub repository.
2. **Railway**:
   - New Project -> GitHub Repo.
   - Add **Postgres** plugin.
   - Set the variables in the "Variables" tab.
3. **Render**:
   - New Web Service -> GitHub Repo.
   - Select **Docker** as environment.
   - Add Disk (1GB) mounted at `/app/uploads` to persist profile pictures.

## 4. Security Checklist

- [ ] Change `DB_PASS` and `EMAIL_PASS`.
- [ ] Ensure `BASE_URL` uses `https`.
- [ ] Review `SecurityConfig.java` for CSRF/CORS settings.
- [ ] Set `logging.level.org.springframework.web=ERROR` in production.

## 5. Maintenance

- **Backups**: Schedule daily backups of the H2/Postgres database.
- **Logs**: Monitor logs via `docker logs -f eventhub-app`.
- **Uploads**: The `/uploads` directory must be persisted (using Docker Volumes or Cloud Storage).

---

© 2026 Central University of Jammu | Event Management System
