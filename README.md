# Harbor Hospital Appointment System

React + TypeScript/Vite, shadcn/ui + Tailwind, Spring Boot REST API, Spring Security, JPA, PostgreSQL 17, and Flyway. The backend targets Java 17 and runs on your **Java 19.0.2**. Native development does not need Docker.

## Version milestone

**V1 is the baseline; all 18 agreed V2 enhancements are implemented and covered by native and Docker verification.** Feature completion and deployment status are tracked in [`ROADMAP.md`](ROADMAP.md). Docker Desktop Linux-container builds, browser workflows through nginx, Mailpit delivery, and database persistence across Compose shutdown/restart have passed on this device.

**V3 adds patient and reception convenience:** next-available discovery, patient/guest date-time changes, a dedicated receptionist booking workflow, and email password recovery with configurable visit/contact information. Commit `b3c98e8` records the completed V2 baseline before this update.

## Quick local startup (V2)

Stop any older backend/frontend terminals with **Ctrl+C**, then run from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-local.ps1
```

The launcher loads `.env`, finds JDK 17+ (including this machine's Java 19), checks Node and PostgreSQL access, installs frontend dependencies if missing, builds an absent/outdated backend jar, and starts both servers with matching available ports. It prints the actual URL and opens the browser. Keep this terminal open; **Ctrl+C** stops the processes the launcher started, without stopping PostgreSQL or unrelated servers.

Useful options:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -CheckOnly
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -NoBrowser
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -BackendPort 18080 -WebPort 5173
powershell -ExecutionPolicy Bypass -File .\start-local.ps1 -SmokeTest -NoBrowser
```

`-CheckOnly` checks prerequisites without starting servers. `-SmokeTest` starts both, checks their HTTP endpoints, then shuts them down. Occupied preferred ports are replaced with nearby available ports. The manual commands below remain supported.

## Run locally on Windows (without Docker)

Prerequisites: **JDK 17+**, **Node 22+**, and a running **PostgreSQL 17** server. Maven is bootstrapped by `backend/mvnw.ps1` on first use (internet required); installing Maven separately is optional.

### 1. Create the database once

Using pgAdmin's Query Tool on the `postgres` database:

```sql
CREATE DATABASE harbor;
```

The configured database is `harbor`, with PostgreSQL username `postgres` and password `postgres`. Flyway creates the tables automatically; do not create them manually. These database credentials are separate from the application's Admin email/password.

### 2. Start the backend in PowerShell

From the repository root:

```powershell
# Your java command uses Java 19, but JAVA_HOME may still point to Java 11.
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-19'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/harbor'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = 'postgres'
$env:ADMIN_EMAIL = 'admin@hospital.local'
$env:ADMIN_PASSWORD = 'choose-a-password-at-least-10-characters'
$env:SEED_DEMO = 'true'
powershell -ExecutionPolicy Bypass -File .\backend\mvnw.ps1 spring-boot:run
```

API: `http://localhost:8080/api/health`. The admin account is created only if no admin exists; changing the environment password later does not reset an existing account. Demo mode creates three doctors and fourteen days of sessions only when the doctor directory is empty. Set `SEED_DEMO=false` for your own hospital data.

### 3. Start the frontend in another terminal

```powershell
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite forwards `/api` to port 8080; use the frontend URL for the application. Log in using the admin credentials set above. Create receptionist accounts under **Team & access**, then assign their doctors and edit permissions.

If port 8080 is occupied (as observed on this machine), set `$env:PORT = '18080'` in the backend terminal before starting it, and `$env:API_PROXY = 'http://localhost:18080'` in the frontend terminal before `npm run dev`. The API health URL then uses port 18080.

On macOS/Linux, set the same variables using `export` and run `sh backend/mvnw spring-boot:run`. Choose the correct local JDK path. For a packaged native backend: `mvnw.ps1 package`, then `java -jar backend/target/hospital-1.0.0.jar` from the root with the same environment variables.

## Run with Docker

Docker Desktop must use **Linux containers**. Allocate at least **4 GB RAM**.

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
# Edit .env: set ADMIN_PASSWORD for the application's Admin login.
docker compose up --build -d
docker compose logs -f backend
```

