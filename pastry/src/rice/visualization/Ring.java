/*
 * Created on Jul 21, 2004
 *
 */
package rice.visualization;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import rice.p2p.commonapi.Id;
import rice.pastry.dist.DistNodeHandle;
import rice.visualization.client.VisualizationClient;

/**
 * @author Jeff Hoye
 */
public class Ring {

  // ************** Non-Visual ******************
  /**
   * Vector of Node
   */
  private Vector nodes;

  /**
   * DistNodeHandle -> VisualizationClient
   */
  protected Hashtable clients;
  
//  protected Hashtable neighbors;

  protected Visualization visualization;
  
  /**
   * The ring name
   */
  public String name;
  
  public Ring(String name, DistNodeHandle handle) {
    this(name,handle,null);
  }
  
  public void setVisualization(Visualization v) {
    this.visualization = v;
  }
  
  public synchronized void addNode(Node node) {
    nodes.add(node);
  }
  
  protected synchronized Vector cloneNodes() {
    Vector r = new Vector();
    
    for (int i=0; i<nodes.size(); i++)
      r.add(nodes.elementAt(i));
    
    return r;
  }
  
  public Ring(String name, DistNodeHandle handle, Ring parent) {
    this.name = name;
    this.nodes = new Vector();
    this.clients = new Hashtable();
    //this.neighbors = new Hashtable();
    this.parent = parent;
    Node bootstrapNode = new Node(handle,this);
    nodes.add(bootstrapNode);
    if (parent != null) {
      childNum = parent.addChild(this);
      updateAngle(parent.numChildren());
      parent.buildAssociations(bootstrapNode); // in case bootstrap node is the same as the parent
    }    
  }

  public Node addNode(DistNodeHandle handle) {
    Node[] distnodes = getNodes();
    
    for (int i=0; i<distnodes.length; i++) 
      if (distnodes[i].handle.getNodeId().equals(handle.getNodeId()))
        return distnodes[i];
    //Thread.dumpStack();
    Node newNode = new Node(handle,this);
    addNode(newNode);
    if (parent != null) {
      parent.buildAssociations(newNode);
    } else {
      buildAssociations(newNode);
    }
    return newNode;
  }  

  /**
   * Checks self and all child rings.
   */
  public void buildAssociations(Node n) {
    if (n.ring != this) {
      // check self  
      Iterator i = cloneNodes().iterator();
      while (i.hasNext()) {
        Node n2 = (Node)i.next();
        if (n.handle.getId().equals(n2.handle.getId())) {
          n.addAssociation(n2);
          n2.addAssociation(n);
        }
      }
    }
    // check children  
    Iterator i2 = children.iterator();
    while (i2.hasNext()) {
      Ring r = (Ring)i2.next();
      r.buildAssociations(n);
    }
  }

  public Node[] getNodes() {
    return (Node[]) cloneNodes().toArray(new Node[0]);
  }
  
  public String toString() {
    return "Ring \""+name+"\" bootstrap: "+cloneNodes().get(0);
  }
  
  public int getState(DistNodeHandle node) {
    if (clients.get(node.getNodeId()) != null)
      return ((VisualizationClient) clients.get(node.getNodeId())).getState();
    else 
      return Visualization.STATE_UNKNOWN;
  }
  

  
  // **************** Visual stuff ***************
  public static final double[] RENDER_RADIUS = { 160.0, 60.0, 30.0 };
  public static final double DISTANCE_BUFFER = 20.0;
  public Ring parent;
  /**
   * 0 is the global ring
   * a numbered coordinate sets a degree offset based on the total number of rings
   */
  public int childNum = 0;  
  Vector children = new Vector();
  public double angle = 0; // this is your angle offset from the global ring in degrees
  public double unitX = 0; // unit positions based on angle
  public double unitY = 0;
  public int renderSize = 0;  // this is how big you are, 0 is the selected ring, 1 is smaller, 2 is smaller still
  boolean rootCenterIsStale = true; // only used by root, but calculated on all because I'm lazy, it's faster than checking for root anyway
  Point rootCenter = new Point(0,0);  
  int width = PastryRingPanel.PASTRY_RING_PANEL_WIDTH;
  int height = PastryRingPanel.PASTRY_RING_PANEL_HEIGHT;
  

