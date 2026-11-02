package com.smartpatient;

import com.smartpatient.config.Database;
import com.smartpatient.model.SessionUser;
import com.smartpatient.service.AuthService;
import com.smartpatient.service.HospitalService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountWorkflowTest {
    @BeforeAll static void init() throws Exception { Database.initialize(); }

    @Test void loginEnforcesSelectedRole() throws Exception {
        AuthService auth = new AuthService();
        assertNotNull(auth.login("admin", "Admin@123", "ADMIN"));
        assertNotNull(auth.login("doctor", "Doctor@123", "DOCTOR"));
        assertNotNull(auth.login("patient", "Patient@123", "PATIENT"));
        assertThrows(SecurityException.class, () -> auth.login("doctor", "Doctor@123", "ADMIN"));
    }

    @Test void administratorCanCreateDoctorAndDoctorCanLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "newdoc_" + suffix;
        SessionUser admin = new SessionUser(1, "admin", "ADMIN", "Admin", "admin@hospital.com");
        HospitalService service = new HospitalService();
        service.createDoctor(admin, username, "NewDoctor@123", "Test Doctor", username + "@example.test",
                "Cardiologist", "MBBS, MD", 5, 650.0);
        SessionUser doctor = new AuthService().login(username, "NewDoctor@123", "DOCTOR");
        assertNotNull(doctor);
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM doctors d JOIN users u ON u.id=d.user_id WHERE u.username=?")) {
            p.setString(1, username); try (ResultSet r = p.executeQuery()) { assertTrue(r.next()); assertEquals(1, r.getInt(1)); }
        }
    }

    @Test void administratorCanCreateAnotherAdminAndNewAdminCanLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "newadmin_" + suffix;
        SessionUser admin = new SessionUser(1, "admin", "ADMIN", "Admin", "admin@hospital.com");
        new HospitalService().createAdmin(admin, username, "NewAdmin@123", "Test Admin", username + "@example.test");
        assertNotNull(new AuthService().login(username, "NewAdmin@123", "ADMIN"));
    }

    @Test void nonAdminCannotCreatePrivilegedAccounts() {
        SessionUser doctor = new SessionUser(2, "doctor", "DOCTOR", "Doctor", "doctor@hospital.com");
        HospitalService service = new HospitalService();
        assertThrows(SecurityException.class, () -> service.createAdmin(doctor, "xadmin", "Password@123", "X", "x@example.test"));
        assertThrows(SecurityException.class, () -> service.createDoctor(doctor, "xdoctor", "Password@123", "X", "y@example.test", "Dentist", "BDS", 1, 100));
    }
    @Test void doctorCanSelfRegisterAndSignIn() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "selfdoc_" + suffix;
        HospitalService service = new HospitalService();
        service.registerDoctor(username, "SelfDoctor@123", "Self Registered Doctor",
                username + "@example.test", "Cardiologist", "MBBS, MD", 2, 500);
        SessionUser doctor = new AuthService().login(username, "SelfDoctor@123", "DOCTOR");
        assertNotNull(doctor);
        assertEquals("DOCTOR", doctor.role());
        assertThrows(SecurityException.class, () -> new AuthService().login(username, "SelfDoctor@123", "ADMIN"));
    }

    @Test void doctorAppointmentListIsScopedToLoggedInDoctor() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "scope_doc_" + suffix;
        HospitalService service = new HospitalService();
        service.registerDoctor(username, "ScopeDoctor@123", "Scoped Doctor",
                username + "@example.test", "Dentist", "BDS", 3, 400);
        SessionUser doctor = new AuthService().login(username, "ScopeDoctor@123", "DOCTOR");
        assertNotNull(doctor);
        int doctorId;
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(
                "SELECT id FROM doctors WHERE user_id=?")) {
            p.setInt(1, doctor.id());
            try (ResultSet r = p.executeQuery()) { assertTrue(r.next()); doctorId = r.getInt(1); }
        }
        SessionUser patient = new AuthService().login("patient", "Patient@123", "PATIENT");
        String code = service.book(patient, doctorId, java.time.LocalDate.now().plusDays(7), "09:00", "General consultation");
        assertTrue(service.appointments(doctor).stream().anyMatch(a -> a[0].equals(code) && a[3].contains("Aarav")));
        service.updateAppointmentStatus(doctor, code, "COMPLETED");
        assertTrue(service.appointments(doctor).stream().anyMatch(a -> a[0].equals(code) && a[5].equals("COMPLETED")));
    }

    @Test void specializationsSchemaHasNameColumnAfterInitialization() throws Exception {
        try (Connection c = Database.connect();
             PreparedStatement p = c.prepareStatement("PRAGMA table_info(specializations)");
             ResultSet r = p.executeQuery()) {
            boolean found = false;
            while (r.next()) if ("name".equalsIgnoreCase(r.getString("name"))) found = true;
            assertTrue(found, "specializations table must expose the name column used by doctor queries");
        }
    }
}