Open **http://localhost:3000** (or your `WEB_PORT`). First startup includes dependency downloads and migrations. PostgreSQL runs inside Compose; your local PostgreSQL installation is not used, and its port is not occupied by this stack. Database data persists in the `hospital-data` volume.

```powershell
docker compose down             # Stops the stack; keeps database data.
docker compose up --build -d    # Rebuild after code changes.
```

Deploy using the same Compose stack on a Linux Docker host. Set real credentials, disable demo seeding, configure SMTP, and place HTTPS termination in front of the published web port. The frontend proxies the API internally, so no frontend API URL rebuild is needed.

## Email

Bookings and staff notifications create **transactional database outbox entries**. A worker delivers separate patient emails every 30 seconds, retrying failures up to 10 attempts. A mail failure never rolls back a booking. With `MAIL_ENABLED=false` messages remain queued; no email is actually sent.

For Docker email testing, set `MAIL_ENABLED=true`, `SMTP_HOST=mailpit`, and `SMTP_PORT=1025` in `.env`, then:

```powershell
docker compose --profile mail up --build -d
```

View messages at **http://localhost:8025**. Configure SMTP through `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS`, `MAIL_FROM`, and `MAIL_ENABLED=true`. Native Spring Boot loads root `.env` when launched from the repository root or `backend`; environment variables override file values. Use unquoted `KEY=value` entries. `.env` is ignored by Git; keep real passwords there rather than in source files. Restart the backend after changing mail settings.

For Gmail, use `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`, `SMTP_AUTH=true`, and `SMTP_TLS=true`. Set `SMTP_USER` and `MAIL_FROM` to the sender's Gmail address, and `SMTP_PASSWORD` to its app password without grouping spaces. A local Gmail configuration is supplied in the ignored `.env`; for Compose, also supply `ADMIN_PASSWORD` in that file. When switching to Mailpit, clear `SMTP_USER`/`SMTP_PASSWORD` and set `SMTP_AUTH=false`/`SMTP_TLS=false`.

## Features and workflows

### V3 patient and reception convenience

- **Find an available appointment:** Find a doctor defaults to **Next available (next 90 days)** and displays each doctor's earliest bookable date/time. Custom date, Today, Tonight, and Tomorrow show only doctors with matching future slots. Today includes later sessions, not just sessions currently underway; Tonight starts at 18:00 in the hospital timezone. Doctor booking and date-change screens have **Next available appointment** and an expandable list of available dates for the next 90 days from the chosen date. You can also enter dates beyond that discovery window manually.
- **Change date / time:** registered patients use My appointments; guests use the private link in their email. Select another slot with the same doctor, review the new details, and confirm. The original booking remains intact if the destination is unavailable, occupied, or a duplicate. Changes are atomic under stable schedule locks, retain the appointment ID, free the old slot, and queue an updated email. Moving to another session assigns a destination-local serial; changing time within the same session keeps the serial. Existing unexpired guest links continue to manage that same appointment. Audit history records the old and new session/date/time/serial.
- **Receptionist booking:** Appointments → **Book for a patient** opens `/staff/book` for walk-in/telephone bookings. Select an assigned doctor/time, search existing patients by name/email/phone, or enter new guest details. Admin can search registered patients; receptionists can reuse patients from assigned-doctor appointment history only. Selecting a registered patient links the booking to their account and uses their saved profile. Guests receive private management links without requiring registration. Duplicate alerts require a deliberate second confirmation; all staff bookings enforce doctor access on the backend.
- **Forgot password:** Login → **Forgot password?** requests an email link with a generic response for unknown/disabled accounts. Enabled accounts can request one link per minute. Links use URL fragments, are hashed in the database, expire after 30 minutes, and work once; a newer request invalidates the earlier link. A successful reset revokes all login sessions. Authenticated password changes and Admin resets/disabling invalidate outstanding recovery links. `MAIL_ENABLED=true`, working SMTP, and a reachable `PUBLIC_URL`/`DOCKER_PUBLIC_URL` are needed for delivery.
- **Plan your visit:** the homepage, footer, booking confirmation, patient appointments, and private guest view link to `/visit-info`. Supply real details using the unquoted `.env` values below, then restart the backend (or recreate Compose containers):

  ```dotenv
  HOSPITAL_ADDRESS=Your hospital's real street address
  RECEPTION_PHONE=Your hospital's real reception phone
  DIRECTIONS_URL=https://your-hospital.example/directions
  VISIT_INSTRUCTIONS=Bring your appointment confirmation and relevant medical records. Check in at reception before going to your consultation room.
  ```

  The page offers click-to-call and directions when configured, and explains arrival/check-in guidance. Without `DIRECTIONS_URL`, a configured address provides a map-search link. Blank contact fields are not replaced with invented details. Confirmation/rescheduling emails include the configured address, reception phone, directions, and instructions.

