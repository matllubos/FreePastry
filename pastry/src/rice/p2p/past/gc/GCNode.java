
package rice.p2p.past.gc;

import rice.p2p.commonapi.*;

/**
 * @(#) GCNode.java
 *
 * This class wraps a Node
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class GCNode implements Node {
  
  /**
   * The node which this mulitring node is wrapping
   */
  protected Node node;
  
  /**
   * Constructor
   *
   * @param node The node which this multiring node is wrapping
   * @param ringId The Id of this node's ring
   */
  public GCNode(Node node) {
    this.node = node;
  }
  
  /**
   * This returns a VirtualizedNode specific to the given application and
   * instance name to the application, which the application can then use
   * in order to send an receive messages.
   *
   * @param application The Application
   * @param instance An identifier for a given instance
   * @return The endpoint specific to this applicationk, which can be used
   * for message sending/receiving.
   */
  public Endpoint registerApplication(Application application, String instance) {
    return new GCEndpoint(node.registerApplication(application, instance));
  }
  
  /**
   * Method which returns the node handle to the local node
   *
   * @return A handle to the local node
   */
  public NodeHandle getLocalNodeHandle() {
    return node.getLocalNodeHandle();
  }
  
  /**
   * Returns the Id of this node
   *
   * @return This node's Id
   */
  public Id getId() {
    return node.getId();
  }
  
  /**
   * Returns a factory for Ids specific to this node's protocol.
   *
   * @return A factory for creating Ids.
   */
  public IdFactory getIdFactory() {
    return new GCIdFactory(node.getIdFactory());
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "{GCNode " + node + "}";
  }
}




