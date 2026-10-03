# V1 baseline · V2 complete · V3 convenience update

## V1 milestone

Commit `637f5be` records the completed V1 baseline: Quick Appointment and optional post-confirmation registration, patient profiles/history, Admin and scoped receptionist workflows, date/session-local serials, round-robin queues, email outbox delivery, affected-patient review/transfers, weekday scheduling, filtered bulk assignments, native Windows setup, and Docker Compose configuration.

## V2 feature checklist

All 18 agreed enhancements are implemented. The checks below track features; native and Docker runtime verification are complete.

- [x] **1. Single-command local startup:** `start-local.ps1` loads `.env`, checks Java/Node/PostgreSQL, bootstraps missing dependencies and outdated builds, selects matching available ports, prints/opens the URL, and cleans up its owned processes. `-CheckOnly` and `-SmokeTest` support diagnostics.
- [x] **2. Calendar with a week view:** Doctors & schedules displays seven days of working sessions, availability, and booking counts, with navigation and direct session actions.
- [x] **3. Copy a week and reusable templates:** copy available weekly hours or save/select/delete named weekday/time-range templates, then preview/apply to a bounded date window. Overlaps, permissions, and booked sessions remain protected.
- [x] **4. Searchable dropdowns:** doctor/receptionist selectors and template selection support text search and keyboard navigation. Multi-doctor checkbox selection remains available.
- [x] **5. Table sorting and pagination:** assignment, patient, appointment, email, audit, and account tables support practical sort fields and 15/30/50-row pagination. Ongoing preserves round-robin queue order.
- [x] **6. Remember filters:** account-scoped local preferences restore searches, selected doctors, date ranges, table filters, and sorting/row-count settings. Local resets and a global Reset saved filters action are available.
- [x] **7. Consistent success feedback:** mutation notifications show successful actions and relevant counts; views retain loading, empty, and error feedback. Weekly preview/save actions stay visible in the dialog footer.
- [x] **8. Email status and resend:** authorized staff see queued/retrying/sent/failed delivery status, attempts, and timestamps without message bodies or private tokens. Pending jobs prevent duplicate resends; failed/completed jobs can receive a new confirmation.
- [x] **9. Private guest management links:** hashed, appointment-scoped tokens expire after 30 days and travel in email URL fragments. Guests can view/cancel active future appointments without registration; claiming, cancellation, and staff revocation invalidate access.
- [x] **10. Duplicate-booking detection:** matching uses a registered account or guest email plus digit-normalized phone for the same doctor/date and active state. Staff must explicitly override after the alert; overrides are audited.
- [x] **11. Date-aware history:** Upcoming checks consultation time and status. Past active records appear in history without silently changing their status. Cancellation preserves consultation time while freeing the slot.
- [x] **12. Hospital timezone:** configured IANA timezone controls date/time business rules, availability search, appointment instants, UI labels, and email details, consistently across native and Compose configuration.
- [x] **13. Live queue refresh:** visible queue pages refresh every 30 seconds with a pause control, manual refresh, and last-update time. Stale responses cannot overwrite newer data, and filters remain selected.
- [x] **14. Receptionist account management:** Admin can disable/enable accounts, reset passwords, and revoke login sessions. Disabled accounts are rejected by authentication and protected business actions.
- [x] **15. Admin password change:** Account security verifies the current password, changes it without database tools, and revokes all sessions, including the current one. The same secure flow is available to other signed-in roles.
- [x] **16. Audit history:** actor/time/record/action details are persisted for doctor/schedule/availability, permission, appointment, transfer, template, and account changes. Staff history is doctor-scoped; Admin can search the full history. Passwords and raw tokens are excluded.
- [x] **17. Selective transfers:** staff select the active patients who agreed to move. Their relative order is preserved, unselected patients remain, capacity is checked atomically, and only moved patients get updated notifications.
- [x] **18. Assignment presets:** View only, Scheduling desk, and Full doctor management presets show their flags and allow individual overrides before an atomic bulk save.

## V3 feature checklist

Commit `b3c98e8` records the completed, Docker-verified V2 baseline. V3 implements the four selected convenience improvements:

- [x] **Next-available discovery and consistent availability filters:** a public, bounded availability search returns only future free slots without patient details; custom-date/Today/Tonight/Tomorrow filtering is consistent. Doctor cards show earliest availability, and booking/change screens provide next-available and available-date navigation.
- [x] **Patient date/time changes:** registered and private-link guest patients can review and confirm another slot with the same doctor. Stable schedule locks and refreshed appointment state protect ownership, double-booking, active/future status, duplicate rules, and rollback. Successful changes free the source slot, recalculate destination-local serials, email updated details, and audit the original and new visit.
- [x] **Receptionist booking shortcut:** Appointments prominently links to an assigned-doctor booking page with scoped patient lookup, registered account reuse, guest details, review, and explicit duplicate overrides. Backend scoping applies to both the dedicated staff endpoint and staff use of the public booking endpoint.
- [x] **Password recovery and visit/contact information:** hashed, single-use, 30-minute email recovery links with per-account cooldown, generic request responses, and session revocation; configurable address, reception phone, directions, and arrival/check-in instructions on the website and in appointment emails. Forward Flyway migration V4 adds recovery storage.

## Verification and deployment status

- V3 verification passes: all 30 backend integration tests on H2 and a dedicated PostgreSQL 17 database; four frontend tests; native and Docker TypeScript/production builds; and V2/V3 Playwright workflows through Docker nginx with Mailpit. V3 browser checks include availability filtering, guest booking with registration skipped, editable reused patient details, registered/guest rescheduling with a competing-slot failure/retry, receptionist account-linked booking and duplicate override, emailed password recovery/session revocation, visit/contact links, and mobile patient/receptionist layouts. V4 applies successfully to PostgreSQL. Verification uses isolated test databases and local SMTP; normal hospital data and SMTP credentials are untouched.
- Backend integration checks exercise V1 invariants and V2 guest security, account/session management, duplicate handling, selective transfers, templates, email privacy/resend, audits, timezone instants, and slot-history preservation on H2 and dedicated PostgreSQL 17 databases.
- Frontend tests cover round-robin ordering, active/past classification, and hospital date calculations. The TypeScript/production build passes.
- Playwright browser checks cover searchable selectors, presets, sorting/pagination, remembered filters, calendar/templates/copy-week, queue/email controls, actual local SMTP acceptance/resend, guest cancellation, selective transfer/audit, receptionist controls, Admin password change, and mobile layout.
- Native launcher prerequisite and complete startup/shutdown smoke checks pass on this Windows/Java 19/PostgreSQL 17 machine.
- Docker runtime verification passed on 2026-10-03 using Docker Desktop's WSL2 Linux engine and an isolated Compose project/database. Both production images build, all 22 backend integration tests pass in the Java 17 build stage, and PostgreSQL 17 applies all three Flyway migrations. The full V2 Playwright suite passes through nginx with real Mailpit SMTP acceptance/resend and guest links. Production assets, SPA fallback, timezone configuration, and database persistence across Compose down/up pass. Verification exposed a mobile queue-grid overflow, which was fixed and rechecked. Temporary containers, network, and test database volume were removed afterward.

V2 uses forward Flyway migrations and retains V1 product rules: guest booking, Patient-only public registration, scoped receptionist authorization, historical queues, and no billing/payments. Usage and reproducible verification commands are in `README.md`.
