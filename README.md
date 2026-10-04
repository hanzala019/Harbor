# Harbor Hospital Appointment System

A hospital appointment web app for patients, receptionists, and administrators. Patients can find doctors, book without an account, and manage their appointments. Staff can manage schedules, bookings, queues, and doctor access. Built with React/TypeScript, Spring Boot, and PostgreSQL.

## Run locally on Windows

### 1. Install prerequisites

- **JDK 17+** — set `JAVA_HOME` to your JDK installation directory if it is not detected automatically.
- **Node.js 22+** with npm.
- **PostgreSQL 17**, running locally.

The launcher downloads Maven and installs missing frontend dependencies automatically. The first run requires internet access.

### 2. Create the database

In pgAdmin, open the Query Tool on the `postgres` database and run this once:

```sql
CREATE DATABASE harbor;
```

The app creates and updates its tables automatically when the backend starts.

### 3. Configure the app

Open PowerShell in the project root folder—the folder containing `start-local.ps1` and `compose.yml`—then run:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Set these values to match your local setup. Use unquoted `KEY=value` entries:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/harbor
DB_USER=postgres
DB_PASSWORD=your-postgresql-password
ADMIN_EMAIL=admin@hospital.local
ADMIN_PASSWORD=choose-a-password-at-least-10-characters
HOSPITAL_TIMEZONE=Asia/Dhaka
SEED_DEMO=true
MAIL_ENABLED=false
```

Choose your hospital's timezone. `SEED_DEMO=true` adds sample doctors and schedules when the doctor directory is empty; use `false` for your own hospital data. `.env` is excluded from Git.

### 4. Start the app

From the same PowerShell window:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-local.ps1
```

The launcher checks prerequisites, builds the backend when needed, starts both servers, and opens the app in your browser. The default app URL is **http://localhost:5173**; if a port is busy, it selects another and prints the actual URL.

Keep the terminal open while using the app. Press **Ctrl+C** to stop both servers. Log in using the Admin email and password from `.env`.

Useful options:

```powershell
# Check prerequisites without starting the app.
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -CheckOnly

# Start without opening a browser.
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -NoBrowser

# Choose preferred ports.
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -BackendPort 18080 -WebPort 5173
```

`ADMIN_PASSWORD` creates the first Admin account only. Changing it in `.env` does not reset an existing account's password.

## Run with Docker

Install and start **Docker Desktop** with **Linux containers** and at least **4 GB RAM** available. Docker runs the frontend, backend, and PostgreSQL, so separate Java, Node.js, and PostgreSQL installations are not required.

### 1. Configure the app

From the project root in PowerShell:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Set `DB_PASSWORD`, `ADMIN_EMAIL`, and an `ADMIN_PASSWORD` of at least 10 characters. Also set:

```dotenv
WEB_PORT=3000
DOCKER_PUBLIC_URL=http://localhost:3000
HOSPITAL_TIMEZONE=Asia/Dhaka
SEED_DEMO=true
MAIL_ENABLED=false
```

If you change `WEB_PORT`, update the port in `DOCKER_PUBLIC_URL` too. Compose uses its own PostgreSQL database and does not connect to your local PostgreSQL server.

### 2. Start the containers

```powershell
docker compose up --build -d
docker compose logs -f backend
```

Wait for the backend to finish starting, then open **http://localhost:3000** (or your configured `WEB_PORT`). Log in using the Admin credentials from `.env`. Press **Ctrl+C** to stop following logs; the containers keep running.

### 3. Stop or rebuild

```powershell
# Stop the app and keep database data.
docker compose down

# Start again or rebuild after code changes.
docker compose up --build -d
```

Database data is stored in the `hospital-data` volume. Avoid `docker compose down -v` unless you intend to delete that database.

### Optional: preview emails with Mailpit

To capture test emails locally, set these values in `.env`:

```dotenv
MAIL_ENABLED=true
SMTP_HOST=mailpit
SMTP_PORT=1025
SMTP_USER=
SMTP_PASSWORD=
SMTP_AUTH=false
SMTP_TLS=false
```

Then start the mail profile:

```powershell
docker compose --profile mail up --build -d
```

Open **http://localhost:8025** to view messages. For real email delivery, configure your SMTP provider using the settings in `.env.example` and set `MAIL_ENABLED=true`. Restart the native app or recreate Docker containers after configuration changes.
