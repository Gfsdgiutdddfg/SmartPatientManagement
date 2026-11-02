package com.smartpatient.ui;
import javax.swing.*;import java.util.Date;import java.awt.*;
/** Small dependency-free date picker based on Swing's date spinner. */
public class JDateChooser extends JPanel {
 private final JSpinner spinner=new JSpinner(new SpinnerDateModel());
 public JDateChooser(){super(new BorderLayout());spinner.setEditor(new JSpinner.DateEditor(spinner,"yyyy-MM-dd"));spinner.setPreferredSize(new Dimension(150,28));add(spinner,BorderLayout.CENTER);}
 public void setDate(Date date){spinner.setValue(date);}public Date getDate(){return (Date)spinner.getValue();}
 @Override public void addPropertyChangeListener(String property,java.beans.PropertyChangeListener listener){super.addPropertyChangeListener(property,listener);spinner.addChangeListener(e->listener.propertyChange(new java.beans.PropertyChangeEvent(this,"date",null,getDate())));}
}
