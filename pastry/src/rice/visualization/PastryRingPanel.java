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

import rice.p2p.commonapi.*;
import rice.pastry.dist.*;
import rice.visualization.data.Data;

public class PastryRingPanel extends JPanel implements MouseListener, MouseMotionListener {
  
  public static int PASTRY_RING_PANEL_BORDER = 20; 
  public static int PASTRY_RING_PANEL_WIDTH = 835;
  public static int PASTRY_RING_PANEL_HEIGHT = 500;
  public static int PASTRY_RING_DIAMETER = 420;
  
  public static int NODE_TEXT_SPACING = 15;
  public static int NODE_DIAMETER = 6;
  
  public static int TICK_LENGTH = 10;
  
  public static Color NODE_COLOR_HEALTHY = Color.green;
  public static Color NODE_COLOR_FAULT = Color.yellow;
  public static Color NODE_COLOR_UNKNOWN = Color.red;
  public static Color NODE_COLOR_DEAD = Color.gray;
  
  public static int LEGEND_LOCATION_X = 30;
  public static int LEGEND_LOCATION_Y = 40;
  public static int LEGEND_SPACING = 10;
  
  public static Color LEAFSET_COLOR = new Color(140, 140, 255);
  public static Color ROUTE_TABLE_COLOR = new Color(255, 140, 140);
  public static Color ASSOC_COLOR = new Color(80, 255, 80);
  public static Color LIGHT_LEAFSET_COLOR = new Color(230, 230, 255);
  public static Color LIGHT_ROUTE_TABLE_COLOR = new Color(255, 230, 230);
  public static Color LIGHT_ASSOC_COLOR = new Color(140, 255, 140);
  
  protected Visualization visualization;
  
  protected Rectangle[] nodeLocations;
  
  protected Rectangle[] textLocations;
  
  protected Node[] nodes;
  
  protected Ring ring;
  