  // ********************** Selection ************************
  /**
   * Attempts to select a node in own ring, then children's rings.
   */
  public Node getNode(int x, int y) {
    Iterator i = cloneNodes().iterator();
    while(i.hasNext()) {
      Node n = (Node)i.next();
      if ((n.selectionArea!=null) && (n.textLocation != null) &&
          (n.selectionArea.contains(x, y) || n.textLocation.contains(x, y))) {
        return n;
      }
    }
 /*   i = children.iterator();
    while(i.hasNext()) {
      Ring r = (Ring)i.next();
      Node n = r.getNode(x,y);
      if (n != null) {
        return n;
      }
    } */
    return null; 
  }

  public Ring getRing(int x, int y) {
    if (contains(x,y)) {
      return this;
    }
    Iterator i = children.iterator();
    while(i.hasNext()) {
      Ring r = (Ring)i.next();
      Ring sel = r.getRing(x,y);
      if (sel != null) {
        return sel;
      }
    }
    return null;
  }

  public boolean contains(int x, int y) {
    // calculate distance from center
    Point c = getCenter();
    int dx = x-c.x;
    int dy = y-c.y;
    double r = getRadius(); 
    return ((dx*dx)+(dy*dy) <= (r*r));     
  }

  // ********************* Drawing of tree ******************************
  public void paintTree(Graphics g, int w, int h) {
    width = w;
    height = h;
    prepSelf(g);
    Iterator i = children.iterator();
    while (i.hasNext()) {
      Ring r = (Ring)i.next();
      r.prepSelf(g);    
    }

    paintSelf(g);
    i = children.iterator();
    while (i.hasNext()) {
      Ring r = (Ring)i.next();
      r.paintSelf(g);    
    }
  }
  
  private void prepSelf(Graphics g) {
    Point p = getCenter();
    int r = (int)getRadius();
    g.setColor(Color.BLACK);   
    g.drawOval(p.x-r,p.y-r,r*2,r*2);
    if (visualization.highlightedRing == this) { // render highlighted as double ring
      g.setColor(new Color(220, 220, 255));
      int r2 = r-2;
      g.fillOval(p.x-r2,p.y-r2,r2*2,r2*2);          

      g.setColor(Color.black);
      g.setFont(new Font("Optima", Font.PLAIN, (int) getRadius()/4));
      int fontHeight = g.getFontMetrics().getMaxAscent();
      Rectangle2D rect = g.getFontMetrics().getStringBounds(name, g);

      g.drawString(name, (int) (p.x - rect.getWidth()/2), p.y + fontHeight/2);      
    }
    prepNodes(g, p, r);        
  }

  public void paintSelf(Graphics g) {
    Point p = getCenter();
    int r = (int)getRadius();
    paintConnections(g);
    paintNodes(g,p,r);
  }
  
  protected void paintConnections(Graphics g) {
    Node sel = visualization.getSelectedNode();
    Iterator i = cloneNodes().iterator();
    while(i.hasNext()) {
      Node node = (Node)i.next();
      if (!(node == sel)) 
        paintConnections(g, node, false);
    }
    
    if (sel != null)
      paintConnections(g, sel, true);
  }

