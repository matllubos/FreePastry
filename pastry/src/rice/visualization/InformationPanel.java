package rice.visualization;

import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.InetSocketAddress;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import rice.pastry.NodeId;
import rice.pastry.dist.DistNodeHandle;
import rice.visualization.data.Data;

public class InformationPanel extends JPanel {
  
  public static int INFORMATION_PANEL_WIDTH = 350;
  public static int INFORMATION_PANEL_HEIGHT = PastryRingPanel.PASTRY_RING_PANEL_HEIGHT/3;
  
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
          InformationPanel.this.visualization.setSelected((NodeId) state.getItem(),InformationPanel.this.visualization.getSelectedRing());
        }
      }
    });
    
    addrModel = new DefaultComboBoxModel();
    JComboBox addrCombo = new JComboBox(addrModel);
    add(addrCombo);
    addrCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent state) { 
        if (state.getStateChange() == state.SELECTED) {
          InformationPanel.this.visualization.setSelected((InetSocketAddress) state.getItem(),InformationPanel.this.visualization.getSelectedRing());
        }
      }
    });
  }
  
  public Dimension getPreferredSize() {
    return new Dimension(INFORMATION_PANEL_WIDTH, INFORMATION_PANEL_HEIGHT);
  }
  
  public void nodeSelected(Node node, Data data) {
    if (node != null) {
      Node[] handles = visualization.getNodes();
      
      for (int i=0; i<handles.length; i++) {
        if (model.getIndexOf(handles[i].handle.getNodeId()) < 0) {
          boolean inserted = false;
          for (int j=0; (j<model.getSize()) && !inserted; j++) {
            if (((NodeId) model.getElementAt(j)).compareTo(handles[i].handle.getNodeId()) > 0) {
              model.insertElementAt(handles[i].handle.getNodeId(), j);
              inserted = true;
            }
          }
          if (! inserted)
            model.addElement(handles[i].handle.getNodeId());
        }
      }
      model.setSelectedItem(node.handle.getNodeId());
      
      for (int i=0; i<handles.length; i++) {
        if (addrModel.getIndexOf(handles[i].handle.getAddress()) < 0) {
          boolean inserted = false;
          for (int j=0; (j<addrModel.getSize()) && !inserted; j++) {
            if (((InetSocketAddress) addrModel.getElementAt(j)).toString().compareTo(handles[i].handle.getAddress().toString()) > 0) {
              addrModel.insertElementAt(handles[i].handle.getAddress(), j);
              inserted = true;
            }
          }
          if (! inserted)
            addrModel.addElement(handles[i].handle.getAddress());
        }
      }
      addrModel.setSelectedItem(node.handle.getAddress());
      
      repaint();
    }
  }
}