  public PastryRingPanel(Visualization visualization) {
    this.visualization = visualization;
    this.ring = visualization.getSelectedRing();
    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Pastry Ring"),
                                                 BorderFactory.createEmptyBorder(5,5,5,5)));
    addMouseListener(this);
    addMouseMotionListener(this);
  }
  
  public Dimension getPreferredSize() {
    return new Dimension(PASTRY_RING_PANEL_WIDTH, PASTRY_RING_PANEL_HEIGHT);
  }
  
  public void paintComponent(Graphics g) { 
    // First paint background 
    super.paintComponent(g); 
    
    g.setColor(Color.white); // paint background white
    g.fillRect(PASTRY_RING_PANEL_BORDER,
               PASTRY_RING_PANEL_BORDER, 
               PASTRY_RING_PANEL_WIDTH-(2*PASTRY_RING_PANEL_BORDER), 
               PASTRY_RING_PANEL_HEIGHT-(2*PASTRY_RING_PANEL_BORDER));


    //paintMainCircle(g);
    paintRings(g, PASTRY_RING_PANEL_WIDTH, PASTRY_RING_PANEL_HEIGHT);
    //paintComponentNodes(g);
    paintLegend(g);
  } 
  
  protected void paintRings(Graphics g, int w, int h) {
    Ring root = visualization.getRingByIndex(0); // root
    root.paintTree(g, w, h);
  }
  
  protected void paintMainCircle(Graphics g) {
    g.setColor(Color.black); // draw major circle
    g.drawOval((PASTRY_RING_PANEL_WIDTH-PASTRY_RING_DIAMETER)/2, 
               (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2, 
               PASTRY_RING_DIAMETER,
               PASTRY_RING_DIAMETER);
//    g.setColor(Color.white);
//    g.fillOval((PASTRY_RING_PANEL_WIDTH-PASTRY_RING_DIAMETER)/2 + 1, 
//               (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2 + 1, 
//               PASTRY_RING_DIAMETER-2, 
//               PASTRY_RING_DIAMETER-2);
//    g.setColor(Color.black); // draw "0" tickmark
    g.drawLine(PASTRY_RING_PANEL_WIDTH/2, (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2-TICK_LENGTH/2,
               PASTRY_RING_PANEL_WIDTH/2, (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2+TICK_LENGTH/2);    
  }
  
  protected void paintLegend(Graphics g) {
    g.setColor(Color.black);
    g.setFont(new Font("Courier", Font.BOLD, 10));
    int fontHeight = g.getFontMetrics().getMaxAscent();
    g.drawString(visualization.selectedRing.name, LEGEND_LOCATION_X, LEGEND_LOCATION_Y-10+fontHeight-5);
    g.drawString("Legend:", LEGEND_LOCATION_X, LEGEND_LOCATION_Y+fontHeight-5);
    
    g.setColor(ROUTE_TABLE_COLOR);
    g.drawLine(LEGEND_LOCATION_X, LEGEND_LOCATION_Y + LEGEND_SPACING + NODE_DIAMETER/2,
               LEGEND_LOCATION_X + NODE_DIAMETER, LEGEND_LOCATION_Y + LEGEND_SPACING + NODE_DIAMETER/2);
    
    g.setColor(LEAFSET_COLOR);
    g.drawLine(LEGEND_LOCATION_X, LEGEND_LOCATION_Y + 2*LEGEND_SPACING + NODE_DIAMETER/2,
               LEGEND_LOCATION_X + NODE_DIAMETER, LEGEND_LOCATION_Y + 2*LEGEND_SPACING + NODE_DIAMETER/2);

    paintNode(g, NODE_COLOR_HEALTHY, LEGEND_LOCATION_X, LEGEND_LOCATION_Y + 3*LEGEND_SPACING);
    paintNode(g, NODE_COLOR_FAULT, LEGEND_LOCATION_X, LEGEND_LOCATION_Y + 4*LEGEND_SPACING);
    paintNode(g, NODE_COLOR_UNKNOWN, LEGEND_LOCATION_X, LEGEND_LOCATION_Y + 5*LEGEND_SPACING);
    paintNode(g, NODE_COLOR_DEAD, LEGEND_LOCATION_X, LEGEND_LOCATION_Y + 6*LEGEND_SPACING); 

    g.setFont(new Font("Courier", Font.PLAIN, 8));
    g.setColor(Color.black);
    fontHeight = g.getFontMetrics().getMaxAscent();
    g.drawString("Route Table Connection", LEGEND_LOCATION_X + 2*NODE_DIAMETER, LEGEND_LOCATION_Y + LEGEND_SPACING + fontHeight);
    g.drawString("Leafset Connection", LEGEND_LOCATION_X + 2*NODE_DIAMETER, LEGEND_LOCATION_Y + 2*LEGEND_SPACING + fontHeight);
    g.drawString("Normal", LEGEND_LOCATION_X + 2*NODE_DIAMETER, LEGEND_LOCATION_Y + 3*LEGEND_SPACING + fontHeight);
    g.drawString("Fault", LEGEND_LOCATION_X + 2*NODE_DIAMETER, LEGEND_LOCATION_Y + 4*LEGEND_SPACING + fontHeight);
    g.drawString("Unknown", LEGEND_LOCATION_X + 2*NODE_DIAMETER, LEGEND_LOCATION_Y + 5*LEGEND_SPACING + fontHeight);
    g.drawString("Dead", LEGEND_LOCATION_X + 2*NODE_DIAMETER, LEGEND_LOCATION_Y + 6*LEGEND_SPACING + fontHeight);

  }

  protected void paintNode(Graphics g, Color color, int x, int y) {    
    g.setColor(color);
    g.fillOval(x, y, NODE_DIAMETER, NODE_DIAMETER);  
    g.setColor(Color.black);
    g.drawOval(x, y, NODE_DIAMETER, NODE_DIAMETER);      
  }
  
  protected void paintConnections(Graphics g, Node node, Ring r) {
//    Color leafset = LIGHT_LEAFSET_COLOR;
//    Color routetable = LIGHT_ROUTE_TABLE_COLOR;
//    
//    // paint the leafset
//    if (isSelected(node)) {
//      leafset = LEAFSET_COLOR;
//      routetable = ROUTE_TABLE_COLOR;
//    }
//    
//    DistNodeHandle[] handles = visualization.getNeighbors(node);
//    
//    for (int j=0; j<handles.length; j++) {
//      paintConnection(g, node.handle.getId(), handles[j].getId(), leafset);
//    }
  }
  
  protected void paintConnections(Graphics g) {
    Node sel = null;
    
    for (int i=0; i<nodes.length; i++) {
      if ((! isSelected(nodes[i])) && (visualization.getState(nodes[i]) == Visualization.STATE_ALIVE)) 
        paintConnections(g, nodes[i],ring);
      else
        sel = nodes[i];
    }
    
    if (sel != null)
      paintConnections(g, sel,ring);
  }
  
  protected void paintComponentNodes(Graphics g) {
    ring = visualization.getSelectedRing();
    nodes = visualization.getNodes(ring);
    Rectangle[] nodeLocations = new Rectangle[nodes.length];
    Rectangle[] textLocations = new Rectangle[nodes.length];
    
    paintConnections(g);
    
    for (int i=0; i<nodes.length; i++) {
      Dimension location = idToLocation(nodes[i].handle.getId());
      nodeLocations[i] = new Rectangle((int) (location.getWidth()-(NODE_DIAMETER/2)), (int) (location.getHeight()-(NODE_DIAMETER/2)), 
                                      NODE_DIAMETER, NODE_DIAMETER);
      
      Color color = NODE_COLOR_HEALTHY;
      
      if (visualization.getState(nodes[i]) == Visualization.STATE_FAULT)
        color = NODE_COLOR_FAULT;
      
      if (visualization.getState(nodes[i]) == Visualization.STATE_DEAD)
        color = NODE_COLOR_DEAD;
      
      if (visualization.getState(nodes[i]) == Visualization.STATE_UNKNOWN)
        color = NODE_COLOR_UNKNOWN;

      paintNode(g, color, (int) nodeLocations[i].getX(), (int) nodeLocations[i].getY());

      if (! (isSelected(nodes[i]) || isHighlighted(nodes[i]))) {
        g.setColor(Color.gray);
        g.setFont(new Font("Courier", Font.PLAIN, 8));
      } else if (isSelected(nodes[i])) {
        g.setColor(Color.red);
        g.setFont(new Font("Courier", Font.BOLD, 8));
      } else if (isHighlighted(nodes[i])) {
        g.setColor(Color.black);
        g.setFont(new Font("Courier", Font.BOLD, 8));
      }
      
      Dimension text = getTextLocation(nodes[i].handle.getId());
      String string = nodes[i].handle.getNodeId().toString() + " " + 
        nodes[i].handle.getInetSocketAddress().getAddress().getHostAddress() + 
        ":" + nodes[i].handle.getInetSocketAddress().getPort();
      FontMetrics metrics = g.getFontMetrics();
      int fontHeight = metrics.getMaxAscent();
      Rectangle2D rect = metrics.getStringBounds(string, g);
      
      if (text.getWidth() < PASTRY_RING_PANEL_WIDTH/2) {
        textLocations[i] = new Rectangle((int) (text.getWidth() - rect.getWidth()), (int) (text.getHeight() - fontHeight/2),
                                         (int) rect.getWidth(), (int) rect.getHeight());
      } else {
        textLocations[i] = new Rectangle((int) text.getWidth(), (int) (text.getHeight() - fontHeight/2),
                                         (int) rect.getWidth(), (int) rect.getHeight());
      }
        
        g.drawString(string, (int) textLocations[i].getX(), (int) (textLocations[i].getY() + fontHeight));
    }
    
    this.nodeLocations = nodeLocations;
    this.textLocations = textLocations;
  }
  
  protected void paintConnection(Graphics g, Id id1, Id id2, Color c) {
    Dimension dim1 = idToLocation(id1);
    Dimension dim2 = idToLocation(id2);
    
    g.setColor(c);
    g.drawLine((int) dim1.getWidth(), (int) dim1.getHeight(), (int) dim2.getWidth(), (int) dim2.getHeight());
  }
  
  public Dimension idToLocation(Id id) {
    BigDecimal idBig = new BigDecimal(new BigInteger(id.toStringFull(), 16));

    char[] max = new char[id.toStringFull().length()];
    for (int i=0; i<max.length; i++) max[i] = 'f';
    BigDecimal maxBig = new BigDecimal(new BigInteger(new String(max), 16));
    
    BigDecimal divBig = idBig.divide(maxBig, 10, BigDecimal.ROUND_HALF_UP);
    
    //System.outt.println("CONVERTING " + id + " TO " + idBig + " AND COMPARING TO " + maxBig + " GIVES " + divBig);
    
    double frac = divBig.doubleValue() * 2 * Math.PI;
    
    return new Dimension((int) (PASTRY_RING_PANEL_WIDTH + Math.sin(frac) * PASTRY_RING_DIAMETER)/2,
                         (int) (PASTRY_RING_PANEL_HEIGHT - (PASTRY_RING_PANEL_HEIGHT + Math.cos(frac) * PASTRY_RING_DIAMETER)/2));
  }
  
  public Dimension getTextLocation(Id id) {
    Dimension idDim = idToLocation(id);
    
    int height = (int) (idDim.getHeight() - (PASTRY_RING_PANEL_HEIGHT/2)) * NODE_TEXT_SPACING / PASTRY_RING_DIAMETER;
    int width = (int) (idDim.getWidth() - (PASTRY_RING_PANEL_WIDTH/2)) * NODE_TEXT_SPACING / PASTRY_RING_DIAMETER;
   
    return new Dimension((int) (idDim.getWidth() + width), (int) (idDim.getHeight() + height));
  }
  
  public void nodeHighlighted(Node node) {
    repaint();
  }
  
  public void nodeSelected(Node node, Data data) {
    repaint();
  }

  protected boolean isSelected(Node node) {
    Node selected = visualization.getSelectedNode();
    return ((selected != null) && (selected.handle.getId().equals(node.handle.getId())));
  }

  protected boolean isHighlighted(Node node) {
    Node highlighted = visualization.getHighlighted();
    return ((highlighted != null) && (highlighted.handle.getId().equals(node.handle.getId())));
  }
  
  public void mouseClicked(MouseEvent e) {
    switch (e.getButton()) {
      case MouseEvent.BUTTON1:  

        final Node n = visualization.getNode(e.getX(), e.getY());
        if (n == null) {
          final Ring r = visualization.getRing(e.getX(), e.getY());
          if (r == null) {
            Thread t = new Thread() {
              public void run() {
                visualization.setSelected((Node) null);
              }
            };
            
            t.start();
          } else { 
            Thread t = new Thread() {
              public void run() {
                visualization.selectRing(r);
              }
            };
            
            t.start();            
          }
        } else {
          Thread t = new Thread() {
            public void run() {
              visualization.setSelected(n);
            }
          };
          
          t.start();
        }

        break;
      case MouseEvent.BUTTON3:  
        switchRings();
        break;
    }
  }
  
  int ringIndex = 0;
  public void switchRings() {
    ringIndex++;
    if (ringIndex >= visualization.getNumRings()) 
      ringIndex = 0;
    Ring r = visualization.getRingByIndex(ringIndex);
    visualization.selectRing(r);
  }
  
  public void mouseMoved(MouseEvent e) {
    Node n = visualization.getNode(e.getX(), e.getY());
    if (n == null) {
      Ring r = visualization.getRing(e.getX(), e.getY());
      visualization.setHighlighted(null,r);
    } else {
      visualization.setHighlighted(n,null);
    }
  }
  
  public void mouseDragged(MouseEvent e) {
  }
  
  public void mouseEntered(MouseEvent e) {
  }
  
  public void mouseExited(MouseEvent e) {
  }
  
  public void mousePressed(MouseEvent e) {
  }
  
  public void mouseReleased(MouseEvent e) {
  }
}