  protected void paintConnections(Graphics g, Node node, boolean selected) {
    Color leafset = PastryRingPanel.LIGHT_LEAFSET_COLOR;
    Color routetable = PastryRingPanel.LIGHT_ROUTE_TABLE_COLOR;
    Color association = PastryRingPanel.LIGHT_ASSOC_COLOR;
    
    // paint the leafset
    if (selected) {
      leafset = PastryRingPanel.LEAFSET_COLOR;
      routetable = PastryRingPanel.ROUTE_TABLE_COLOR;
      association = PastryRingPanel.ASSOC_COLOR;
    }
    
    Iterator i = node.neighbors.iterator();
    while(i.hasNext()) {
      Node n2 = (Node)i.next();
      g.setColor(leafset);
      g.drawLine(node.location.x, node.location.y, n2.location.x, n2.location.y);
    }
    i = node.associations.iterator();
    while(i.hasNext()) {
      Node n2 = (Node)i.next();
      g.setColor(association);
      g.drawLine(node.location.x, node.location.y, n2.location.x, n2.location.y);
    }
  }


  private void paintNodes(Graphics g, Point center, int radius) {

    Iterator i = cloneNodes().iterator();
    while (i.hasNext()) {
      Node node = (Node)i.next();
      //node.location = idToLocation(node.handle.getId(), center, radius);
      node.selectionArea = new Rectangle((int) (node.location.x-(PastryRingPanel.NODE_DIAMETER/2)), (int) (node.location.y-(PastryRingPanel.NODE_DIAMETER/2)), 
                                         PastryRingPanel.NODE_DIAMETER, PastryRingPanel.NODE_DIAMETER);
      
      Color color = PastryRingPanel.NODE_COLOR_HEALTHY;
      
      if (getState(node.handle) == Visualization.STATE_FAULT)
        color = PastryRingPanel.NODE_COLOR_FAULT;
      
      if (getState(node.handle) == Visualization.STATE_DEAD)
        color = PastryRingPanel.NODE_COLOR_DEAD;
      
      if (getState(node.handle) == Visualization.STATE_UNKNOWN)
        color = PastryRingPanel.NODE_COLOR_UNKNOWN;

      paintNodeCircle(g, color, node.location.x, node.location.y);
      
      if (! (isSelected(node) || isHighlighted(node))) {
        g.setColor(Color.gray);
        g.setFont(new Font("Courier", Font.PLAIN, 8));
      } else if (isSelected(node)) {
        g.setColor(Color.red);
        g.setFont(new Font("Courier", Font.BOLD, 8));
      } else if (isHighlighted(node)) {
        g.setColor(Color.black);
        g.setFont(new Font("Courier", Font.BOLD, 8));
      }   
      
      Point text = getTextLocation(node.location);
      String string = node.handle.getNodeId().toString() + " " + node.handle.getAddress().getAddress().getHostAddress() + ":" + node.handle.getAddress().getPort();
      
      FontMetrics metrics = g.getFontMetrics();
      int fontHeight = metrics.getMaxAscent();
      Rectangle2D rect = metrics.getStringBounds(string, g);
      
      if (node.location.x < getCenter().x) {
        node.textLocation = new Rectangle((int) (text.x - rect.getWidth()), (int) (text.y - fontHeight/2),
                                         (int) rect.getWidth(), (int) rect.getHeight());
      } else {
        node.textLocation = new Rectangle((int) text.x, (int) (text.y - fontHeight/2),
                                         (int) rect.getWidth(), (int) rect.getHeight());
      }
        
      if (this.equals(visualization.getSelectedRing()))
        g.drawString(string, node.textLocation.x, node.textLocation.y + fontHeight);         
    }
  }

  public void prepNodes(Graphics g, Point center, int radius) {
    Iterator i = cloneNodes().iterator();
    while(i.hasNext()) {
      Node node = (Node)i.next(); 
      node.location = idToLocation(node.handle.getId(), center, radius);
    }
  }

  public Point getTextLocation(Point idDim) {
    //Dimension idDim = idToLocation(id);
    int diameter = (int)(getRadius()*2);
    int y = (int) (idDim.y - getCenter().y) * PastryRingPanel.NODE_TEXT_SPACING / diameter;
    int x = (int) (idDim.x - getCenter().x) * PastryRingPanel.NODE_TEXT_SPACING / diameter;
   
    return new Point((int) (idDim.x + x), (int) (idDim.y + y));
  }

  
  protected boolean isSelected(Node node) {
    Node selected = visualization.getSelectedNode();
    return node == selected;  
//    return ((selected != null) && (selected.handle.getId().equals(node.handle.getId())));
  }
  
