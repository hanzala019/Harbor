### Requirements For Hospital Appointment System

**Design Architecture**
  - Should be simple, straightforward, responsive, and easy to use for patients, receptionists, and admins.
  - Login/sign up is optional for patients.
  - Guest users can make appointments without creating an account.
  - The guest/no-login booking flow will be called **Quick Appointment**.
  - After a guest successfully confirms an appointment, the system should optionally offer **Save My Details / Register as Patient** so the guest can create a patient account using the information already entered for the appointment.
  - The registration prompt/modal must appear only after the appointment has been confirmed.
  - The registration modal can be closed or skipped without affecting the confirmed appointment.
  - Any user who registers normally from the public site is automatically registered with the **Patient** role.
  - Only admins can create **Receptionist** accounts.
  - Main user roles are:
    - Guest Patient
    - Registered Patient
    - Receptionist
    - Admin
  - Patients can look up doctors, filter specialists, view availability, and make an appointment.
  - Receptionists and admins must log in.
  - Receptionists update doctor information, schedules, status, and availability only for doctors they have permission to manage.
  - Appointment slots and serial numbers are created/calculated from doctor schedule, availability, current appointments, cancellations, and queue state.
  - There is no billing, service fee, payment gateway, or other monetary transaction in this system.

**Public / Patient Flow**
  - The landing page should contain a large hero banner.
  - The hero banner should use medical-themed illustrations, icons, shapes, equipment, hospital symbols, doctor/patient themed vector artwork, etc.
  - Do not use live-action or real human photographs in the hero banner.
  - At the center of the hero banner there should be a primary CTA button named **"Make An Appointment"**.
  - Below the hero banner, the landing page should contain information such as:
    - About the hospital
    - Hospital highlights
    - Patient reviews/testimonials
    - Medical services
    - Departments/specialties
    - Technologies and facilities available
    - Other useful hospital information
  - Clicking **"Make An Appointment"** takes the user to the appointment search page.

**Appointment Search Page**
  - Patients/guests should be able to search and filter doctors using:
    - Doctor name
    - Doctor type/specialty
    - Availability: Now
    - Availability: Tonight
    - Availability: Tomorrow
    - Custom date selection
  - Search/filter results should show a list of doctors.
  - Each doctor card/list item should display enough summary information to help the patient decide, such as:
    - Doctor name
    - Specialty
    - Department
    - Available date/time summary
    - Room/chamber information when applicable
    - Short doctor information/profile summary
  - Each result should contain a **"Select"** button.
  - Selecting a doctor should show a more detailed doctor view before booking.
  - The detailed doctor view should contain a **"Make An Appointment"** button.

**Appointment Details & Confirmation**
  - When an appointment slot is selected, the system should show:
    - Doctor name
    - Appointment date
    - Appointment time / estimated consultation time
    - Serial number
    - Room number
    - Doctor specialty/department
  - The patient must confirm the appointment after reviewing the above information.
  - Guest users must provide the following information before appointment confirmation:
    - Full name
    - Email
    - Age
    - Gender
    - Phone number
  - Registered patients should not be required to re-enter information already stored in their profile.
  - After appointment confirmation:
    - The appointment must be saved.
    - The patient should receive an email confirmation.
    - The email should include appointment information, serial number, doctor name, room number, date/time, and estimated arrival time.
  - For a guest patient, after confirmation is complete, show an optional registration modal with text explaining benefits such as:
    - Faster appointments later
    - No need to re-enter personal information
    - View upcoming appointments
    - View past appointment history
    - Manage profile information
  - The modal should contain:
    - **"Register / Save My Details"** button
    - Close/Skip option
  - Selecting **"Register / Save My Details"** should create a Patient account using the appointment information already entered and should not require the patient to enter the same information again.

**Registered Patient Pages**
  - Registered patients should have an appointment page/dashboard where they can view:
    - Upcoming appointments
    - Past appointments
    - Appointment status
    - Doctor information
    - Serial number
    - Date and time
  - Logged-in patients should have a profile page.
  - The profile page should be available from the user avatar/menu.
  - The profile should contain the patient's stored personal information and allow supported profile updates.

**Navbar**
  - The navbar should contain:
    - Hospital/site logo
    - Site/hospital name
    - Main navigation items as required
  - On the far right:
    - If not logged in: show **Login**.
    - If logged in: show the user's name and a circular avatar.
  - If the user has no profile image, the circular avatar should display the first letter of the user's name.
  - Hovering/clicking the avatar should expose profile/account navigation for logged-in patients.

