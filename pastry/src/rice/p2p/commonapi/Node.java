
package rice.p2p.commonapi;

/**
 * @(#) Node.java
 *
 * Interface which represents a node in a peer-to-peer system, regardless of
 * the underlying protocol.  This represents a factory, in a sense, that will
 * give a application an Endpoint which it can use to send and receive
 * messages.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface Node {

  /**
   * This returns a VirtualizedNode specific to the given application and
   * instance name to the application, which the application can then use in
   * order to send an receive messages.
   * 
   * @param application The Application
   * @param instance An identifier for a given instance
   * @return The endpoint specific to this applicationk, which can be used for
   *         message sending/receiving.
   */
  public Endpoint registerApplication(Application application, String instance);

  /**
   * Returns the Id of this node
   * 
   * @return This node's Id
   */
  public Id getId();

  /**
   * Returns a factory for Ids specific to this node's protocol.
   * 
   * @return A factory for creating Ids.
   */
  public IdFactory getIdFactory();

  /**
   * Returns a handle to the local node. This node handle is serializable, and
   * can therefore be sent to other nodes in the network and still be valid.
   * 
   * @return A NodeHandle referring to the local node.
   */
  public NodeHandle getLocalNodeHandle();

}

