package com.smartpatient;

import com.smartpatient.config.Database;
import com.smartpatient.service.AuthService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseIntegrationTest {
    @BeforeAll static void initializeDatabase() throws Exception { Database.initialize(); }

    @Test void demoAdminAndDoctorCanAuthenticate() throws Exception {
        AuthService auth = new AuthService();
        assertEquals("ADMIN", auth.login("admin", "Admin@123").role());
        assertEquals("DOCTOR", auth.login("doctor", "Doctor@123").role());
        assertEquals("PATIENT", auth.login("patient", "Patient@123").role());
    }

    @Test void databaseContainsOneHundredDemoDoctors() throws Exception {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM doctors")) {
            assertTrue(r.next());
            assertTrue(r.getInt(1) >= 100, "Expected at least 100 demo doctors");
        }
    }

    @Test void foreignKeysAreEnabledOnEveryConnection() throws Exception {
        try (Connection c = Database.connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(r.next());
            assertEquals(1, r.getInt(1));
        }
    }
}
