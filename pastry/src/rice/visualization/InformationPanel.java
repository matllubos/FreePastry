package rice.visualization;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import java.math.*;
import java.net.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.p2p.commonapi.*;

public class InformationPanel extends JPanel {
  
  public static int INFORMATION_PANEL_WIDTH = 350;
  public static int INFORMATION_PANEL_HEIGHT = PastryRingPanel.PASTRY_RING_PANEL_HEIGHT;
  
  protected Visualization visualization;
  
  protected DefaultComboBoxModel model;
  
  protected DefaultComboBoxModel addrModel;
    
  public InformationPanel(Visualization visualization) {
    this.visualization = visualization;
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Ring Information"),
                                                 BorderFactory.createEmptyBorder(5,5,5,5)));
    
    model = new DefaultComboBoxModel();
    JComboBox combo = new JComboBox(model);
    add(combo);
    combo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent state) { 
        if (state.getStateChange() == state.SELECTED) {
          InformationPanel.this.visualization.setSelected((NodeId) state.getItem());
        }
      }
    });
    
    addrModel = new DefaultComboBoxModel();
    JComboBox addrCombo = new JComboBox(addrModel);
    add(addrCombo);
    addrCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent state) { 
        if (state.getStateChange() == state.SELECTED) {
          InformationPanel.this.visualization.setSelected((InetSocketAddress) state.getItem());
        }
      }
    });
  }
  
  public Dimension getPreferredSize() {
    return new Dimension(INFORMATION_PANEL_WIDTH, INFORMATION_PANEL_HEIGHT);
  }
  
  public void nodeSelected(DistNodeHandle node) {
    if (node != null) {
      DistNodeHandle[] handles = visualization.getNodes();
      
      for (int i=0; i<handles.length; i++) {
        if (model.getIndexOf(handles[i].getNodeId()) < 0) {
          boolean inserted = false;
          for (int j=0; (j<model.getSize()) && !inserted; j++) {
            if (((NodeId) model.getElementAt(j)).compareTo(handles[i].getNodeId()) > 0) {
              model.insertElementAt(handles[i].getNodeId(), j);
              inserted = true;
            }
          }
          if (! inserted)
            model.addElement(handles[i].getNodeId());
        }
      }
      model.setSelectedItem(node.getNodeId());
      
      for (int i=0; i<handles.length; i++) {
        if (addrModel.getIndexOf(handles[i].getAddress()) < 0) {
          boolean inserted = false;
          for (int j=0; (j<addrModel.getSize()) && !inserted; j++) {
            if (((InetSocketAddress) addrModel.getElementAt(j)).toString().compareTo(handles[i].getAddress().toString()) > 0) {
              addrModel.insertElementAt(handles[i].getAddress(), j);
              inserted = true;
            }
          }
          if (! inserted)
            addrModel.addElement(handles[i].getAddress());
        }
      }
      addrModel.setSelectedItem(node.getAddress());
      
      repaint();
    }
  }
}