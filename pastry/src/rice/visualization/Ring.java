/*
 * Created on Jul 21, 2004
 *
 */
package rice.visualization;

import java.util.Hashtable;
import java.util.Vector;

import rice.pastry.dist.DistNodeHandle;
import rice.visualization.data.Data;

/**
 * @author Jeff Hoye
 */
public class Ring {
  /**
   * Vector of DistNodeHandle
   */
  public Vector nodes;

  /**
   * DistNodeHandle -> VisualizationClient
   */
  protected Hashtable clients;
  
  protected Hashtable neighbors;
  
  /**
   * The ring name
   */
  public String name;
  
  
  public Ring(String name, DistNodeHandle handle) {
    this.name = name;
    this.nodes = new Vector();
    this.clients = new Hashtable();
    this.neighbors = new Hashtable();

    nodes.add(handle);
  }

  public void addNode(DistNodeHandle handle) {
    DistNodeHandle[] distnodes = getNodes();
    
    for (int i=0; i<distnodes.length; i++) 
      if (distnodes[i].getNodeId().equals(handle.getNodeId()))
        return;
    //Thread.dumpStack();
    nodes.addElement(handle);
  }  

  public DistNodeHandle[] getNodes() {
    return (DistNodeHandle[]) nodes.toArray(new DistNodeHandle[0]);
  }
  
  public String toString() {
    return "Ring \""+name+"\" bootstrap: "+nodes.get(0);
  }
}
