package com.smartpatient.ui;
import com.smartpatient.model.SessionUser;import com.smartpatient.service.*;import com.smartpatient.service.HospitalService.Doctor;
import com.google.zxing.BarcodeFormat;import com.google.zxing.client.j2se.MatrixToImageWriter;import com.google.zxing.qrcode.QRCodeWriter;
import javax.swing.*;import javax.swing.border.*;import javax.swing.table.DefaultTableModel;import java.awt.*;import java.sql.SQLException;import java.awt.event.*;import java.nio.file.*;import java.time.*;import java.util.*;import java.util.List;

public class AppFrame extends JFrame {
 private final AuthService auth=new AuthService();private final HospitalService hospital=new HospitalService();private SessionUser user;private JPanel root;private JPanel activeContent;private boolean dark=false;
 private final Color blue=new Color(35,117,190),ink=new Color(34,54,76),muted=new Color(112,130,148),pale=new Color(239,247,253);
 public AppFrame(){setTitle("Smart Patient | Hospital Management");setDefaultCloseOperation(EXIT_ON_CLOSE);setSize(1180,760);setMinimumSize(new Dimension(980,650));setLocationRelativeTo(null);showLogin();}
 private void showLogin(){
  activeContent=null;
  root=new JPanel(new GridBagLayout());
  root.setBackground(new Color(239,247,253));
  setContentPane(root);

  JPanel card=new JPanel(new GridBagLayout());
  card.setBackground(Color.WHITE);
  card.setBorder(new CompoundBorder(new LineBorder(new Color(218,232,244),1,true),new EmptyBorder(34,44,34,44)));
  card.setPreferredSize(new Dimension(520,590));
  GridBagConstraints g=new GridBagConstraints();
  g.gridx=0; g.gridy=0; g.fill=GridBagConstraints.HORIZONTAL; g.weightx=1;
  g.insets=new Insets(6,0,6,0);

  JLabel logo=new JLabel("✚  SMART PATIENT",SwingConstants.CENTER);
  logo.setFont(new Font("SansSerif",Font.BOLD,29)); logo.setForeground(blue); card.add(logo,g);
  g.gridy++;
  JLabel sub=new JLabel("Care, connected.",SwingConstants.CENTER);
  sub.setForeground(muted); sub.setFont(new Font("SansSerif",Font.PLAIN,17));
  g.insets=new Insets(5,0,22,0); card.add(sub,g);

  JComboBox<String> role=new JComboBox<>(new String[]{"PATIENT","DOCTOR","ADMIN"});
  role.setFont(new Font("SansSerif",Font.PLAIN,15)); role.setPreferredSize(new Dimension(400,44));
  JTextField username=new JTextField(); JPasswordField password=new JPasswordField();
  styleField(username,"Username or email"); styleField(password,"Password");

  g.gridy++; g.insets=new Insets(5,0,5,0); card.add(label("LOGIN AS"),g);
  g.gridy++; card.add(role,g);
  g.gridy++; card.add(label("USERNAME OR EMAIL"),g);
  g.gridy++; card.add(username,g);
  g.gridy++; card.add(label("PASSWORD"),g);
  g.gridy++; card.add(password,g);

  g.gridy++; JButton login=primary("Sign in");
  login.setPreferredSize(new Dimension(400,48)); g.insets=new Insets(18,0,7,0); card.add(login,g);
  g.gridy++; JButton accountAction=secondary("Create patient account");
  accountAction.setPreferredSize(new Dimension(400,44)); g.insets=new Insets(5,0,5,0); card.add(accountAction,g);
  g.gridy++; JLabel helper=new JLabel("Secure access for patients, doctors and hospital administrators.",SwingConstants.CENTER);
  helper.setForeground(muted); helper.setFont(new Font("SansSerif",Font.PLAIN,12));
  g.insets=new Insets(14,0,0,0); card.add(helper,g);
  root.add(card);
  revalidate(); repaint(); setVisible(true);

  Runnable updateAccountAction=()->{
   String selected=(String)role.getSelectedItem();
   if("PATIENT".equals(selected)){accountAction.setText("Create patient account");accountAction.setEnabled(true);}
   else if("DOCTOR".equals(selected)){accountAction.setText("Create doctor account");accountAction.setEnabled(true);}
   else {accountAction.setText("Administrator access is managed by the hospital");accountAction.setEnabled(false);}
  };
  role.addActionListener(e->updateAccountAction.run());
  updateAccountAction.run();

  Runnable doLogin=()->{
   if(!login.isEnabled()) return;
   if(username.getText().isBlank()||password.getPassword().length==0){msg("Enter both username and password.");return;}
   login.setEnabled(false); login.setText("Signing in…");
   String identity=username.getText().trim(), secret=new String(password.getPassword()), selected=(String)role.getSelectedItem();
   new SwingWorker<SessionUser,Void>(){
    protected SessionUser doInBackground() throws Exception{return auth.login(identity,secret,selected);}
    protected void done(){
     login.setEnabled(true); login.setText("Sign in");
     try{
      SessionUser authenticated=get();
      if(authenticated==null){msg("Incorrect username or password. Please check your details.");password.setText("");password.requestFocusInWindow();}
      else{user=authenticated;showDashboard();}
     }catch(Exception ex){
      Throwable cause=ex;
      while(cause.getCause()!=null && (cause instanceof java.util.concurrent.ExecutionException || cause instanceof java.util.concurrent.CompletionException)) cause=cause.getCause();
      if(cause instanceof SecurityException)msg(cause.getMessage());
      else if(cause instanceof SQLException)error(new Exception("Unable to access the hospital database: "+cause.getMessage(),cause));
      else error(new Exception(cause.getMessage()==null?"Sign-in failed. Please try again.":cause.getMessage(),cause));
     }
    }
   }.execute();
  };
  login.addActionListener(e->doLogin.run());
  password.addActionListener(e->doLogin.run());
  username.addActionListener(e->password.requestFocusInWindow());
  accountAction.addActionListener(e->{
   String selected=(String)role.getSelectedItem();
   if("PATIENT".equals(selected))registerDialog();
   else if("DOCTOR".equals(selected))doctorRegisterDialog();
  });
 }
 private void registerDialog(){JTextField n=new JTextField(),un=new JTextField(),em=new JTextField();JPasswordField pw=new JPasswordField();Object[] f={"Full name",n,"Username",un,"Email",em,"Password (min 8 characters)",pw};if(JOptionPane.showConfirmDialog(this,f,"Patient registration",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)try{auth.register(un.getText(),new String(pw.getPassword()),n.getText(),em.getText());msg("Account created. Sign in with your username and password.");}catch(Exception ex){error(ex);}}
 private void doctorRegisterDialog(){
  JTextField name=new JTextField(22),un=new JTextField(22),em=new JTextField(22);
  JTextField qualification=new JTextField("MBBS, MD",22),experience=new JTextField("2",22),fee=new JTextField("500",22);
  JPasswordField pw=new JPasswordField(22);
  JComboBox<String> specialty;
  try{specialty=new JComboBox<>(hospital.specialties().stream().filter(x->!x.equals("All Specialties")).toArray(String[]::new));}
  catch(Exception ex){error(ex);return;}
  Object[] fields={"Full name",name,"Username",un,"Email",em,"Password (min 8 characters)",pw,
   "Specialization",specialty,"Qualification",qualification,"Experience (years)",experience,"Consultation fee (INR)",fee};
  if(JOptionPane.showConfirmDialog(this,fields,"Doctor registration",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
   try{
    hospital.registerDoctor(un.getText(),new String(pw.getPassword()),name.getText(),em.getText(),
      (String)specialty.getSelectedItem(),qualification.getText(),
      Integer.parseInt(experience.getText().trim()),Double.parseDouble(fee.getText().trim()));
    msg("Doctor account created successfully. You can now sign in using the DOCTOR role.");
   }catch(Exception ex){error(ex);}
  }
 }
 private void showDashboard(){root=new JPanel(new BorderLayout());root.setBackground(pale);setContentPane(root);JPanel side=new JPanel();side.setBackground(new Color(20,54,83));side.setPreferredSize(new Dimension(225,0));side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));JLabel brand=new JLabel("  ✚ SMART PATIENT");brand.setForeground(Color.WHITE);brand.setFont(new Font("SansSerif",Font.BOLD,18));brand.setBorder(new EmptyBorder(28,8,35,4));side.add(brand);String[] items=user.role().equals("PATIENT")?new String[]{"Overview","Find Doctors & Book","My Appointments","Medical Files / X-rays"}:user.role().equals("DOCTOR")?new String[]{"Overview","My Appointments"}:new String[]{"Overview","All Doctors","Add Doctor Account","Add Admin Account","All Appointments","Activity & Database"};for(String item:items){JButton b=new JButton("  ◦  "+item);b.setHorizontalAlignment(SwingConstants.LEFT);b.setMaximumSize(new Dimension(220,46));b.setForeground(new Color(224,239,250));b.setBackground(new Color(20,54,83));b.setBorder(new EmptyBorder(12,18,12,8));b.setFocusPainted(false);b.addActionListener(e->navigate(item));side.add(b);}side.add(Box.createVerticalGlue());JButton theme=new JButton(dark?"☀ Light mode":"◐ Dark mode");theme.addActionListener(e->{dark=!dark;FlatTheme.apply(dark);showDashboard();});side.add(theme);JButton logout=new JButton("⇥  Sign out");logout.setForeground(Color.WHITE);logout.setBackground(new Color(20,54,83));logout.setBorder(new EmptyBorder(16,18,22,8));logout.setFocusPainted(false);logout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));logout.setHorizontalAlignment(SwingConstants.LEFT);logout.setAlignmentX(Component.LEFT_ALIGNMENT);logout.setMaximumSize(new Dimension(Integer.MAX_VALUE,52));logout.setToolTipText("Sign out and return to the login screen");logout.addActionListener(e->performLogout());side.add(logout);root.add(side,BorderLayout.WEST);
 JPanel top=new JPanel(new BorderLayout());top.setBackground(Color.WHITE);top.setBorder(new EmptyBorder(18,28,18,28));JLabel title=new JLabel("Good day, "+user.fullName());title.setFont(new Font("SansSerif",Font.BOLD,22));title.setForeground(ink);top.add(title,BorderLayout.WEST);JLabel role=new JLabel(user.role()+"  •  Secure workspace");role.setForeground(muted);top.add(role,BorderLayout.EAST);root.add(top,BorderLayout.NORTH);navigate("Overview");revalidate();repaint();setVisible(true);
  getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_L,InputEvent.CTRL_DOWN_MASK),"logout");
  getRootPane().getActionMap().put("logout",new AbstractAction(){public void actionPerformed(ActionEvent e){performLogout();}});
 }
 private void performLogout(){
  if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(this::performLogout);return;}
  user=null;activeContent=null;
  getContentPane().removeAll();
  showLogin();
  revalidate();repaint();setVisible(true);
 }
 private void navigate(String item){JPanel content=new JPanel(new BorderLayout(14,14));content.setOpaque(false);content.setBorder(new EmptyBorder(24,28,24,28));JLabel h=new JLabel(item);h.setFont(new Font("SansSerif",Font.BOLD,24));h.setForeground(ink);content.add(h,BorderLayout.NORTH);JPanel body=new JPanel(new BorderLayout(12,12));body.setOpaque(false);JScrollPane contentScroll=new JScrollPane(body);contentScroll.setBorder(null);contentScroll.getViewport().setOpaque(false);contentScroll.setOpaque(false);content.add(contentScroll,BorderLayout.CENTER);
 try{if(item.equals("Overview"))overview(body);else if(item.contains("Find Doctors"))booking(body);else if(item.equals("My Appointments")||item.equals("All Appointments"))appointments(body);else if(item.contains("Medical Files")||item.equals("Patient Files"))files(body);else if(item.equals("All Doctors"))doctorList(body);else if(item.equals("Add Doctor Account"))addDoctorPanel(body);else if(item.equals("Add Admin Account"))addAdminPanel(body);else adminActivity(body);}catch(Exception e){error(e);}if(activeContent!=null)root.remove(activeContent);activeContent=content;root.add(activeContent,BorderLayout.CENTER);root.revalidate();root.repaint();}
 private void overview(JPanel p)throws Exception{
  if("DOCTOR".equals(user.role())){
   List<String[]> rows=hospital.appointments(user);
   LocalDate today=LocalDate.now();
   long upcoming=rows.stream().filter(a->"BOOKED".equalsIgnoreCase(a[5])&&!LocalDate.parse(a[1]).isBefore(today)).count();
   long completed=rows.stream().filter(a->"COMPLETED".equalsIgnoreCase(a[5])).count();
   JPanel cards=new JPanel(new GridLayout(1,3,14,14));cards.setOpaque(false);
   cards.add(stat("MY APPOINTMENTS",String.valueOf(rows.size()),"Assigned to your account"));
   cards.add(stat("UPCOMING",String.valueOf(upcoming),"Today and future bookings"));
   cards.add(stat("COMPLETED",String.valueOf(completed),"Consultations completed"));
   p.add(cards,BorderLayout.NORTH);
   JPanel middle=new JPanel(new BorderLayout(10,10));middle.setOpaque(false);
   JPanel welcome=panel();welcome.setLayout(new BoxLayout(welcome,BoxLayout.Y_AXIS));
   welcome.add(text("Doctor workspace",21,ink,true));welcome.add(Box.createVerticalStrut(8));
   welcome.add(text("Review patients who booked appointments with you, check their appointment time and update the visit status after consultation.",14,muted,false));
   JButton view=primary("Open my appointment list");view.addActionListener(e->navigate("My Appointments"));
   JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.setOpaque(false);actions.add(view);welcome.add(Box.createVerticalStrut(14));welcome.add(actions);
   middle.add(welcome,BorderLayout.NORTH);
   DefaultTableModel preview=new DefaultTableModel(new String[]{"Appointment ID","Date","Time","Patient","Specialty","Status"},0){public boolean isCellEditable(int r,int c){return false;}};
   rows.stream().filter(a->"BOOKED".equalsIgnoreCase(a[5])).limit(8).forEach(a->preview.addRow(a));
   JTable t=table(preview);JPanel recent=panel();recent.setLayout(new BorderLayout(8,8));recent.add(text("Recent / upcoming patient bookings",16,ink,true),BorderLayout.NORTH);recent.add(new JScrollPane(t),BorderLayout.CENTER);
   middle.add(recent,BorderLayout.CENTER);p.add(middle,BorderLayout.CENTER);
   return;
  }
  JPanel cards=new JPanel(new GridLayout(1,3,14,14));cards.setOpaque(false);
  int docs=hospital.count("doctors"),appts=hospital.count("appointments"),files=hospital.count("medical_files");
  if("PATIENT".equals(user.role())){
   List<String[]> mine=hospital.appointments(user);
   long upcoming=mine.stream().filter(a->"BOOKED".equalsIgnoreCase(a[5])&&!LocalDate.parse(a[1]).isBefore(LocalDate.now())).count();
   cards.add(stat("AVAILABLE DOCTORS",String.valueOf(docs),"Across 10 specialties"));
   cards.add(stat("MY APPOINTMENTS",String.valueOf(mine.size()),"Your booking history"));
   cards.add(stat("UPCOMING VISITS",String.valueOf(upcoming),"Today and future bookings"));
  }else{
   cards.add(stat("REGISTERED DOCTORS",String.valueOf(docs),"Across 10 specialties"));
   cards.add(stat("TOTAL APPOINTMENTS",String.valueOf(appts),"Bookings in the system"));
   cards.add(stat("MEDICAL FILES",String.valueOf(files),"Reports and X-rays uploaded"));
  }
  p.add(cards,BorderLayout.NORTH);
  JPanel welcome=panel();welcome.setLayout(new BoxLayout(welcome,BoxLayout.Y_AXIS));
  welcome.add(text("Your healthcare workspace",22,ink,true));welcome.add(Box.createVerticalStrut(12));
  welcome.add(text("Browse doctor profiles, schedule appointments, and keep medical documents in one place.",14,muted,false));
  welcome.add(Box.createVerticalStrut(18));welcome.add(text("Quick actions",15,ink,true));
  JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,10,10));actions.setOpaque(false);
  if("PATIENT".equals(user.role())){
   JButton b=primary("Book an appointment");b.addActionListener(e->navigate("Find Doctors & Book"));actions.add(b);
   JButton f=secondary("Upload report / X-ray");f.addActionListener(e->navigate("Medical Files / X-rays"));actions.add(f);
  }
  welcome.add(actions);p.add(welcome,BorderLayout.CENTER);
 }
 private void booking(JPanel p)throws Exception{JPanel filter=panel();filter.setLayout(new FlowLayout(FlowLayout.LEFT,12,12));JComboBox<String> spec=new JComboBox<>(hospital.specialties().toArray(String[]::new));JTextField search=new JTextField(18);JButton go=primary("Search doctors");filter.add(new JLabel("Specialization"));filter.add(spec);filter.add(search);filter.add(go);p.add(filter,BorderLayout.NORTH);DefaultTableModel model=new DefaultTableModel(new String[]{"Doctor","Specialty","Qualification","Experience","Fee (INR)","Rating"},0){public boolean isCellEditable(int r,int c){return false;}};JTable table=table(model);JScrollPane scroll=new JScrollPane(table);p.add(scroll,BorderLayout.CENTER);Runnable load=()->{try{model.setRowCount(0);for(Doctor d:hospital.doctors((String)spec.getSelectedItem()))if(d.name().toLowerCase().contains(search.getText().toLowerCase()))model.addRow(new Object[]{d.name(),d.specialty(),d.qualification(),d.experience()+" yrs", "₹"+(int)d.fee(),String.format("%.1f ★",d.rating())});}catch(Exception ex){error(ex);}};go.addActionListener(e->load.run());load.run();JButton book=primary("Book selected doctor");book.addActionListener(e->{int row=table.getSelectedRow();if(row<0){msg("Select a doctor first.");return;}try{int modelRow=table.convertRowIndexToModel(row);String doctorName=(String)model.getValueAt(modelRow,0);Doctor d=hospital.doctors((String)spec.getSelectedItem()).stream().filter(x->x.name().equals(doctorName)).findFirst().orElseThrow();bookDialog(d);}catch(Exception ex){error(ex);}});JPanel south=new JPanel(new FlowLayout(FlowLayout.RIGHT));south.setOpaque(false);south.add(book);p.add(south,BorderLayout.SOUTH);}
 private void bookDialog(Doctor d)throws Exception{JDateChooser date=new JDateChooser();JComboBox<String> slots=new JComboBox<>();JTextField concern=new JTextField(22);Runnable refresh=()->{try{slots.removeAllItems();for(String s:hospital.slots(d.id(),date.getDate()==null?LocalDate.now().plusDays(1):date.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()))slots.addItem(s);}catch(Exception ex){error(ex);}};date.setDate(java.util.Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()));date.addPropertyChangeListener("date",e->refresh.run());refresh.run();Object[] fields={"Doctor: "+d.name()+" | Fee ₹"+(int)d.fee(),"Appointment date",date,"Available slot",slots,"Health concern",concern};if(JOptionPane.showConfirmDialog(this,fields,"Book appointment",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){if(slots.getSelectedItem()==null){msg("No available slots for this date. Choose another date.");return;}LocalDate day=date.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();String code=hospital.book(user,d.id(),day,(String)slots.getSelectedItem(),concern.getText());createQr(code);msg("Appointment confirmed!\nAppointment ID: "+code+"\nQR ticket saved in exports/"+code+".png");}}
 private void appointments(JPanel p)throws Exception{String[] cols={"Appointment ID","Date","Time","Patient / Doctor","Specialty","Status"};DefaultTableModel m=new DefaultTableModel(cols,0){public boolean isCellEditable(int r,int c){return false;}};for(String[] a:hospital.appointments(user))m.addRow(a);JTable t=table(m);p.add(new JScrollPane(t),BorderLayout.CENTER);JPanel south=new JPanel(new FlowLayout(FlowLayout.RIGHT,10,8));south.setOpaque(false);
  JButton refresh=secondary("Refresh");refresh.addActionListener(e->navigate(user.role().equals("ADMIN")?"All Appointments":"My Appointments"));south.add(refresh);
  JButton cancel=secondary("Cancel selected booking");cancel.addActionListener(e->{int r=t.getSelectedRow();if(r<0){msg("Select an appointment first.");return;}try{int modelRow=t.convertRowIndexToModel(r);hospital.cancel(user,(String)m.getValueAt(modelRow,0));msg("Appointment cancelled.");navigate(user.role().equals("ADMIN")?"All Appointments":"My Appointments");}catch(Exception ex){error(ex);}});south.add(cancel);
  if(user.role().equals("DOCTOR")){
   JButton complete=primary("Mark completed");complete.addActionListener(e->{int r=t.getSelectedRow();if(r<0){msg("Select an appointment first.");return;}try{int modelRow=t.convertRowIndexToModel(r);hospital.updateAppointmentStatus(user,(String)m.getValueAt(modelRow,0),"COMPLETED");msg("Appointment marked as completed.");navigate("My Appointments");}catch(Exception ex){error(ex);}});south.add(complete);
  }
  p.add(south,BorderLayout.SOUTH);}
 private void files(JPanel p)throws Exception{JPanel controls=panel();controls.setLayout(new FlowLayout(FlowLayout.LEFT,12,12));JComboBox<String> cat=new JComboBox<>(new String[]{"General Report","X-Ray","MRI / CT Scan","Blood Test","Prescription","Other"});JButton upload=primary("＋ Upload medical file / X-ray");controls.add(cat);controls.add(upload);p.add(controls,BorderLayout.NORTH);DefaultTableModel m=new DefaultTableModel(new String[]{"Category","File name","Uploaded at","Stored file"},0){public boolean isCellEditable(int r,int c){return false;}};JTable t=table(m);for(String[] a:hospital.files(user))m.addRow(a);p.add(new JScrollPane(t),BorderLayout.CENTER);upload.addActionListener(e->{JFileChooser fc=new JFileChooser();fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Medical files (PDF, JPG, PNG)","pdf","jpg","jpeg","png"));if(fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)try{hospital.upload(user,fc.getSelectedFile().toPath(),(String)cat.getSelectedItem());msg("File uploaded successfully.");navigate("Medical Files / X-rays");}catch(Exception ex){error(ex);}});JButton open=secondary("Open selected file");open.addActionListener(e->{int r=t.getSelectedRow();if(r>=0)try{int modelRow=t.convertRowIndexToModel(r);Desktop.getDesktop().open(Path.of((String)m.getValueAt(modelRow,3)).toFile());}catch(Exception ex){error(ex);}});JPanel south=new JPanel(new FlowLayout(FlowLayout.RIGHT));south.setOpaque(false);south.add(open);p.add(south,BorderLayout.SOUTH);}
 private void doctorList(JPanel p)throws Exception{
  JPanel bar=panel();bar.setLayout(new FlowLayout(FlowLayout.LEFT,12,8));JTextField search=new JTextField(22);JButton refresh=secondary("Refresh list");JButton add=primary("＋ Add doctor account");bar.add(new JLabel("Search doctor"));bar.add(search);bar.add(refresh);bar.add(add);p.add(bar,BorderLayout.NORTH);
  DefaultTableModel m=new DefaultTableModel(new String[]{"Doctor","Specialty","Qualification","Experience","Fee (INR)","Rating"},0){public boolean isCellEditable(int r,int c){return false;}};JTable t=table(m);p.add(new JScrollPane(t),BorderLayout.CENTER);
  Runnable load=()->{try{m.setRowCount(0);for(Doctor d:hospital.doctors("All Specialties"))if(d.name().toLowerCase().contains(search.getText().toLowerCase())||d.specialty().toLowerCase().contains(search.getText().toLowerCase()))m.addRow(new Object[]{d.name(),d.specialty(),d.qualification(),d.experience()+" yrs","₹"+(int)d.fee(),String.format("%.1f ★",d.rating())});}catch(Exception ex){error(ex);}};
  refresh.addActionListener(e->load.run());search.addActionListener(e->load.run());add.addActionListener(e->addDoctorDialog(load));load.run();
 }
 private void addDoctorPanel(JPanel p){JPanel card=panel();card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));card.add(text("Create a doctor login and professional profile",18,ink,true));card.add(Box.createVerticalStrut(8));card.add(text("New doctors can sign in immediately using the username and password you assign.",13,muted,false));card.add(Box.createVerticalStrut(18));JButton create=primary("＋ Create doctor account");create.addActionListener(e->addDoctorDialog(()->navigate("All Doctors")));card.add(create);p.add(card,BorderLayout.NORTH);}
 private void addDoctorDialog(Runnable after){
  JTextField name=new JTextField(22),username=new JTextField(22),email=new JTextField(22),qualification=new JTextField("MBBS, MD",22),experience=new JTextField("3",22),fee=new JTextField("500",22);JPasswordField password=new JPasswordField(22);JComboBox<String> specialty;
  try{specialty=new JComboBox<>(hospital.specialties().stream().filter(x->!x.equals("All Specialties")).toArray(String[]::new));}catch(Exception ex){error(ex);return;}
  Object[] fields={"Full name",name,"Username",username,"Temporary password (min 8 characters)",password,"Email",email,"Specialization",specialty,"Qualification",qualification,"Experience (years)",experience,"Consultation fee (INR)",fee};
  if(JOptionPane.showConfirmDialog(this,fields,"Create doctor account",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)try{hospital.createDoctor(user,username.getText(),new String(password.getPassword()),name.getText(),email.getText(),(String)specialty.getSelectedItem(),qualification.getText(),Integer.parseInt(experience.getText().trim()),Double.parseDouble(fee.getText().trim()));msg("Doctor account created successfully.\nUsername: "+username.getText()+"\nRole: DOCTOR\nThe doctor can now sign in with the assigned password.");after.run();}catch(Exception ex){error(ex);}
 }
 private void addAdminPanel(JPanel p){JPanel card=panel();card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));card.add(text("Create another administrator login",18,ink,true));card.add(Box.createVerticalStrut(8));card.add(text("Administrator accounts can manage doctors and view all appointments. Only share these credentials with trusted staff.",13,muted,false));card.add(Box.createVerticalStrut(18));JButton create=primary("＋ Create administrator account");create.addActionListener(e->addAdminDialog());card.add(create);p.add(card,BorderLayout.NORTH);}
 private void addAdminDialog(){JTextField name=new JTextField(22),username=new JTextField(22),email=new JTextField(22);JPasswordField password=new JPasswordField(22);Object[] fields={"Full name",name,"Username",username,"Password (min 8 characters)",password,"Email",email};if(JOptionPane.showConfirmDialog(this,fields,"Create administrator account",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)try{hospital.createAdmin(user,username.getText(),new String(password.getPassword()),name.getText(),email.getText());msg("Administrator account created.\nUsername: "+username.getText());}catch(Exception ex){error(ex);}}
 private void adminActivity(JPanel p)throws Exception{JPanel stats=new JPanel(new GridLayout(2,2,12,12));stats.setOpaque(false);for(String t:new String[]{"users","doctors","patients","appointments"})stats.add(stat(t.toUpperCase(),String.valueOf(hospital.count(t)),"Live SQLite count"));p.add(stats,BorderLayout.NORTH);JLabel note=text("Database: data/smart_patient.db  •  Backups: backups/\nDemo seed creates 100 doctors once; existing data is preserved on restart.",14,muted,false);p.add(note,BorderLayout.CENTER);JButton backup=secondary("Create database backup");backup.addActionListener(e->{try{Path dest=Path.of("backups","smart_patient_"+System.currentTimeMillis()+".db");Files.copy(Path.of("data/smart_patient.db"),dest);msg("Backup created: "+dest);}catch(Exception ex){error(ex);}});JPanel south=new JPanel(new FlowLayout(FlowLayout.LEFT));south.setOpaque(false);south.add(backup);p.add(south,BorderLayout.SOUTH);}
 private void createQr(String code)throws Exception{var matrix=new QRCodeWriter().encode("Smart Patient Appointment: "+code,BarcodeFormat.QR_CODE,320,320);MatrixToImageWriter.writeToPath(matrix,"PNG",Path.of("exports",code+".png"));}
 private JPanel stat(String title,String value,String hint){JPanel c=panel();c.setLayout(new BoxLayout(c,BoxLayout.Y_AXIS));c.add(text(title,11,muted,true));c.add(Box.createVerticalStrut(10));c.add(text(value,30,blue,true));c.add(Box.createVerticalStrut(5));c.add(text(hint,12,muted,false));return c;}
 private JPanel panel(){JPanel p=new JPanel();p.setBackground(Color.WHITE);p.setBorder(new CompoundBorder(new LineBorder(new Color(220,232,242)),new EmptyBorder(18,20,18,20)));return p;}
 private JLabel text(String s,int size,Color c,boolean bold){JLabel l=new JLabel(s);l.setFont(new Font("SansSerif",bold?Font.BOLD:Font.PLAIN,size));l.setForeground(c);return l;}private JLabel label(String s){JLabel l=text(s,11,muted,true);return l;}
 private JButton primary(String s){JButton b=new JButton(s);b.setBackground(blue);b.setForeground(Color.WHITE);b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.setBorder(new EmptyBorder(11,18,11,18));return b;}private JButton secondary(String s){JButton b=new JButton(s);b.setBackground(new Color(231,242,251));b.setForeground(blue);b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.setBorder(new EmptyBorder(10,16,10,16));return b;}
 private void styleField(JTextField f,String hint){f.setPreferredSize(new Dimension(330,42));f.setFont(new Font("SansSerif",Font.PLAIN,14));f.setToolTipText(hint);}private JTable table(DefaultTableModel m){JTable t=new JTable(m);t.setRowHeight(34);t.setFont(new Font("SansSerif",Font.PLAIN,13));t.getTableHeader().setFont(new Font("SansSerif",Font.BOLD,12));t.setSelectionBackground(new Color(216,237,252));t.setSelectionForeground(ink);t.setFillsViewportHeight(true);t.setAutoCreateRowSorter(true);t.setIntercellSpacing(new Dimension(0,1));t.setShowVerticalLines(false);t.getTableHeader().setReorderingAllowed(false);return t;}
 private void msg(String s){JOptionPane.showMessageDialog(this,s,"Smart Patient",JOptionPane.INFORMATION_MESSAGE);}private void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage()==null?e.toString():e.getMessage(),"Operation failed",JOptionPane.ERROR_MESSAGE);} 
}
