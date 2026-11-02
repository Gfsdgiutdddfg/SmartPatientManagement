package com.smartpatient;
import com.formdev.flatlaf.FlatLightLaf;import com.smartpatient.config.Database;import com.smartpatient.ui.AppFrame;import javax.swing.*;
public class Main { public static void main(String[] args){SwingUtilities.invokeLater(()->{try{Database.initialize();FlatLightLaf.setup();UIManager.put("Component.arc",14);UIManager.put("Button.arc",14);UIManager.put("TextComponent.arc",10);new AppFrame().setVisible(true);}catch(Exception e){e.printStackTrace();JOptionPane.showMessageDialog(null,"Startup failed: "+e.getMessage(),"Smart Patient",JOptionPane.ERROR_MESSAGE);}});}}