  protected boolean isHighlighted(Node node) {
    Node highlighted = visualization.getHighlighted();
    return ((highlighted != null) && (highlighted.handle.getId().equals(node.handle.getId())));
  }
  
  public Point idToLocation(Id id, Point center, int radius) {
    BigDecimal idBig = new BigDecimal(new BigInteger(id.toStringFull(), 16));

    char[] max = new char[id.toStringFull().length()];
    for (int i=0; i<max.length; i++) max[i] = 'f';
    BigDecimal maxBig = new BigDecimal(new BigInteger(new String(max), 16));
    
    BigDecimal divBig = idBig.divide(maxBig, 10, BigDecimal.ROUND_HALF_UP);
    
    //System.out.println("CONVERTING " + id + " TO " + idBig + " AND COMPARING TO " + maxBig + " GIVES " + divBig);
    
    double frac = divBig.doubleValue() * 2 * Math.PI;
    
    return new Point((int) (center.x + Math.sin(frac) * radius),
                     (int) (center.y - Math.cos(frac) * radius));
  }

  protected void paintNodeCircle(Graphics g, Color color, int x, int y) { 
    int nodeRad = PastryRingPanel.NODE_DIAMETER/2;   
    g.setColor(color);
    g.fillOval(x-nodeRad, y-nodeRad, PastryRingPanel.NODE_DIAMETER, PastryRingPanel.NODE_DIAMETER);  
    g.setColor(Color.black);
    g.drawOval(x-nodeRad, y-nodeRad, PastryRingPanel.NODE_DIAMETER, PastryRingPanel.NODE_DIAMETER);      
  }
  

  // ********************** Calculation of centers ***************************
  /**
   * Called after the render sizes have been set, but need to calculate tree's positions
   */
//  private void calculatePositions() {
//    if (parent != null) throw new RuntimeException("Only expected to be called on root");    
//    rootCenterIsStale = true;
//  }

  /**
   * Calculates the center of the root based on global size calculations of the children.
   */
  private Point getRootCenter() {
    if (rootCenterIsStale) {
      rootCenterIsStale = false;
      int minX, minY, maxX, maxY;
      int rad = (int)getRadius();
      minX = -rad;
      minY = -rad;
      maxX = rad;
      maxY = rad;
      Iterator i = children.iterator();
      while(i.hasNext()) {
        Ring r = (Ring)i.next();
        Point p = r.getCenterBasedOnParent();
        rad = (int)r.getRadius();
        
        // calc mins/maxs
        int x1 = p.x-rad;
        int x2 = p.x+rad;
        int y1 = p.y-rad;
        int y2 = p.y+rad;
        
        if (x1 < minX)
          minX = x1;
        if (x2 > maxX)
          maxX = x2;
        if (y1 < minY)
          minY = y1;
        if (y2 > maxY)
          maxY = y2;        
      }
      
      int offX = maxX + minX;
      int offY = maxY + minY;
      offX/=2;
      offY/=2;
      
      
      int screenCenterX = width/2;
      int screenCenterY = height/2;
      // use min/max to calculate center      
      rootCenter = new Point(screenCenterX-offX,screenCenterY-offY);
//      rootCenter = new Point(screenCenterX,screenCenterY);
    }
    return rootCenter;
  }

  public Point getCenter() {
    if (parent == null) {
      return getRootCenter();
    } else {
      Point p1 = getCenterBasedOnParent();
      Point p2 = parent.getCenter();
      // need to call getCenterBasedOnParent()+parent.getCenter();
      return new Point(p1.x+p2.x,p1.y+p2.y);
    }
  }

