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
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import java.math.*;

import rice.visualization.data.*;
import rice.visualization.render.*;
import rice.pastry.dist.*;

public class PastryNodePanel extends JPanel {
  
  public static int PASTRY_NODE_PANEL_WIDTH = PastryRingPanel.PASTRY_RING_PANEL_WIDTH + InformationPanel.INFORMATION_PANEL_WIDTH;
  public static int PASTRY_NODE_PANEL_HEIGHT = 300;
  
  public static int PASTRY_NODE_TAB_WIDTH = PASTRY_NODE_PANEL_WIDTH - 40;
  public static int PASTRY_NODE_TAB_HEIGHT = PASTRY_NODE_PANEL_HEIGHT - 40;
  
  protected ViewRendererFactory factory;
  
  protected int selected = 0;
  
  protected JTabbedPane pane = null;
        
  public PastryNodePanel(ViewRendererFactory factory) {    
    this.factory = factory;
  }
  
  public void nodeSelected(Node node, Data data) {
    removeAll();
    
    if (node != null) {
      if (pane != null)
        selected = pane.getSelectedIndex();
  
      processData(data);
    } else {
      pane = null;
      selected = 0;
    }
    
    repaint();  
  }
  
  protected void processData(Data data) {
    if (data != null) {
      pane = processDataPanels(data.getDataPanels());
      add(pane);

      doLayout(); 
    }
  }
  
  protected JTabbedPane processDataPanels(DataPanel[] panels) {
    JTabbedPane pane = new JTabbedPane();

    for (int i=0; i<panels.length; i++) {
      if (panels[i] instanceof MultiDataPanel) {
        JTabbedPane tabs = processDataPanels(((MultiDataPanel) panels[i]).getDataPanels());
        pane.addTab(panels[i].getName(), tabs);
      } else {
        JPanel panel = processDataPanel(panels[i]);
        pane.addTab(panels[i].getName(), panel);
      }
    }
//    pane.setSelectedIndex(selected);
    return pane;
  }
  
  protected JPanel processDataPanel(DataPanel panel) {
    GridBagLayout layout = new GridBagLayout();
    JPanel jpanel = new JPanel(layout);
    jpanel.setPreferredSize(new Dimension(PASTRY_NODE_TAB_WIDTH, PASTRY_NODE_TAB_HEIGHT));
    
    for (int i=0; i<panel.getDataViewCount(); i++) {
      JPanel thisPanel = factory.render(panel.getDataView(i));
      layout.setConstraints(thisPanel, panel.getDataView(i).getConstraints().trans());      
      jpanel.add(thisPanel);
    }
        
    return jpanel; //factory.render(panel.getDataView(0)); //jpanel;
  }
  
  public Dimension getPreferredSize() {
    return new Dimension(PASTRY_NODE_PANEL_WIDTH, PASTRY_NODE_PANEL_HEIGHT);
  }
}