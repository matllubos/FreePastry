
package rice.p2p.commonapi;

/**
 * @(#) Application.java
 *
 * Interface which an application must implement in order to run on top of
 * the Node interface.  
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface Application {

  /**
   * This method is invoked on applications when the underlying node
   * is about to forward the given message with the provided target to
   * the specified next hop.  Applications can change the contents of
   * the message, specify a different nextHop (through re-routing), or
   * completely terminate the message.
   *
   * @param message The message being sent, containing an internal message
   * along with a destination key and nodeHandle next hop.
   *
   * @return Whether or not to forward the message further
   */
  public boolean forward(RouteMessage message);

  /**
   * This method is called on the application at the destination node
   * for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message);

  /**
   * This method is invoked to inform the application that the given node
   * has either joined or left the neighbor set of the local node, as the set
   * would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined);
  
  /**
   * This method is invoked to inform the application that the given message
   * was not properly delivered
   *
   * @param msg The undelivered message.
   * @param reason The reason it wasn't delivered.
   */
//  public void messageNotDelivered(Message msg, String reason);
  
}