  /** 
   * Calculates your center as an offset of your parent's center.
   * @return the point that is your center.
   */ 
  public Point getCenterBasedOnParent() {
    if (parent == null) {
      return new Point(0,0);
    } else {
      double dist = parent.getRadius() + getRadius() + DISTANCE_BUFFER;
      double x = unitX*dist;
      double y = unitY*dist;
      return new Point((int)x,(int)y);
    }
  }
 
  // ************* Used to set your size **********************
  public void select() {
    if (parent == null) {
      setRenderSize(0);
      setChildrenSize(1,null);
      rootCenterIsStale = true;
      //calculatePositions();
    } else {
      parent.setRenderSize(1);
      parent.setChildrenSize(2,this);
      setRenderSize(0);
      parent.rootCenterIsStale = true;
      //parent.calculatePositions();
    }
  }
  
  private void setRenderSize(int i) {
    oldRadius = getRadius();
    renderSize = i;
  }

  private void setChildrenSize(int childSize, Ring excludeRing) {
    Iterator i = children.iterator();
    while (i.hasNext()) {
      Ring r = (Ring)i.next();
      if (r != excludeRing)
        r.setRenderSize(childSize);
    }
  }

  /**
   * @return radius based on rendersize
   */
//  public double getRadius() {
//    return RENDER_RADIUS[renderSize]; 
//  }

  double oldRadius = 0;
  public double getRadius() {
    double dr = RENDER_RADIUS[renderSize]-oldRadius;
    dr/=(double)visualization.NUM_STEPS;
    return oldRadius+(dr*visualization.curStep);
  }

 
  // ************ Used to set up the angles *******************
  public int numChildren() {
    return children.size();
  }
  
  public int addChild(Ring r) {
    notifyChildrenOfNumberOfChildren(children.size()+1);
    children.add(r);
    return children.size();
  }
  
  private void notifyChildrenOfNumberOfChildren(int numChildren) {
    Iterator i = children.iterator();
    while(i.hasNext()) {
      Ring r = (Ring)i.next();
      r.updateNumChildren(numChildren);
    }
  }
  
  private void updateNumChildren(int numChildren) {
    updateAngle(numChildren);
  }

  private void updateAngle(int numChildren) {
    calculateAngle(numChildren);
    unitX = Math.sin(Math.toRadians(angle));
    unitY = -Math.cos(Math.toRadians(angle));
  }
  
  /**
   * This function chooses your offset angle based on the global ring, it's number of chidren, and your childnum
   * It's pretty hard coded and limited to 4 children.  Use a real graph algorithm in the future
   * @param numChildren
   */
  private void calculateAngle(int numChildren) {
    switch (numChildren) {
      case 1:
        if (childNum != 1) throw new RuntimeException("unexpected number of children childnum:"+childNum+" numChildren:"+numChildren);
        angle = 180; // below
        break;

      case 2:
        switch (childNum) {
          case 1:
            angle = (360/3)*2; // lower left
            break;
          case 2:
            angle = (360/3); // lower right
            break;
          default:
            throw new RuntimeException("unexpected number of children childnum:"+childNum+" numChildren:"+numChildren);
        }
        break;
        
      case 3:
        switch (childNum) {
          case 1:
            angle = (360/3)*2; // lower left
            break;
          case 2:
            angle = (360/3); // lower right
            break;
          case 3:
            angle = 0; // top
            break;
          default:
            throw new RuntimeException("unexpected number of children childnum:"+childNum+" numChildren:"+numChildren);
        }
        break;
        
      case 4:
        switch (childNum) {
          case 1:
            angle = (360/8)*5; // lower left
            break;
          case 2:
            angle = (360/8)*3; // lower right
            break;
          case 3:
            angle = (360/8)*7; // upper left
            break;
          case 4:
            angle = (360/8); // upper right
            break;
          default:
            throw new RuntimeException("unexpected number of children childnum:"+childNum+" numChildren:"+numChildren);
        }
        break;

      default:
        throw new RuntimeException("unexpected number of children childnum:"+childNum+" numChildren:"+numChildren);    
    }   
  }

  
}