**Authentication & Roles**
  - Public patients may use Quick Appointment without authentication.
  - Public registration always creates a user with the **Patient** role.
  - Receptionists cannot self-register.
  - Only Admin users can create Receptionist users.
  - Receptionists log in using email and password.
  - After successful receptionist login, automatically redirect them to the receptionist appointments page.
  - Role-based access control must be applied to all protected pages and backend endpoints.

**Receptionist**
  - Receptionists have two main additional pages:
    1. Appointments
    2. Doctors
  - Admin controls which doctors each receptionist can access.
  - Admin also controls whether a receptionist has view-only or edit permission for each doctor/schedule area.
  - Example:
    - A front desk receptionist may see all doctors and availability but cannot edit them.
    - A floor receptionist may see only doctors from that floor and may edit their calendar and availability.

**Receptionist - Appointments Page**
  - Receptionists can see appointments for doctors assigned to them.
  - They can filter appointments by:
    - Today
    - Previous dates
    - Future dates
    - Custom date/date range where required
    - Doctor
    - Appointment status
  - Receptionists can search appointments by:
    - Patient name
    - Patient phone number
  - The appointments page should use tabs:
    - **Ongoing**
    - **Visited**
    - **Did Not Show Up**
  - The appointment table should show at minimum:
    - SL / Serial No.
    - Patient Name
    - Dr. Name
  - Additional useful columns may include appointment time, phone number, room, and status.
  - Receptionists should be able to:
    - Mark a patient as **Visited / Checked Out** using a tick/check action.
    - Mark a patient as **Did Not Show Up** using a cross/no-show action.
    - See the current serial for each doctor.
    - See who is next in line for each doctor.
  - On the right side of the appointments page there should be a quick-view panel showing, for each accessible doctor:
    - Current serial number
    - Next patient name
    - Next patient serial number
  - The ongoing appointment list must always make the next patients in each doctor's queue easy to identify.

**Receptionist - Multi-Doctor Queue Ordering**
  - When a receptionist has access to multiple doctors, the Ongoing table should merge the doctors' queues using a **round-robin next-patient view**, not a global serial-number sort.
  - Each doctor contributes their current next pending patient first.
  - After showing the current next pending patient for each doctor, the table continues with the next pending patient for each doctor in the same manner.
  - The serial number remains local to that doctor's queue.
  - Example: if Dr-1 and Dr-2 are about to receive SL-1, while Dr-3 is currently about to receive SL-10, the table may appear as:
    - `SL 1, Ahmed, Dr-1`
    - `SL 1, Abrar, Dr-2`
    - `SL 10, Jamal, Dr-3`
    - `SL 2, Alam, Dr-1`
    - `SL 2, Fakir, Dr-2`
    - then continue with the next pending patient from each doctor's queue.
  - Cancelled, visited, and no-show appointments should not be treated as active pending positions in this merged queue.

**Receptionist - Doctors Page**
  - Receptionists can see doctors they are assigned permission to access.
  - For each doctor, receptionist can see:
    - Doctor profile information
    - Total number of appointments
    - Today's appointments
    - Future appointments
    - Calendar
    - Daily schedules
    - Available/unavailable dates
    - Available/unavailable time ranges
  - Depending on permission, receptionists can update:
    - Doctor information
    - Calendar days
    - Available time ranges per day
    - Unavailable time ranges per day
    - Whole-day availability/unavailability
  - The schedule UI should allow different working times for different calendar days.

**Doctor Schedule Changes / Unavailability**
  - If a doctor's time changes or the doctor becomes unavailable after patients have already booked:
    - Receptionist/admin should be able to mark the affected schedule as changed/unavailable.
    - The system should identify affected appointments.
    - Receptionist/admin should have an option to send an email notification to all affected patients.
  - The notification should explain:
    - The doctor is unavailable or the schedule changed.
    - Their current appointment is affected.
    - The next available date/time when known.
    - Whether they want to move their appointment to the doctor's next available schedule.
  - When affected patients are moved to the next available schedule, their new serial ordering should preserve their relative order from the previous appointment queue.
  - Cancelled appointments between affected patients should be removed before new serials are generated.
  - Example:
    - Patient A previously had SL-10.
    - Patient B previously had SL-15.
    - Patients between them cancelled or are otherwise no longer part of the transferred queue.
    - If A is now first among the transferred active patients, A gets new `SL-1`.
    - If B is second among the transferred active patients, B gets new `SL-2`.
  - This re-serializing must preserve relative order among all active transferred patients.

**Admin**
  - Admin can view all appointments across the hospital/system.
  - Admin can view all patients.
  - Admin can view all doctors.
  - Admin can view and manage doctor schedules, calendars, and availability.
  - Admin can create Receptionist accounts.
  - Admin can assign doctors to receptionists.
  - Admin can control receptionist permissions, including:
    - Which doctors the receptionist can view.
    - Which doctors the receptionist can edit.
    - Whether the receptionist can update doctor information.
    - Whether the receptionist can update schedules/calendars.
    - Whether the receptionist can change availability.
  - Admin should be able to review receptionist access assignments and change/revoke them.

