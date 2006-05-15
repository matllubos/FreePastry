
package rice.p2p.commonapi;

import java.io.*;

import rice.p2p.commonapi.rawserialization.*;

/**
 * @(#) RouteMessage.java
 *
 * This interface is a container which represents a message, as it is
 * about to be forwarded to another node.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Peter Druschel
 */
public interface RouteMessage extends Serializable {

  /**
   * Returns the destination Id for this message
   *
   * @return The destination Id
   */
  public Id getDestinationId();

  /**
   * Returns the next hop handle for this message
   *
   * @return The next hop
   */
  public NodeHandle getNextHopHandle();

  /**
   * Returns the enclosed message inside of this message
   *
   * @return The enclosed message
   * @deprecated use getMesage(MessageDeserializer)
   */
  public Message getMessage();
  
  public Message getMessage(MessageDeserializer md) throws IOException;

  /**
   * Sets the destination Id for this message
   *
   * @param id The destination Id
   */
  public void setDestinationId(Id id);

  /**
   * Sets the next hop handle for this message
   *
   * @param nextHop The next hop for this handle
   */
  public void setNextHopHandle(NodeHandle nextHop);

  /**
   * Sets the internal message for this message
   *
   * @param message The internal message
   */
  public void setMessage(Message message);
  
  /**
   * Sets the internal message for this message
   *
   * Does the same as setMessage(Message) but with better
   * performance, because it doesn't have to introspect 
   * if the message is a RawMessage
   *
   * @param message The internal message
   */
  public void setMessage(RawMessage message);
  
}


