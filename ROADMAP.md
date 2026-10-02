# V1 complete · V2 development roadmap

## V1 milestone

V1 establishes the working hospital appointment system described in `requirements.md`:

- Guest Quick Appointment, optional post-confirmation patient registration, patient profiles, appointment history, and cancellation.
- Admin doctor/receptionist management and doctor-scoped, independently enforced receptionist edit permissions.
- Date/session-local serials, round-robin ongoing queues, visited/no-show history, affected-patient review, notifications, and ordered queue transfers.
- Single-session and weekday/time-range schedule generation with preview, atomic overlap validation, and duplicate prevention.
- Filtered multi-doctor assignments, assignment/patient table filters, and persistent preview/save actions in the weekly schedule dialog.
- PostgreSQL/Flyway persistence, expiring bearer authentication, transactional email outbox, native Windows development, and Docker Compose deployment configuration.

Verification at the milestone: 13 backend integration tests and 2 frontend queue tests passed; the production frontend build and native Windows/Java 19 checks passed. Browser checks against isolated PostgreSQL covered booking/registration, receptionist permissions, weekly scheduling, bulk assignments, filtering, and mobile layouts. A real booking confirmation was received through Gmail. Compose configuration validates; full container execution remains to be verified.

## V2 scope

All 18 enhancements below are included in V2. Unchecked items are planned work, not completed features. Existing V1 controls are the starting point for the expanded workflows.

### Local development

- [ ] **1. Single-command local startup — `start-local.ps1`.** Check Java, Node, and PostgreSQL; load `.env`; bootstrap dependencies when needed; start backend/frontend with matching available ports; print the application URL. Support clean shutdown of processes started by the launcher, without stopping unrelated services. Native startup must not require Docker.

### Scheduling and navigation

- [ ] **2. Calendar with a week view.** Display working hours, unavailable periods, and booking counts; navigate between weeks/doctors and open the relevant session actions without entering each date separately.
- [ ] **3. Copy a week and reusable schedule templates.** Save named weekday/time-range patterns and apply them to another bounded date window. Preserve the current preview, overlap checks, permissions, and protection of booked sessions.
- [ ] **4. Searchable dropdowns.** Use keyboard-accessible searchable selectors for doctors and receptionists, while retaining multi-selection where appropriate.
- [ ] **5. Table sorting and pagination.** Add practical sorting and pagination to staff/admin tables. Keep the Ongoing queue's doctor-by-doctor round-robin ordering; do not replace it with a global serial sort.
- [ ] **6. Remember filters.** Restore selected doctors, dates, searches, and table filters when returning to a page. Provide a visible reset action and scope saved preferences to the signed-in account.
- [ ] **7. Consistent success feedback.** Show clear results for scheduling, assignments, cancellations, and other mutations, including affected counts and relevant details. Keep loading, empty, and error feedback consistent.

### Booking and communications

- [ ] **8. Email status and resend controls.** Let authorized staff see queued/sent/failed confirmation status and resend a confirmation. Explain that SMTP acceptance is not proof of inbox delivery; retain individual-recipient privacy and transactional queuing.
- [ ] **9. Private guest appointment management links.** Add secure links to confirmation emails so guests can view or cancel their own appointments without registering. Limit each link to its appointment and support expiry/revocation.
- [ ] **10. Duplicate-booking detection.** Alert staff when the same patient already has an active appointment with the same doctor on the same date. Define reliable matching for guests and registered patients, and distinguish this from the existing same-slot conflict protection.
- [ ] **11. Date-aware upcoming/history classification.** Use date/time as well as status so an unprocessed past appointment does not remain under Upcoming. Preserve its actual status until an authorized action changes it.
- [ ] **12. Explicit hospital timezone.** Configure and display the hospital's timezone, with consistent appointment interpretation in Windows and Docker. Make timezone handling clear in search filters, previews, confirmations, and emails.

### Staff and account operations

- [ ] **13. Live queue refresh.** Refresh queues automatically, show the last successful update time, keep a manual refresh action, and preserve filters. Prevent stale responses from replacing newer queue data.
- [ ] **14. Receptionist account management.** Allow Admin to disable accounts, reset passwords, and revoke active login tokens. Enforce disabled status on backend authentication and protected actions.
- [ ] **15. Admin password change.** Add an authenticated account-settings workflow for changing the Admin password without database tools, including current-password verification and a defined session-revocation policy.
- [ ] **16. Audit history.** Record actor, time, affected record, and action for schedule changes, transfers, permission changes, and appointment status updates. Provide an authorized, searchable history without recording passwords or raw tokens.
- [ ] **17. Selective patient transfers.** Let staff choose the affected active patients who agreed to move. Preserve their relative order, leave unselected patients in place, enforce destination capacity atomically, and notify only the moved patients with their updated details.
- [ ] **18. Assignment permission presets.** Provide View only, Scheduling desk, and Full doctor management presets, with visible permissions and individual overrides before saving. Apply presets consistently to bulk selections.

## Delivery expectations

- Implement every checklist item; the recommended order is a sequencing aid, not a reduction in scope.
- Suggested sequence: local launcher and navigation → calendar/templates → email and account tools → booking refinements, audit, and selective transfers.
- Retain `requirements.md` product rules, including guest booking, Patient-only public registration, scoped receptionist access, queue ordering, and no billing/payments.
- Use forward Flyway migrations for new persisted data and preserve existing accounts, appointments, schedules, and outbox jobs.
- Verify changed business rules with meaningful tests and verify interactive workflows in the browser. Complete a full Docker startup check as part of V2 deployment verification.
- Mark items complete only after implementation and verification, documenting how to use the finished feature in `README.md`.
