> **Status (19 October 2026): legacy guide only.** Do not follow these steps for production or place real university data/secrets into this setup. This guide contains superseded H2/PostgreSQL/MySQL deployment examples. The documented architecture target is MongoDB replica-set transactions, while the current backend still uses a JPA relational bridge; the target database migration and production acceptance are not complete. For the isolated synthetic professor review, use [the current local runbook](../PROFESSOR_DEMO.md). For current verified gaps and release gates, see [the implementation matrix](../CURRENT_IMPLEMENTATION_MATRIX_2026-10-18.md).

# Historical deployment examples — not a production runbook

Current release qualification is defined in [PRODUCTION_RELEASE_GATES.md](../PRODUCTION_RELEASE_GATES.md). The `prod` profile deliberately refuses startup until the reviewed Mongo persistence adapter and migration are implemented. None of the examples below authorizes deployment or supplies that missing implementation.

## 1. Environment Configuration

Create a `.env` file in the root directory (or set these in your cloud provider's dashboard):

```env
# Historical relational database example; superseded, do not deploy
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
APP_BASE_URL=https://<institution-approved-host>
# Exact hostname only; leave empty until the approved HTTPS hostname exists.
APP_RESET_LINK_HOSTS=<institution-approved-host>
UPLOAD_DIR=/app/uploads
```

Forgot-password requests retain a generic response. Reset tokens are issued only when the public origin is HTTPS and its exact hostname appears in `APP_RESET_LINK_HOSTS`; an empty value intentionally leaves password recovery unavailable.

## 2. Historical Docker example

The Docker configuration selects the blocked `prod` profile. Building an image does not demonstrate a runnable or approved production platform.

### Build & Run locally

```bash
docker-compose up --build -d
```

## 3. Superseded cloud examples (do not execute)

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