### V2 quality-of-life features

- **Calendar and templates:** the Doctors page has a seven-day calendar with session hours, booking counts, and availability. Click a day/session to open its actions. **Copy this week** opens the weekly wizard with its available hours; mixed visit durations require separate templates. Save named templates inside the wizard, search/select one to reuse it, and preview before applying it to another date window.
- **Search, sorting, and pagination:** doctor/receptionist selectors support typing, arrow keys, and Enter. Staff/admin tables have sorting and 15/30/50-row pagination; Ongoing keeps round-robin order instead of exposing global sorting. Filters, selected doctors, dates, and table preferences are remembered per account. Use local reset buttons or **Reset saved filters** in the footer to clear preferences.
- **Permission presets:** Team & access offers View only, Scheduling desk (schedule + availability editing), and Full doctor management. Review or override individual flags before saving the complete selection.
- **Live queues and feedback:** queues refresh every 30 seconds while the page is visible, with an optional pause checkbox, last-update time, and manual refresh. Mutations show consistent success notifications, including created/updated counts where applicable.
- **Email tools:** Tools & history → Email delivery shows queued/retrying/sent/failed status and supports resending confirmations. Appointment rows also have a resend action, including V1 records without linked email history. A pending job blocks duplicate resends. SMTP acceptance is recorded as SENT, not proof of inbox delivery; the worker retries up to 10 attempts.
- **Guest management:** new guest confirmation/rescheduling emails contain an appointment-scoped private link. The random token is hashed in the database, expires after 30 days, and is sent in a URL fragment. Guests can view or cancel active future appointments without registering. Cancellation, account claiming, or staff link revocation invalidates the links. Resending a guest confirmation generates a fresh link.
- **Duplicate detection:** registered patients are matched by account; guests by normalized email and digit-normalized phone. An active appointment with the same doctor/date blocks another booking. Authorized staff booking from the doctor page see an alert and must deliberately confirm again to override it; the override is audited.
- **History and timezone:** Upcoming uses consultation time as well as active status. Past unprocessed appointments appear in history without changing their status. Appointment times and email details use `HOSPITAL_TIMEZONE` (an IANA zone, configured locally as `Asia/Dhaka`) and carry an absolute consultation instant. Configure the same zone for native and Docker deployments.
- **Account security:** the avatar menu → Account security changes your password after checking the current password, then revokes all sessions. Admin can disable/enable receptionists, reset their passwords, and revoke sessions under Tools & history → Receptionist accounts. Changing `.env` never resets an existing account's password.
- **Audit and selective transfers:** Tools & history → Audit history supports date/search filters and records actors, times, records, and change details without raw passwords/tokens. Receptionists see only assigned-doctor history. In affected-patient review, select the patients who agreed to move; unselected patients remain in place, capacity is checked atomically, and only selected patients get rescheduling notifications.

`PUBLIC_URL` must be the reachable frontend URL used in guest emails. The launcher sets it to its chosen port. Compose uses `DOCKER_PUBLIC_URL` (default `http://localhost:3000`); set it to your deployed HTTPS URL when hosting remotely. Do not change the hospital timezone after bookings exist without reviewing their intended interpretation.

### V1 foundations

