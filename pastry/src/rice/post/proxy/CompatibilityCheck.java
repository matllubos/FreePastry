/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.post.proxy;

import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class CompatibilityCheck {
  
  protected JFrame frame;
  
  protected PostPanel panel;
  
  protected KillPanel kill;
  
  protected JTextArea area;
  
  protected JScrollPane scroll;
  
  public CompatibilityCheck() {
    frame = new JFrame();
    panel = new PostPanel();
    kill = new KillPanel();
    area = new JTextArea(10,75);
    area.setFont(new Font("Courier", Font.PLAIN, 10));
    scroll = new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    
    GridBagLayout layout = new GridBagLayout();
    frame.getContentPane().setLayout(layout);
    
    GridBagConstraints c = new GridBagConstraints();
    layout.setConstraints(panel, c);      
    frame.getContentPane().add(panel);
    
    GridBagConstraints d = new GridBagConstraints();
    d.gridy=1;
    layout.setConstraints(scroll, d);      
    frame.getContentPane().add(scroll);
    
    GridBagConstraints e = new GridBagConstraints();
    e.gridy=2;
    layout.setConstraints(kill, e);      
    frame.getContentPane().add(kill);
    
    frame.pack();
    frame.setVisible(true);
  }
  
  public void test() {
    append("Testing Operating System Compatibility.......................");
    
    if (! testOS(System.getProperty("os.name"))) {
      append("[FAILED]\n\nYou appear to be running an incompatible operating system '" + System.getProperty("os.name") + "'.\n");
      append("Currently, only Windows and Linux are supported for ePOST.");
    } else {
      append("[SUCCESS]\n");
      append("Testing Java Version.........................................");
      
      if (! testJavaVersion(System.getProperty("java.version"))) {
        append("[FAILED]\n\nYou appear to be running an incompatible version of Java '" + System.getProperty("java.version") + "'.\n");
        append("Currently, only Java 1.4 is supported, and you must be running a\n");
        append("version of at least 1.4.2.  Please see http://java.sun.com in order\n");
        append("to download a compatible version.");
      } else {
        append("[SUCCESS]\n");
        append("Testing IP Address...........................................");
        
        try {
          if (! testIPAddress(InetAddress.getLocalHost().getHostAddress())) {
            append("[FAILED]\n\nYou appear to be running from a computer which is not Internet-visible\n");
            append("with IP address '" + InetAddress.getLocalHost().getHostAddress() + "'. Currently, ePOST requires that all\n");
            append("machines have a valid, routable IP address which is not behind a\n");
            append("firewall or a NAT.");
          } else {
            append("[SUCCESS]\n\n");
            append("Your computer has successfully passed the compatibility check - you\n");
            append("should be able to run ePOST.  Please download the latest code from\n");
            append("http://www.epostmail.org/");
          }
        } catch (UnknownHostException uhe) {
          append("[FAILED]\n\nThe compatibility checker was unable to determine the local host address -\n");
          append("checking caused an UnknownHostException.  This usually indicates that you are not connected\n");
          append("to the Internet.");
        }
      }
    }
  }
          
  
  public void append(String s) {
    Dimension dim = area.getPreferredSize();
    scroll.getViewport().setViewPosition(new Point(0,(int) (dim.getHeight()+20)));
    area.append(s);
  }

  public static boolean testOS(String os) {
    if ((os.toLowerCase().indexOf("linux") >= 0) || (os.toLowerCase().indexOf("windows") >= 0) || (os.toLowerCase().indexOf("mac os x") >= 0))
      return true;
    
    return false;
  }
  
  public static boolean testJavaVersion(String version) {
    if (version.startsWith("1.5") || version.startsWith("1.6"))
      return true;
    
    // a little bit of future-proofing
    if (version.startsWith("1.7") || version.startsWith("2"))
      return true;
    
    if ((! version.startsWith("1.4")) || version.startsWith("1.4.0") || version.startsWith("1.4.1"))
      return false;
    
    return true;
  }
  
  public static boolean testIPAddress(String ip) {
    if (ip.startsWith("127.0.0.1") || ip.startsWith("10.") || ip.startsWith("192.168."))
      return false;
    
    return true;
  }
  
  public static void main(String[] args) {
    CompatibilityCheck c = new CompatibilityCheck();
    c.test();
  }
  
  protected class PostPanel extends JPanel {
    public Dimension getPreferredSize() {
      return new Dimension(350,60); 
    }
    
    public void paint(Graphics g) {
      g.setFont(new Font("Times", Font.BOLD, 24));
      g.drawString("ePOST Compatibility Check", 23, 40);      
    }
  }
  
  protected class KillPanel extends JPanel {
    public KillPanel() {
      JButton kill = new JButton("Exit");
      
      kill.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          System.exit(-2);
        }
      });
            
      GridBagLayout layout = new GridBagLayout();
      setLayout(layout);
      
      GridBagConstraints d = new GridBagConstraints();
      d.gridx=1;
      layout.setConstraints(kill, d);      
      add(kill);
    }
    
    public Dimension getPreferredSize() {
      return new Dimension(300, 30);
    }
  }
}