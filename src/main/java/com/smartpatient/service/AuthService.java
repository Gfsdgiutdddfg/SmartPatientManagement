package com.smartpatient.service;

import com.smartpatient.config.Database;
import com.smartpatient.model.SessionUser;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

public class AuthService {
    public SessionUser login(String username, String password) throws SQLException {
        return login(username, password, null);
    }

    /** Authenticates credentials and, when supplied, enforces the selected login role. */
    public SessionUser login(String username, String password, String expectedRole) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) return null;
        String identity = username.trim();
        String sql = "SELECT id,username,password_hash,role,full_name,email FROM users " +
                "WHERE (lower(username)=lower(?) OR lower(email)=lower(?)) AND active=1";
        try (Connection c = Database.connect(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, identity);
            p.setString(2, identity);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                String storedRole = r.getString("role");
                if (expectedRole != null && !expectedRole.equalsIgnoreCase(storedRole)) {
                    throw new SecurityException("This account is registered as " + storedRole + ". Select the correct role and try again.");
                }
                boolean passwordMatches;
                try { passwordMatches = BCrypt.checkpw(password, r.getString("password_hash")); }
                catch (IllegalArgumentException malformedHash) { passwordMatches = false; }
                if (!passwordMatches) return null;
                SessionUser u = new SessionUser(r.getInt("id"), r.getString("username"), storedRole,
                        r.getString("full_name"), r.getString("email"));
                try (PreparedStatement a = c.prepareStatement(
                        "INSERT INTO activity_logs(user_id,action,details) VALUES(?,?,?)")) {
                    a.setInt(1, u.id()); a.setString(2, "LOGIN"); a.setString(3, "Successful " + storedRole + " login");
                    a.executeUpdate();
                }
                return u;
            }
        }
    }

    public void register(String username, String password, String name, String email) throws Exception {
        if (username == null || username.isBlank() || name == null || name.isBlank() ||
                email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ||
                password == null || password.length() < 8)
            throw new IllegalArgumentException("Enter a name, username, valid email and a password of at least 8 characters.");
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO users(username,password_hash,role,full_name,email) VALUES(?,?,'PATIENT',?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, username.trim());
                p.setString(2, BCrypt.hashpw(password, BCrypt.gensalt(11)));
                p.setString(3, name.trim());
                p.setString(4, email.trim().toLowerCase());
                p.executeUpdate();
                try (ResultSet r = p.getGeneratedKeys(); PreparedStatement x = c.prepareStatement("INSERT INTO patients(user_id) VALUES(?)")) {
                    if (!r.next()) throw new SQLException("Could not create patient account.");
                    x.setInt(1, r.getInt(1)); x.executeUpdate();
                }
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
}
