package rice.visualization;

import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import java.math.*;

import rice.p2p.commonapi.*;
import rice.pastry.dist.*;

public class PastryRingPanel extends JPanel implements MouseListener, MouseMotionListener {
  
  public static int PASTRY_RING_PANEL_BORDER = 20; 
  public static int PASTRY_RING_PANEL_WIDTH = 835;
  public static int PASTRY_RING_PANEL_HEIGHT = 520;
  public static int PASTRY_RING_DIAMETER = 440;
  
  public static int NODE_TEXT_SPACING = 15;
  public static int NODE_DIAMETER = 6;
  
  public static int TICK_LENGTH = 10;
  
  public static Color NODE_COLOR_HEALTHY = Color.green;
  public static Color NODE_COLOR_FAULT = Color.yellow;
  public static Color NODE_COLOR_UNKNOWN = Color.red;
  public static Color NODE_COLOR_DEAD = Color.gray;
  
  public static int LEGEND_LOCATION_X = 30;
  public static int LEGEND_LOCATION_Y = 30;
  public static int LEGEND_SPACING = 10;
  
  public static Color LEAFSET_COLOR = new Color(140, 140, 255);
  public static Color ROUTE_TABLE_COLOR = new Color(255, 140, 140);
  public static Color LIGHT_LEAFSET_COLOR = new Color(230, 230, 255);
  public static Color LIGHT_ROUTE_TABLE_COLOR = new Color(255, 230, 230);
  
  protected Visualization visualization;
  
  protected Rectangle[] nodeLocations;
  
  protected Rectangle[] textLocations;
  
  protected DistNodeHandle[] nodes;
  
  public PastryRingPanel(Visualization visualization) {
    this.visualization = visualization;
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
    
    g.setColor(Color.white);
    g.fillRect(PASTRY_RING_PANEL_BORDER,
               PASTRY_RING_PANEL_BORDER, 
               PASTRY_RING_PANEL_WIDTH-(2*PASTRY_RING_PANEL_BORDER), 
               PASTRY_RING_PANEL_HEIGHT-(2*PASTRY_RING_PANEL_BORDER));
    g.setColor(Color.black);
    g.fillOval((PASTRY_RING_PANEL_WIDTH-PASTRY_RING_DIAMETER)/2, 
               (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2, 
               PASTRY_RING_DIAMETER,
               PASTRY_RING_DIAMETER);
    g.setColor(Color.white);
    g.fillOval((PASTRY_RING_PANEL_WIDTH-PASTRY_RING_DIAMETER)/2 + 1, 
               (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2 + 1, 
               PASTRY_RING_DIAMETER-2, 
               PASTRY_RING_DIAMETER-2);
    g.setColor(Color.black);
    g.drawLine(PASTRY_RING_PANEL_WIDTH/2, (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2-TICK_LENGTH/2,
               PASTRY_RING_PANEL_WIDTH/2, (PASTRY_RING_PANEL_HEIGHT-PASTRY_RING_DIAMETER)/2+TICK_LENGTH/2);
               
    paintLegend(g);
    paintComponentNodes(g);
  } 
  
  protected void paintLegend(Graphics g) {
    g.setColor(Color.black);
    g.setFont(new Font("Courier", Font.BOLD, 10));
    int fontHeight = g.getFontMetrics().getMaxAscent();
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
    g.setColor(Color.black);
    g.fillOval(x, y, NODE_DIAMETER, NODE_DIAMETER);      
    
    g.setColor(color);
    g.fillOval(x+1, y+1, NODE_DIAMETER-2, NODE_DIAMETER-2);  
  }
  
  protected void paintConnections(Graphics g, DistNodeHandle node) {
    Color leafset = LIGHT_LEAFSET_COLOR;
    Color routetable = LIGHT_ROUTE_TABLE_COLOR;
    
    // paint the leafset
    if (isSelected(node)) {
      leafset = LEAFSET_COLOR;
      routetable = ROUTE_TABLE_COLOR;
    }
    
    DistNodeHandle[] handles = visualization.getNeighbors(node);
    
    for (int j=0; j<handles.length; j++) {
      paintConnection(g, node.getId(), handles[j].getId(), leafset);
    }
  }
  
  protected void paintConnections(Graphics g) {
    DistNodeHandle sel = null;
    
    for (int i=0; i<nodes.length; i++) {
      if ((! isSelected(nodes[i])) && (visualization.getState(nodes[i]) == Visualization.STATE_ALIVE)) 
        paintConnections(g, nodes[i]);
      else
        sel = nodes[i];
    }
    
    if (sel != null)
      paintConnections(g, sel);
  }
  
  protected void paintComponentNodes(Graphics g) {
    nodes = visualization.getNodes();
    Rectangle[] nodeLocations = new Rectangle[nodes.length];
    Rectangle[] textLocations = new Rectangle[nodes.length];
    
    paintConnections(g);
    
    for (int i=0; i<nodes.length; i++) {
      Dimension location = idToLocation(nodes[i].getId());
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
      
      Dimension text = getTextLocation(nodes[i].getId());
      String string = nodes[i].getNodeId().toString() + " " + nodes[i].getAddress().getAddress().getHostAddress() + ":" + nodes[i].getAddress().getPort();
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
    
    //System.out.println("CONVERTING " + id + " TO " + idBig + " AND COMPARING TO " + maxBig + " GIVES " + divBig);
    
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
  
  public void nodeHighlighted(DistNodeHandle node) {
    repaint();
  }
  
  public void nodeSelected(DistNodeHandle node) {
    repaint();
  }

  protected boolean isSelected(DistNodeHandle node) {
    DistNodeHandle selected = visualization.getSelected();
    return ((selected != null) && (selected.getId().equals(node.getId())));
  }

  protected boolean isHighlighted(DistNodeHandle node) {
    DistNodeHandle highlighted = visualization.getHighlighted();
    return ((highlighted != null) && (highlighted.getId().equals(node.getId())));
  }
  
  public void mouseClicked(MouseEvent e) {
    if (nodeLocations == null)
      return;
    
    for (int i=0; i<nodeLocations.length; i++) {
      if (nodeLocations[i].contains(e.getX(), e.getY()) || textLocations[i].contains(e.getX(), e.getY())) {
        visualization.setSelected(nodes[i]);

        return;
      }
    }
    
    visualization.setSelected((DistNodeHandle) null);
  }
  
  public void mouseMoved(MouseEvent e) {
    if (nodeLocations == null)
      return;
    
    for (int i=0; i<nodeLocations.length; i++) {
      if (nodeLocations[i].contains(e.getX(), e.getY()) || textLocations[i].contains(e.getX(), e.getY())) {
        visualization.setHighlighted(nodes[i]);
        return;
      }
    } 
    
    visualization.setHighlighted(null);
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