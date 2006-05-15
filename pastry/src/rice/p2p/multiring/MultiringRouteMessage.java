
package rice.p2p.multiring;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.rawserialization.*;

import java.io.*;

/**
 * @(#) MultiringRouteMessage.java
 *
 * This wraps a real RouteMessage.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public class MultiringRouteMessage implements RouteMessage {
  
  /**
   * The internal message
   */
  protected RouteMessage message;
  
  /**
   * The messages's ringId
   */
  protected Id ringId;
  
  /**
   * Constructor 
   */
  protected MultiringRouteMessage(Id ringId, RouteMessage message) {
    this.ringId = ringId;
    this.message = message;
    
    if ((ringId instanceof RingId) || (message instanceof MultiringRouteMessage))
      throw new IllegalArgumentException("Illegal creation of MRRouteMessage: " + ringId.getClass() + ", " + message.getClass());
  }
  
  /**
   * Returns the internal route message
   *
   * @return The internal route message
   */
  protected RouteMessage getRouteMessage() {
    return message;
  }
  
  /**
   * Returns the destination Id for this message
   *
   * @return The destination Id
   */
  public Id getDestinationId() {
    return RingId.build(ringId, message.getDestinationId());
  }
  
  /**
   * Returns the next hop handle for this message
   *
   * @return The next hop
   */
  public NodeHandle getNextHopHandle() {
    return new MultiringNodeHandle(ringId, message.getNextHopHandle()); 
  }
  
  /**
    * Returns the enclosed message inside of this message
   *
   * @return The enclosed message
   */
  public Message getMessage() {
    return message.getMessage();
  }
  
  public Message getMessage(MessageDeserializer md) throws IOException {
    return message.getMessage(md);
  }
  
  /**
    * Sets the destination Id for this message
   *
   * @param id The destination Id
   */
  public void setDestinationId(Id id) {
    message.setDestinationId(((RingId) id).getId()); 
  }
  
  /**
    * Sets the next hop handle for this message
   *
   * @param nextHop The next hop for this handle
   */
  public void setNextHopHandle(NodeHandle nextHop) {
    message.setNextHopHandle(((MultiringNodeHandle) nextHop).getHandle());
  }
  
  /**
    * Sets the internal message for this message
   *
   * @param message The internal message
   */
  public void setMessage(Message message) {
    if (message instanceof RawMessage) {
      setMessage((RawMessage)message); 
    } else {
      this.message.setMessage(message); 
    }
  }
  
  /**
   * Better performance.
   */
  public void setMessage(RawMessage message) {
    this.message.setMessage(message); 
  }
  
}