**Appointment Statuses**
  - The system should support clear appointment states such as:
    - Confirmed / Upcoming
    - Ongoing / Waiting
    - Visited / Completed
    - Did Not Show Up
    - Cancelled
    - Reschedule Pending, when applicable
    - Rescheduled, when applicable

**Serial / Slot Logic**
  - Serial numbers are doctor-specific for a particular date/session/schedule.
  - A serial number must be assigned only after an appointment is successfully confirmed.
  - Serial numbers should follow the doctor's active appointment order.
  - Cancelled appointments must not block a serial when a queue is recalculated for a transferred/rescheduled session.
  - No-show and visited appointments remain historical records but should no longer appear as upcoming queue items.
  - Estimated arrival/consultation time should be calculated from the doctor's schedule and the patient's serial/slot position.
  - The system should prevent double-booking the same doctor slot.

**Email Notifications**
  - Send an appointment confirmation email after successful booking.
  - Send schedule-change/unavailability emails when triggered by an authorized receptionist/admin.
  - Rescheduling emails should include the updated date, time, serial number, room, and doctor information when a new appointment is accepted/created.
  - Email actions should not expose other patients' information.

**TechStack**
 1.*Frontend*
   - Language: TypeScript / JavaScript
   - JS Framework: React
   - Build Tool: Vite
   - UI Component Library: shadcn/ui
   - Styling: Tailwind CSS
   - Routing: React Router
   - Forms/Validation: React Hook Form + Zod
   - API Communication: Fetch API or Axios
   - Calendar/Date UI: React-compatible calendar/date components integrated with shadcn/ui

 2.*Backend*
   - Language: Java
   - Framework: Spring Boot
   - API: REST API
   - Authentication/Authorization: Spring Security with role-based access control
   - Database: PostgreSQL
   - ORM: Spring Data JPA / Hibernate
   - Email Service: Spring Mail or an external SMTP/email provider
   - Database migrations: Flyway or Liquibase

 3.*Docker*
   - OS: Linux
   - Minimum target RAM: 4 GB
   - Frontend, backend, and PostgreSQL should be deployable using Docker containers.
   - Docker Compose may be used for local/development deployment.

**UI/UX**
  - Use shadcn/ui components as the main component system.
  - Visual style should be clean, modern, calm, and appropriate for a hospital/medical application.
  - Keep patient booking steps short and obvious.
  - Use clear status badges for Confirmed, Ongoing, Visited, No Show, Cancelled, and Rescheduled states.
  - Use tables for receptionist/admin operational views and cards for patient-facing doctor browsing where appropriate.
  - Important appointment information such as doctor, serial, room, date, and time should be visually prominent before confirmation.
  - The guest registration prompt should never block or invalidate an already-confirmed appointment.
  - Use responsive layouts for desktop, tablet, and mobile.
  - Use accessible labels, buttons, dialogs, forms, keyboard navigation, and sufficient contrast.
  - Use loading, empty, error, and success states for appointment searches, bookings, table operations, and schedule changes.
  - Use confirmation dialogs for destructive or high-impact actions such as cancelling an appointment, marking a no-show, or making a doctor unavailable when appointments already exist.

**Suggested Main Pages / Routes**
  - Public:
    - Home
    - Doctors / Find a Doctor
    - Appointment Search
    - Doctor Details
    - Appointment Details / Confirmation
    - Login
    - Register
  - Registered Patient:
    - My Appointments
    - Profile
  - Receptionist:
    - Appointments
    - Doctors
  - Admin:
    - Dashboard
    - All Appointments
    - Patients
    - Doctors
    - Doctor Schedules / Availability
    - Receptionists
    - Receptionist Access / Permissions

**Important Business Rules**
  - Guest booking must remain fully supported.
  - Registration must never be required to complete an appointment.
  - Guest-to-patient registration happens only after appointment confirmation when the patient chooses it.
  - Public registration creates Patient users only.
  - Receptionist accounts can only be created by Admin users.
  - Receptionists can only view/edit doctors permitted by Admin.
  - Receptionist login redirects directly to the receptionist Appointments page.
  - Doctor schedule changes affecting existing appointments must clearly identify affected patients before notifications or rescheduling actions are performed.
  - Queue transfer/rescheduling must preserve the relative ordering of affected active patients while recalculating their serial numbers for the new schedule.
  - No billing or payment functionality should be implemented.
