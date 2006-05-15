
package rice.p2p.past.gc;

import rice.environment.Environment;
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
    
    GCEndpoint gce = new GCEndpoint(node.buildEndpoint(application, instance));
    gce.register();
    return gce;
  }
  
  public Endpoint buildEndpoint(Application application, String instance) {
    return new GCEndpoint(node.buildEndpoint(application, instance));
  }
  
  /**
   * This returns a Endpoint specific to the given application and
   * instance name to the application, which the application can then use in
   * order to send an receive messages.  This method allows advanced 
   * developers to specify which "port" on the node they wish their
   * application to register as.  This "port" determines which of the
   * applications on top of the node should receive an incoming 
   * message.
   *
   * NOTE: Use of this method of registering applications is recommended only
   * for advanced users - 99% of all applications should just use the
   * other registerApplication
   * 
   * @param application The Application
   * @param port The port to use
   * @return The endpoint specific to this applicationk, which can be used for
   *         message sending/receiving.
   */
//  public Endpoint registerApplication(Application application, int port) {
//    return new GCEndpoint(node.registerApplication(application, port));
//  }
  
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

  /* (non-Javadoc)
   * @see rice.p2p.commonapi.Node#getEnvironment()
   */
  public Environment getEnvironment() {
    return node.getEnvironment();
  }
}




