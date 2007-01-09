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
package rice.visualization;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import rice.visualization.data.*;
import rice.visualization.render.*;
import rice.pastry.dist.*;

public class VisualizationFrame extends JFrame {
  
  protected Visualization visualization;
    
  protected PastryRingPanel pastryRingPanel;

  protected PastryNodePanel pastryNodePanel;
  
  protected InformationPanel informationPanel;
  
  protected ControlPanel controlPanel;
  
  protected VisualizationFrame(Visualization visualization) {
    super("Pastry Network Visualization"); 
    setResizable(false);
    
    this.visualization = visualization;
    
    GridBagLayout layout = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();
    
    getContentPane().setLayout(layout);
    
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    
    ViewRendererFactory factory = new ViewRendererFactory();
    factory.addRenderer(new TableViewRenderer(visualization));
    factory.addRenderer(new KeyValueListViewRenderer(visualization));
    factory.addRenderer(new LineGraphViewRenderer(visualization));
    factory.addRenderer(new PieChartViewRenderer(visualization));
        
    pastryRingPanel = new PastryRingPanel(visualization);
    c.gridx = 0;
    c.gridy = 0;
    c.gridheight = 3;
    c.gridwidth = 1;
    layout.setConstraints(pastryRingPanel, c);
    getContentPane().add(pastryRingPanel);
    
    c = new GridBagConstraints();
    
    pastryNodePanel = new PastryNodePanel(factory);
    c.gridx = 0;
    c.gridy = 3;
    c.gridheight = 1;
    c.gridwidth = 2;
    layout.setConstraints(pastryNodePanel, c);
    getContentPane().add(pastryNodePanel); 
    
    c = new GridBagConstraints();
    
    informationPanel = new InformationPanel(visualization);
    c.gridx = 1;
    c.gridy = 0;
    c.gridheight = 1;
    c.gridwidth = 1;
    layout.setConstraints(informationPanel, c);
    getContentPane().add(informationPanel); 
    
    c = new GridBagConstraints();
    
    controlPanel = new ControlPanel(visualization);
    c.gridx = 1;
    c.gridy = 1;
    c.gridheight = 2;
    c.gridwidth = 1;
    layout.setConstraints(controlPanel, c);
    getContentPane().add(controlPanel); 
    
    //Display the window.
    pack();
    setVisible(true);
  }
  
  public void nodeHighlighted(Node node) {
    pastryRingPanel.nodeHighlighted(node);
  }
  
  public void nodeSelected(Node node, Data data) {
    controlPanel.nodeSelected(node, data); 
    if (node != null) {
      if (node.ring == visualization.selectedRing)
        informationPanel.nodeSelected(node, data);
    } else {
      informationPanel.nodeSelected(node, data);      
    }
    pastryRingPanel.nodeSelected(node, data);
    pastryNodePanel.nodeSelected(node, data);
  }
}