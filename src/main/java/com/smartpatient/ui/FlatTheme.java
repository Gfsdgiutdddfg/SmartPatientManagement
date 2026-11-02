package com.smartpatient.ui;
import com.formdev.flatlaf.FlatDarkLaf;import com.formdev.flatlaf.FlatLightLaf;
public final class FlatTheme {private FlatTheme(){}public static void apply(boolean dark){try{if(dark)FlatDarkLaf.setup();else FlatLightLaf.setup();}catch(Exception ignored){}}}
