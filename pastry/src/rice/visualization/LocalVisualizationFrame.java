/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.visualization;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import rice.visualization.data.*;
import rice.visualization.render.*;
import rice.pastry.dist.*;

public class LocalVisualizationFrame extends JFrame {
  
  protected PastryNodePanel pastryNodePanel;
  
  protected LocalVisualization visualization;
  
  protected LocalVisualizationFrame(LocalVisualization visualization) {
    super("Local Node Visualization"); 
    setResizable(false);
    
    this.visualization = visualization;
    
    GridBagLayout layout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    
    getContentPane().setLayout(layout);
    
    ViewRendererFactory factory = ViewRendererFactory.buildFactory(visualization);

    pastryNodePanel = new PastryNodePanel(factory);
    layout.setConstraints(pastryNodePanel, c);
    getContentPane().add(pastryNodePanel); 
    
    //Display the window.
    pack();
    setVisible(true);
    
    addWindowListener(new WindowListener() {
  public void windowActivated(WindowEvent e) {}      
  public void windowClosed(WindowEvent e) {
  LocalVisualizationFrame.this.visualization.exit();
  }      
      public void windowClosing(WindowEvent e) {
        LocalVisualizationFrame.this.visualization.exit();
      }      
  public void windowDeactivated(WindowEvent e) {}      
  public void windowDeiconified(WindowEvent e) {}      
  public void windowIconified(WindowEvent e) {}      
  public void windowOpened(WindowEvent e) {}
    });
      
  }
  
  public void nodeSelected(Node node, Data data) {
    pastryNodePanel.nodeSelected(node, data); 
  }
}