package com.smartpatient.config;

import org.mindrot.jbcrypt.BCrypt;
import java.nio.file.*;
import java.sql.*;

public final class Database {
    private static final String URL = "jdbc:sqlite:data/smart_patient.db";
    private Database() {}
    public static Connection connect() throws SQLException {
        Connection c=DriverManager.getConnection(URL);
        try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA busy_timeout=5000");}
        return c;
    }
    public static void initialize() throws Exception {
        Files.createDirectories(Path.of("data")); Files.createDirectories(Path.of("uploads")); Files.createDirectories(Path.of("exports")); Files.createDirectories(Path.of("backups"));
        try(Connection c=connect(); Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('PATIENT','DOCTOR','ADMIN')), full_name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, active INTEGER NOT NULL DEFAULT 1, created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS specializations(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS patients(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER UNIQUE NOT NULL REFERENCES users(id),phone TEXT,age INTEGER,gender TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS doctors(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER UNIQUE NOT NULL REFERENCES users(id),specialization_id INTEGER REFERENCES specializations(id),qualification TEXT,experience INTEGER,fee REAL,rating REAL DEFAULT 4.5,active INTEGER DEFAULT 1)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS doctor_availability(id INTEGER PRIMARY KEY AUTOINCREMENT,doctor_id INTEGER NOT NULL REFERENCES doctors(id),day_of_week INTEGER NOT NULL,start_time TEXT NOT NULL,end_time TEXT NOT NULL,slot_minutes INTEGER DEFAULT 30,UNIQUE(doctor_id,day_of_week,start_time))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS appointments(id INTEGER PRIMARY KEY AUTOINCREMENT,appointment_code TEXT UNIQUE NOT NULL,patient_id INTEGER NOT NULL REFERENCES patients(id),doctor_id INTEGER NOT NULL REFERENCES doctors(id),appointment_date TEXT NOT NULL,slot_time TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'BOOKED',concern TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP,UNIQUE(doctor_id,appointment_date,slot_time))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS medical_files(id INTEGER PRIMARY KEY AUTOINCREMENT,patient_id INTEGER NOT NULL REFERENCES patients(id),uploaded_by INTEGER REFERENCES users(id),category TEXT NOT NULL,original_name TEXT NOT NULL,stored_path TEXT NOT NULL,mime_type TEXT,size_bytes INTEGER,uploaded_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS medical_records(id INTEGER PRIMARY KEY AUTOINCREMENT,patient_id INTEGER NOT NULL REFERENCES patients(id),doctor_id INTEGER NOT NULL REFERENCES doctors(id),appointment_id INTEGER REFERENCES appointments(id),consultation_date TEXT NOT NULL,diagnosis TEXT,notes TEXT,follow_up_date TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS prescriptions(id INTEGER PRIMARY KEY AUTOINCREMENT,record_id INTEGER NOT NULL REFERENCES medical_records(id),created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS prescription_items(id INTEGER PRIMARY KEY AUTOINCREMENT,prescription_id INTEGER NOT NULL REFERENCES prescriptions(id),medicine TEXT NOT NULL,dosage TEXT,instructions TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS notifications(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER REFERENCES users(id),message TEXT NOT NULL,read_flag INTEGER DEFAULT 0,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS reviews(id INTEGER PRIMARY KEY AUTOINCREMENT,patient_id INTEGER REFERENCES patients(id),doctor_id INTEGER REFERENCES doctors(id),rating INTEGER CHECK(rating BETWEEN 1 AND 5),comment TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS activity_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER REFERENCES users(id),action TEXT NOT NULL,details TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS password_reset_tokens(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER REFERENCES users(id),token_hash TEXT NOT NULL,expires_at TEXT NOT NULL,used INTEGER DEFAULT 0)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_appt_patient ON appointments(patient_id,appointment_date)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_appt_doctor ON appointments(doctor_id,appointment_date)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_files_patient ON medical_files(patient_id)");
        }
        migrateSpecializationsNameColumn();
        seed();
    }
    /**
     * Repairs legacy databases where the specialization label column was named
     * specialty/specialization instead of name. CREATE TABLE IF NOT EXISTS does
     * not change an existing table, so this migration must run before seed().
     */
    private static void migrateSpecializationsNameColumn() throws SQLException {
        try (Connection c = connect()) {
            java.util.Set<String> columns = new java.util.HashSet<>();
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(specializations)")) {
                while (r.next()) columns.add(r.getString("name").toLowerCase(java.util.Locale.ROOT));
            }
            if (columns.contains("name")) return;

            String legacy = null;
            for (String candidate : new String[]{"specialty_name", "specialization_name", "specialty", "specialization", "speciality"}) {
                if (columns.contains(candidate)) { legacy = candidate; break; }
            }
            try (Statement s = c.createStatement()) {
                if (legacy != null) {
                    s.executeUpdate("ALTER TABLE specializations RENAME COLUMN " + legacy + " TO name");
                } else {
                    s.executeUpdate("ALTER TABLE specializations ADD COLUMN name TEXT");
                    s.executeUpdate("UPDATE specializations SET name='General Physician' WHERE name IS NULL OR trim(name)=''");
                }
                s.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_specializations_name ON specializations(name)");
            }
        }
    }

    private static void seed() throws Exception {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String[] specs = {"Cardiologist", "Dermatologist", "Ophthalmologist", "Orthopedic", "Dentist",
                        "Gastroenterologist", "Neurologist", "ENT Specialist", "Pediatrician", "General Physician"};
                for (String sp : specs) try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO specializations(name) VALUES(?)")) {
                    p.setString(1, sp); p.executeUpdate();
                }

                // Seed demo identities even when an older database already contains users.
                ensureUser(c, "admin", "Admin@123", "ADMIN", "Hospital Administrator", "admin@hospital.com");
                int patientId = ensureUser(c, "patient", "Patient@123", "PATIENT", "Aarav Sharma", "patient@hospital.com");
                ensurePatient(c, patientId);
                ensureUser(c, "doctor", "Doctor@123", "DOCTOR", "Aarav Sharma", "doctor@hospital.com");

                String[] first = {"Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh", "Krishna", "Ishaan", "Rohan", "Ananya", "Diya", "Aadhya", "Saanvi", "Myra", "Ira", "Meera", "Kavya", "Sara", "Kiara"};
                String[] last = {"Sharma", "Verma", "Reddy", "Patel", "Iyer", "Gupta", "Nair", "Singh", "Rao", "Mehta"};
                String[] qual = {"MBBS, MD", "MBBS, MS", "MBBS, DM", "MBBS, DNB"};
                for (int i = 0; i < 100; i++) {
                    String uname = i == 0 ? "doctor" : String.format("doctor%03d", i + 1);
                    String name = first[i % first.length] + " " + last[(i * 7) % last.length];
                    String email = i == 0 ? "doctor@hospital.com" : uname + "@hospital.com";
                    int uid = ensureUser(c, uname, "Doctor@123", "DOCTOR", name, email);
                    int specializationId = specializationId(c, specs[i % specs.length]);
                    int did = ensureDoctor(c, uid, specializationId, qual[i % qual.length], 2 + (i * 3) % 23, 400 + (i % 8) * 100);
                    ensureAvailability(c, did);
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,message) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE user_id=? AND message=?)")) {
                    p.setInt(1, patientId); p.setString(2, "Welcome to Smart Patient. Browse doctors and book your first appointment.");
                    p.setInt(3, patientId); p.setString(4, "Welcome to Smart Patient. Browse doctors and book your first appointment."); p.executeUpdate();
                }
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    private static int ensureUser(Connection c, String username, String password, String role, String name, String email) throws Exception {
        try (PreparedStatement p = c.prepareStatement("SELECT id,role FROM users WHERE username=?")) {
            p.setString(1, username);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    if (!role.equals(r.getString("role"))) throw new SQLException("Username '" + username + "' is already used by a different account role. Rename that account before using the demo login.");
                    return r.getInt("id");
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO users(username,password_hash,role,full_name,email) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, username); p.setString(2, BCrypt.hashpw(password, BCrypt.gensalt(11)));
            p.setString(3, role); p.setString(4, name); p.setString(5, email); p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) { if (r.next()) return r.getInt(1); }
        }
        throw new SQLException("Unable to initialize demo account: " + username);
    }

    private static int specializationId(Connection c, String name) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM specializations WHERE name=?")) {
            p.setString(1, name); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("Missing specialization " + name); return r.getInt(1); }
        }
    }

    private static void ensurePatient(Connection c, int userId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO patients(user_id,phone,age,gender) VALUES(?,?,?,?)")) {
            p.setInt(1, userId); p.setString(2, "9876543210"); p.setInt(3, 24); p.setString(4, "Prefer not to say"); p.executeUpdate();
        }
    }

    private static int ensureDoctor(Connection c, int userId, int specializationId, String qualification, int experience, double fee) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO doctors(user_id,specialization_id,qualification,experience,fee,rating) VALUES(?,?,?,?,?,4.5)")) {
            p.setInt(1, userId); p.setInt(2, specializationId); p.setString(3, qualification); p.setInt(4, experience); p.setDouble(5, fee); p.executeUpdate();
        }
        try (PreparedStatement p = c.prepareStatement("SELECT id FROM doctors WHERE user_id=?")) {
            p.setInt(1, userId); try (ResultSet r = p.executeQuery()) { if (!r.next()) throw new SQLException("Doctor profile missing for user " + userId); return r.getInt(1); }
        }
    }

    private static void ensureAvailability(Connection c, int doctorId) throws SQLException {
        for (int day = 1; day <= 7; day++) try (PreparedStatement p = c.prepareStatement(
                "INSERT OR IGNORE INTO doctor_availability(doctor_id,day_of_week,start_time,end_time,slot_minutes) VALUES(?,?, '09:00','17:00',30)")) {
            p.setInt(1, doctorId); p.setInt(2, day); p.executeUpdate();
        }
    }
    private static int addUser(Connection c,String username,String password,String role,String name,String email)throws Exception{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password_hash,role,full_name,email) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            p.setString(1,username);p.setString(2,BCrypt.hashpw(password,BCrypt.gensalt(11)));p.setString(3,role);p.setString(4,name);p.setString(5,email);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){return r.next()?r.getInt(1):0;}
        }
    }
    private static int scalar(Connection c,String q)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){return r.next()?r.getInt(1):0;}}
}
