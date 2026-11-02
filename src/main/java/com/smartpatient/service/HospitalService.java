package com.smartpatient.service;
import com.smartpatient.config.Database;import com.smartpatient.model.SessionUser;import java.sql.*;import java.time.*;import java.util.*;import java.nio.file.*;import java.security.SecureRandom;
public class HospitalService {
 public record Doctor(int id,String name,String specialty,String qualification,int experience,double fee,double rating) {public String toString(){return name+"  •  "+specialty+"  •  ₹"+(int)fee+"  •  "+experience+" yrs";}}
 public List<Doctor> doctors(String specialty)throws SQLException{List<Doctor> out=new ArrayList<>();String q="SELECT d.id,u.full_name,s.name,d.qualification,d.experience,d.fee,d.rating FROM doctors d JOIN users u ON u.id=d.user_id JOIN specializations s ON s.id=d.specialization_id WHERE d.active=1 AND (?='All Specialties' OR s.name=?) ORDER BY s.name,u.full_name";try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(q)){p.setString(1,specialty);p.setString(2,specialty);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new Doctor(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getDouble(6),r.getDouble(7)));}}return out;}
 public List<String> specialties()throws SQLException{List<String>a=new ArrayList<>(List.of("All Specialties"));try(Connection c=Database.connect();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT name FROM specializations ORDER BY name")){while(r.next())a.add(r.getString(1));}return a;}
 public List<String> slots(int doctorId,LocalDate date)throws SQLException{List<String> out=new ArrayList<>();if(date.isBefore(LocalDate.now()))return out;int dow=date.getDayOfWeek().getValue();try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT start_time,end_time,slot_minutes FROM doctor_availability WHERE doctor_id=? AND day_of_week=?")){p.setInt(1,doctorId);p.setInt(2,dow);try(ResultSet r=p.executeQuery()){while(r.next()){LocalTime t=LocalTime.parse(r.getString(1)),end=LocalTime.parse(r.getString(2));int mins=r.getInt(3);while(!t.plusMinutes(mins).isAfter(end)){String v=t.toString();try(PreparedStatement z=c.prepareStatement("SELECT 1 FROM appointments WHERE doctor_id=? AND appointment_date=? AND slot_time=? AND status='BOOKED'")){z.setInt(1,doctorId);z.setString(2,date.toString());z.setString(3,v);try(ResultSet a=z.executeQuery()){if(!a.next()&&(!date.equals(LocalDate.now())||t.isAfter(LocalTime.now())))out.add(v);}}t=t.plusMinutes(mins);}}}}return out;}
 public String book(SessionUser user,int doctorId,LocalDate date,String time,String concern)throws Exception{if(!user.role().equals("PATIENT"))throw new IllegalArgumentException("Only patient accounts can book appointments.");if(date.isBefore(LocalDate.now()))throw new IllegalArgumentException("Choose a future date.");String code="SP-"+Long.toString(System.currentTimeMillis(),36).toUpperCase()+"-"+new SecureRandom().nextInt(9000+1000);try(Connection c=Database.connect()){c.setAutoCommit(false);try{int pid;try(PreparedStatement p=c.prepareStatement("SELECT id FROM patients WHERE user_id=?")){p.setInt(1,user.id());try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Patient profile not found");pid=r.getInt(1);}}try(PreparedStatement p=c.prepareStatement("INSERT INTO appointments(appointment_code,patient_id,doctor_id,appointment_date,slot_time,concern) VALUES(?,?,?,?,?,?)")){p.setString(1,code);p.setInt(2,pid);p.setInt(3,doctorId);p.setString(4,date.toString());p.setString(5,time);p.setString(6,concern);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("INSERT INTO notifications(user_id,message) VALUES(?,?)")){p.setInt(1,user.id());p.setString(2,"Appointment confirmed: "+code+" on "+date+" at "+time);p.executeUpdate();}c.commit();return code;}catch(Exception e){c.rollback();if(e.getMessage()!=null&&e.getMessage().toLowerCase().contains("unique"))throw new IllegalStateException("This slot was just booked. Please choose another slot.");throw e;}finally{c.setAutoCommit(true);}}}
 public int patientId(int userId)throws SQLException{try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("SELECT id FROM patients WHERE user_id=?")){p.setInt(1,userId);try(ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):-1;}}}
 public List<String[]> appointments(SessionUser u)throws SQLException{List<String[]> a=new ArrayList<>();String q=u.role().equals("PATIENT")?"SELECT a.appointment_code,a.appointment_date,a.slot_time,du.full_name,s.name,a.status FROM appointments a JOIN patients p ON p.id=a.patient_id JOIN doctors d ON d.id=a.doctor_id JOIN users du ON du.id=d.user_id JOIN specializations s ON s.id=d.specialization_id WHERE p.user_id=? ORDER BY a.appointment_date DESC":u.role().equals("ADMIN")?"SELECT a.appointment_code,a.appointment_date,a.slot_time,pu.full_name || ' / ' || du.full_name,s.name,a.status FROM appointments a JOIN doctors d ON d.id=a.doctor_id JOIN users du ON du.id=d.user_id JOIN specializations s ON s.id=d.specialization_id JOIN patients p ON p.id=a.patient_id JOIN users pu ON pu.id=p.user_id ORDER BY a.appointment_date DESC":"SELECT a.appointment_code,a.appointment_date,a.slot_time,pu.full_name,s.name,a.status FROM appointments a JOIN doctors d ON d.id=a.doctor_id JOIN users du ON du.id=d.user_id JOIN specializations s ON s.id=d.specialization_id JOIN patients p ON p.id=a.patient_id JOIN users pu ON pu.id=p.user_id WHERE du.id=? ORDER BY a.appointment_date DESC";try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(q)){if(!u.role().equals("ADMIN"))p.setInt(1,u.id());try(ResultSet r=p.executeQuery()){while(r.next())a.add(new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)});}}return a;}
 public void cancel(SessionUser u,String code)throws SQLException{
  String q;
  if(u.role().equals("PATIENT")) q="UPDATE appointments SET status='CANCELLED' WHERE appointment_code=? AND patient_id=(SELECT id FROM patients WHERE user_id=?) AND status='BOOKED'";
  else if(u.role().equals("DOCTOR")) q="UPDATE appointments SET status='CANCELLED' WHERE appointment_code=? AND doctor_id=(SELECT id FROM doctors WHERE user_id=?) AND status='BOOKED'";
  else if(u.role().equals("ADMIN")) q="UPDATE appointments SET status='CANCELLED' WHERE appointment_code=? AND status='BOOKED'";
  else throw new SecurityException("Role is not allowed to cancel appointments.");
  try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(q)){
   p.setString(1,code);if(!u.role().equals("ADMIN"))p.setInt(2,u.id());
   if(p.executeUpdate()==0)throw new SQLException("Appointment could not be cancelled or you do not have access to it.");
  }
 }
 public Path upload(SessionUser u,Path source,String category)throws Exception{String n=source.getFileName().toString().toLowerCase();String ext=n.contains(".")?n.substring(n.lastIndexOf('.')+1):"";if(!Set.of("pdf","jpg","jpeg","png").contains(ext))throw new IllegalArgumentException("Only PDF, JPG, JPEG and PNG files are allowed.");long size=Files.size(source);if(size>15L*1024*1024)throw new IllegalArgumentException("Maximum upload size is 15 MB.");int pid=patientId(u.id());if(pid<0)throw new IllegalArgumentException("Patient profile not found.");Path dir=Path.of("uploads",String.valueOf(pid));Files.createDirectories(dir);Path dest=dir.resolve(UUID.randomUUID()+"."+ext);Files.copy(source,dest);try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO medical_files(patient_id,uploaded_by,category,original_name,stored_path,mime_type,size_bytes) VALUES(?,?,?,?,?,?,?)")){p.setInt(1,pid);p.setInt(2,u.id());p.setString(3,category);p.setString(4,source.getFileName().toString());p.setString(5,dest.toString());p.setString(6,Files.probeContentType(source));p.setLong(7,size);p.executeUpdate();}return dest;}
 public List<String[]> files(SessionUser u)throws SQLException{List<String[]>a=new ArrayList<>();String q="SELECT category,original_name,uploaded_at,stored_path FROM medical_files WHERE patient_id=(SELECT id FROM patients WHERE user_id=?) ORDER BY uploaded_at DESC";try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(q)){p.setInt(1,u.id());try(ResultSet r=p.executeQuery()){while(r.next())a.add(new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4)});}}return a;}
 public void registerDoctor(String username,String password,String fullName,String email,String specialty,String qualification,int experience,double fee)throws Exception{
  if(username==null||username.isBlank()||password==null||password.length()<8||fullName==null||fullName.isBlank()||email==null||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")||qualification==null||qualification.isBlank()||experience<0||fee<0)
   throw new IllegalArgumentException("Enter valid doctor details. Password must contain at least 8 characters.");
  try(Connection c=Database.connect()){
   c.setAutoCommit(false);
   try{
    int specId;
    try(PreparedStatement q=c.prepareStatement("SELECT id FROM specializations WHERE name=?")){
     q.setString(1,specialty);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Select a valid specialization.");specId=r.getInt(1);}
    }
    int uid;
    try(PreparedStatement q=c.prepareStatement("INSERT INTO users(username,password_hash,role,full_name,email) VALUES(?,?, 'DOCTOR',?,?)",Statement.RETURN_GENERATED_KEYS)){
     q.setString(1,username.trim());q.setString(2,org.mindrot.jbcrypt.BCrypt.hashpw(password,org.mindrot.jbcrypt.BCrypt.gensalt(11)));q.setString(3,fullName.trim());q.setString(4,email.trim().toLowerCase());q.executeUpdate();
     try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Could not create doctor login.");uid=r.getInt(1);}
    }
    int did;
    try(PreparedStatement q=c.prepareStatement("INSERT INTO doctors(user_id,specialization_id,qualification,experience,fee,rating) VALUES(?,?,?,?,?,4.5)",Statement.RETURN_GENERATED_KEYS)){
     q.setInt(1,uid);q.setInt(2,specId);q.setString(3,qualification.trim());q.setInt(4,experience);q.setDouble(5,fee);q.executeUpdate();
     try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Could not create doctor profile.");did=r.getInt(1);}
    }
    for(int day=1;day<=7;day++)try(PreparedStatement q=c.prepareStatement("INSERT OR IGNORE INTO doctor_availability(doctor_id,day_of_week,start_time,end_time,slot_minutes) VALUES(?,?, '09:00','17:00',30)")){
     q.setInt(1,did);q.setInt(2,day);q.executeUpdate();
    }
    try(PreparedStatement q=c.prepareStatement("INSERT INTO activity_logs(user_id,action,details) VALUES(?,?,?)")){
     q.setInt(1,uid);q.setString(2,"DOCTOR_REGISTERED");q.setString(3,"Doctor self-registration");q.executeUpdate();
    }
    c.commit();
   }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
  }
 }
 public void updateAppointmentStatus(SessionUser actor,String code,String status)throws SQLException{
  if(actor==null||!Set.of("COMPLETED","IN_PROGRESS").contains(status))throw new SecurityException("Invalid appointment status or session.");
  String sql;
  if("ADMIN".equals(actor.role())) sql="UPDATE appointments SET status=? WHERE appointment_code=? AND status='BOOKED'";
  else if("DOCTOR".equals(actor.role())) sql="UPDATE appointments SET status=? WHERE appointment_code=? AND doctor_id=(SELECT id FROM doctors WHERE user_id=?) AND status='BOOKED'";
  else throw new SecurityException("Only the assigned doctor or an administrator can update appointment status.");
  try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement(sql)){
   p.setString(1,status);p.setString(2,code);if("DOCTOR".equals(actor.role()))p.setInt(3,actor.id());
   if(p.executeUpdate()==0)throw new SQLException("Appointment was not found, is already updated, or does not belong to this doctor.");
  }
 }
 public void createDoctor(SessionUser actor,String username,String password,String fullName,String email,String specialty,String qualification,int experience,double fee)throws Exception{
  requireAdmin(actor);
  if(username==null||username.isBlank()||password==null||password.length()<8||fullName==null||fullName.isBlank()||email==null||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")||qualification==null||qualification.isBlank()||experience<0||fee<0)throw new IllegalArgumentException("Enter valid doctor details. Password must contain at least 8 characters.");
  try(Connection c=Database.connect()){c.setAutoCommit(false);try{
   int specId;try(PreparedStatement q=c.prepareStatement("SELECT id FROM specializations WHERE name=?")){q.setString(1,specialty);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Select a valid specialization.");specId=r.getInt(1);}}
   int uid;try(PreparedStatement q=c.prepareStatement("INSERT INTO users(username,password_hash,role,full_name,email) VALUES(?,?, 'DOCTOR',?,?)",Statement.RETURN_GENERATED_KEYS)){q.setString(1,username.trim());q.setString(2,org.mindrot.jbcrypt.BCrypt.hashpw(password,org.mindrot.jbcrypt.BCrypt.gensalt(11)));q.setString(3,fullName.trim());q.setString(4,email.trim().toLowerCase());q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Could not create doctor login.");uid=r.getInt(1);}}
   int did;try(PreparedStatement q=c.prepareStatement("INSERT INTO doctors(user_id,specialization_id,qualification,experience,fee,rating) VALUES(?,?,?,?,?,4.5)",Statement.RETURN_GENERATED_KEYS)){q.setInt(1,uid);q.setInt(2,specId);q.setString(3,qualification.trim());q.setInt(4,experience);q.setDouble(5,fee);q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){if(!r.next())throw new SQLException("Could not create doctor profile.");did=r.getInt(1);}}
   for(int day=1;day<=7;day++)try(PreparedStatement q=c.prepareStatement("INSERT OR IGNORE INTO doctor_availability(doctor_id,day_of_week,start_time,end_time,slot_minutes) VALUES(?,?, '09:00','17:00',30)")){q.setInt(1,did);q.setInt(2,day);q.executeUpdate();}
   c.commit();
  }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}
 }
 public void createAdmin(SessionUser actor,String username,String password,String fullName,String email)throws Exception{
  requireAdmin(actor);
  if(username==null||username.isBlank()||password==null||password.length()<8||fullName==null||fullName.isBlank()||email==null||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Enter valid administrator details and a password of at least 8 characters.");
  try(Connection c=Database.connect();PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password_hash,role,full_name,email) VALUES(?,?, 'ADMIN',?,?)")){p.setString(1,username.trim());p.setString(2,org.mindrot.jbcrypt.BCrypt.hashpw(password,org.mindrot.jbcrypt.BCrypt.gensalt(11)));p.setString(3,fullName.trim());p.setString(4,email.trim().toLowerCase());p.executeUpdate();}
 }
 private void requireAdmin(SessionUser actor) {
  if (actor == null || !"ADMIN".equals(actor.role())) throw new SecurityException("Only an authenticated administrator can create accounts.");
 }
 public int count(String table)throws SQLException{try(Connection c=Database.connect();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){return r.next()?r.getInt(1):0;}}
}
