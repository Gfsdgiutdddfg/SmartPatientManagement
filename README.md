# Smart Patient Management System

**Java 17+ · Swing · FlatLaf · SQLite · JDBC · Maven**

## Login screen behavior
- The demo credential block has been removed from the login screen.
- Selecting **PATIENT** shows **Create patient account**.
- Selecting **DOCTOR** shows **Create doctor account**. Doctor registration creates a doctor login, profile, specialty and seven-day default availability.
- Selecting **ADMIN** disables public registration and explains that administrator accounts are managed by the hospital. This prevents anyone from creating an admin account from the public login screen.
- Login accepts username or email and validates the selected role.
- Press Enter in the username/password fields to continue.

## Demo credentials
| Role | Username | Password |
|---|---|---|
| Administrator | `admin` | `Admin@123` |
| Doctor | `doctor` | `Doctor@123` |
| Patient | `patient` | `Patient@123` |
| Seed doctors | `doctor002` to `doctor100` | `Doctor@123` |

## Doctor workspace
After signing in as a doctor, the dashboard shows:
- Number of appointments assigned to that doctor.
- Upcoming booked appointments.
- Completed consultations.
- Recent/upcoming patient bookings with patient name, appointment date/time and status.
- Appointment list with actions to cancel a booking or mark it completed.

The appointment query is scoped to the logged-in doctor's user ID. It does not display another doctor's bookings.

## Build and run on macOS
Requirements: JDK 17+ and Maven 3.9+.
```bash
chmod +x launch.command
mvn clean verify
mvn clean package
java -jar target/smart-patient-management.jar
```
The first Maven build needs network access to resolve dependencies. The app creates `data/smart_patient.db`, `uploads/`, `exports/`, and `backups/` on startup.

## Preserve existing data
Before replacing files, back up `data/smart_patient.db` and `uploads/`. Startup initialization is intended to preserve existing accounts and appointments and add missing demo data without overwriting existing weekly availability.

## Important
This is a student project, not certified medical software. Do not use it with real patient data or for clinical decisions without professional security, privacy and clinical-safety review. Some advanced requirements from the initial wish list may remain outside this implementation.