- Guests use **Quick Appointment**: search → doctor details → choose a slot → enter patient details → review → confirm. Serials shown before confirmation are estimates. Confirmation assigns the final doctor/session-local serial.
- Optional **Save My Details / Register as Patient** appears after booking. Skipping keeps the booking. Registration uses a private, one-use booking token and reuses patient details.
- Registered patients can book from their saved profile, view appointments/history, cancel an appointment, and update their profile.
- Receptionists see only assigned doctors. Info, schedule, and availability editing are independently authorized by the API. Login redirects to Appointments.
- Ongoing appointments use a round-robin next-patient table. Visited, no-show, and cancelled records remain in history. The right panel shows the next pending serial for each doctor/session.
- To change a booked session, **review affected patients** first. Mark it unavailable, optionally notify patients, create a new non-overlapping session, then transfer the agreed active queue. Transfer preserves relative order, skips inactive patients, appends after existing destination serials, and fails atomically if capacity is insufficient. Arbitrary time edits to booked sessions are blocked.
- Each calendar date can contain multiple working sessions. Model unavailable time ranges as gaps between sessions, or mark individual sessions unavailable; whole-day unavailability requires marking all that day's sessions.
- In **Doctors & schedules**, select a doctor and choose **Weekly schedule**. Set a date window (up to 366 days), select weekdays, and add one or more time ranges per day. **Copy hours to selected days** avoids repeating the same hours. Preview the generated sessions, then create them together. Identical existing sessions are skipped; overlapping sessions block the whole batch. Existing availability and appointments are preserved. These are dated sessions through the chosen end date, not an indefinite recurrence.
- Public registration is Patient-only. Admin creates receptionists, manages doctors, views patients/appointments, and grants or revokes doctor assignments.
- In **Team & access**, choose a receptionist, filter doctors by name/room/specialty/department, and select multiple doctors or **Select all filtered**. Selections stay selected when filters change. Saving applies the same view/edit permissions to every selected doctor; existing selected assignments receive the new permissions. Use **Edit access** in the assignments table for a single doctor's settings.
- Receptionists can be searched by name/email/phone. The assignments table filters by search, receptionist, doctor, and permission; the patient directory filters by name/email/phone, gender, and age range. Each table displays matching counts and has a clear-filters action.
- Authentication uses expiring opaque bearer tokens, hashed in the database; the frontend stores its token in session storage. Logout revokes it. No browser cookies are used for authentication.
- No billing, payments, or service fees.

## Verification

```powershell
# Backend integration tests and executable jar (no Docker or PostgreSQL needed):
powershell -ExecutionPolicy Bypass -File .\backend\mvnw.ps1 verify
# Single test class:
powershell -ExecutionPolicy Bypass -File .\backend\mvnw.ps1 '-Dtest=HospitalIntegrationTest' test
# Frontend typecheck and production build:
cd frontend
npm run build
npm test
```

Backend tests use an isolated H2 database in PostgreSQL mode and apply the same Flyway migration. To run against a **dedicated empty PostgreSQL test database**, set `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` before running tests. Tests create test accounts/doctors/bookings: never point these variables at your normal hospital database.

V2 and V3 browser tests use Playwright, a local SMTP fixture, and a dedicated PostgreSQL database. Build the backend first, create an empty database such as `harbor_v3_test`, then in `frontend`:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/harbor_v3_test'
$env:TEST_DB_USER = 'postgres'
$env:TEST_DB_PASSWORD = 'postgres'
npm ci
npx playwright install chromium
npm run test:e2e
```

The runner starts isolated backend/frontend processes (default ports 28080 and 15173), captures SMTP messages locally without Gmail, creates fixtures, exercises the V2 and V3 interfaces, and stops its own servers. It supplies clearly fictional contact details and restores the test Admin password after its password-change check. Choose free ports with `E2E_BACKEND_PORT`/`E2E_WEB_PORT`; set `E2E_JAVA` if `java` on PATH is not JDK 17+. Use a dedicated empty test database for each complete run; fixtures are retained there for inspection.

## Structure

- `backend/src/main/java/com/hospital`: API controllers, transactional business rules, security, JPA models, email worker.
- `backend/src/main/resources/db/migration`: versioned database schema; add new migrations rather than editing applied versions.
- `frontend/src/pages`: public booking, patient account, and staff/admin views.
- `frontend/src/components/ui`: shadcn-style Radix primitives.
<<<<<<< HEAD

=======
>>>>>>> 034f668ff25c408277cf257d02770f0d2b245f84

This is an initial implementation. Hospital contact details, real testimonials, and staff biographies should be supplied by the hospital; illustrative/demo data is not a claim about a real facility.
