/*
 * Created on Jul 22, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.visualization;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Vector;

import rice.pastry.dist.DistNodeHandle;

/**
 * @author Jeff Hoye
 */
public class Node {
  DistNodeHandle handle;
  Point location;
  Ring ring;
  Rectangle selectionArea;
  Rectangle textLocation;
  
  
  /**
   * Vector of Node 
   * these are neighbors in the same ring.
   */
  Vector neighbors = new Vector();

  /**
   * Vector of Node 
   * These correspond to the same node, but in different rings.
   */
  HashSet associations = new HashSet();

    
  public Node(DistNodeHandle h, Ring r) {
    handle = h;
    ring = r;
  }
    
  public String toString() {
    return ring.name+":"+handle;
  }
  
  public void addAssociation(Node n) {
    if (n.handle.getId().equals(this.handle.getId())) {
      associations.add(n);
    }
  }
}
