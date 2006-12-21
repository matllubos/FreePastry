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

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.InetSocketAddress;
import java.util.*;

import javax.swing.*;

import rice.pastry.dist.DistNodeHandle;
import rice.visualization.data.Data;

public class InformationPanel extends JPanel {
  
  public static int INFORMATION_PANEL_WIDTH = 350;
  public static int INFORMATION_PANEL_HEIGHT = PastryRingPanel.PASTRY_RING_PANEL_HEIGHT/3;
  public static String TOUCH_BUTTON_START = "Touch Ring";
  public static String TOUCH_BUTTON_STOP = "Stop Touching Ring";
  
  protected Visualization visualization;
  
  protected DefaultComboBoxModel model;
  
  protected DefaultComboBoxModel addrModel;
  
  protected JTextField text;
  
  protected boolean changing = false;
    
  public InformationPanel(Visualization visualization) {
    this.visualization = visualization;
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Ring Information"),
                                                 BorderFactory.createEmptyBorder(5,5,5,5)));
    
    model = new DefaultComboBoxModel();
    JComboBox combo = new JComboBox(model);
    add(combo);
    combo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent state) { 
        if ((state.getStateChange() == state.SELECTED) && (! changing)) {
          InformationPanel.this.visualization.setSelected((rice.pastry.Id) state.getItem(),InformationPanel.this.visualization.getSelectedRing());
        }
      }
    });
    
    addrModel = new DefaultComboBoxModel();
    JComboBox addrCombo = new JComboBox(addrModel);
    add(addrCombo);
    addrCombo.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent state) { 
        if ((state.getStateChange() == state.SELECTED) && (! changing)) {
          InformationPanel.this.visualization.setSelected((InetSocketAddress) state.getItem(),InformationPanel.this.visualization.getSelectedRing());
        }
      }
    });
    
    text = new JTextField(getText());
    add(text);
    
    final JButton touchButton = new JButton(TOUCH_BUTTON_START);
    touchButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent arg0) {
        if (touchButton.getText().equals(TOUCH_BUTTON_START)) {
          touchButton.setText(TOUCH_BUTTON_STOP); 
          if (InformationPanel.this.visualization.selectedRing != null) {
            InformationPanel.this.visualization.selectedRing.touchAllNodes();
          }          
        } else {
          touchButton.setText(TOUCH_BUTTON_START);           
          if (InformationPanel.this.visualization.selectedRing != null) {
            InformationPanel.this.visualization.selectedRing.stopTouchingNodes();
          }
        }

      }
    });
    add(touchButton);
  }
  
  protected String getText() {
    return model.getSize() + " live nodes";
  }
  
  public Dimension getPreferredSize() {
    return new Dimension(INFORMATION_PANEL_WIDTH, INFORMATION_PANEL_HEIGHT);
  }
  
  public void nodeSelected(Node node, Data data) {
    if (node != null) {
      changing = true;
      Node[] handles = visualization.getNodes();
      Arrays.sort(handles, new Comparator() {
        public int compare(Object a, Object b) {
          return ((Node) a).handle.getNodeId().compareTo(((Node) b).handle.getNodeId());
        }
        
        public boolean equals() {
          return false;
        }
      });
      
      model.removeAllElements();
      
      for (int i=0; i<handles.length; i++) 
        if (handles[i].ring == node.ring)
          model.addElement(handles[i].handle.getNodeId());
      
      model.setSelectedItem(node.handle.getNodeId());
      
      Arrays.sort(handles, new Comparator() {
        public int compare(Object a, Object b) {
          return ((Node) a).handle.getAddress().toString().compareTo(((Node) b).handle.getAddress().toString());
        }
        
        public boolean equals() {
          return false;
        }
      });
                  
                  
      addrModel.removeAllElements();
      
      for (int i=0; i<handles.length; i++) 
        if (handles[i].ring == node.ring)
          addrModel.addElement(handles[i].handle.getAddress());
      
      addrModel.setSelectedItem(node.handle.getAddress());
      
      text.setText(getText());
      
      repaint();
      changing = false;
    }
  }
}