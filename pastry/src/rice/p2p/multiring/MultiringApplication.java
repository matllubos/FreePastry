
package rice.p2p.multiring;

import rice.p2p.commonapi.*;

/**
 * @(#) MultiringApplication.java
 *
 * Application which wraps a real application
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class MultiringApplication implements Application {
  
  /**
   * The app which this mulitring app is wrapping
   */
  protected Application application;
  
  /**
   * The Id which represents the current ring this app is a member of
   */
  protected Id ringId;
  
  /** 
   * Constructor
   */
  protected MultiringApplication(Id ringId, Application application) {
    this.application = application;
    this.ringId = ringId;
    
    if ((ringId instanceof RingId) || (application instanceof MultiringApplication))
      throw new IllegalArgumentException("Illegal creation of MRApplication: " + ringId.getClass() + ", " + application.getClass());
  }
  
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
  public boolean forward(RouteMessage message) {
    return application.forward(new MultiringRouteMessage(ringId, message)); 
  }
  
  /**
   * This method is called on the application at the destination node
   * for the given id.
   *
   * @param id The destination id of the message
   * @param message The message being sent
   */
  public void deliver(Id id, Message message) {
    if (id != null) {
      application.deliver(RingId.build(ringId, id), message);
    } else {
      application.deliver(null, message);
    }
  }
  
  /**
    * This method is invoked to inform the application that the given node
   * has either joined or left the neighbor set of the local node, as the set
   * would be returned by the neighborSet call.
   *
   * @param handle The handle that has joined/left
   * @param joined Whether the node has joined or left
   */
  public void update(NodeHandle handle, boolean joined) {
    application.update(new MultiringNodeHandle(ringId, handle), joined);
  }
  
  public String toString() {
    return "MultiringApplication<"+application+">:"+ringId; 
  }
  
}




