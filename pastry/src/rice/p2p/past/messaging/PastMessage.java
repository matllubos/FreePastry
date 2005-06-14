
package rice.p2p.past.messaging;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) PastMessage.java
 *
 * This class the abstraction of a message used internally by Past.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public abstract class PastMessage implements Message {
  
  // serialver for backward compatibility
  private static final long serialVersionUID = -7195054010358285316L;

  // the unique id for this message
  protected int id;

  // the source Id of this message
  protected NodeHandle source;

  // the destination of this message
  protected Id dest;
  
  // whether or not this message is a response
  protected boolean isResponse;

  /**
   * Constructor which takes a unique integer Id
   *
   * @param id The unique id
   * @param source The source address
   * @param dest The destination address
   */
  protected PastMessage(int id, NodeHandle source, Id dest) {
    this.id = id;
    this.source = source;
    this.dest = dest;

    isResponse = false;
  }
  
  /**
   * Method which should return the priority level of this message.  The messages
   * can range in priority from 0 (highest priority) to Integer.MAX_VALUE (lowest) -
   * when sending messages across the wire, the queue is sorted by message priority.
   * If the queue reaches its limit, the lowest priority messages are discarded.  Thus,
   * applications which are very verbose should have LOW_PRIORITY or lower, and
   * applications which are somewhat quiet are allowed to have MEDIUM_PRIORITY or
   * possibly even HIGH_PRIORITY.
   *
   * @return This message's priority
   */
  public int getPriority() {
    return MEDIUM_HIGH_PRIORITY;
  }

  /**
   * Method which returns this messages' unique id
   *
   * @return The id of this message
   */
  public int getUID() {
    return id;
  }

  /**
   * Method which returns this messages' source address
   *
   * @return The source of this message
   */
  public NodeHandle getSource() {
    return source;
  }

  /**
   * Method which returns this messages' destination address
   *
   * @return The dest of this message
   */
  public Id getDestination() {
    return dest;
  }

  /**
   * Method which builds a response for this message, using the provided
   * object as a result.  Should be overriden by subclasses, but with the
   * super.setResponse() called.
   */
  protected void setResponse() {
    isResponse = true;
  }

  /**
   * Method which returns whether or not this message is a response.
   *
   * @return whether or not this message is a response.
   */
  public boolean isResponse() {
    return isResponse;
  }

  /**
   * Method by which this message is supposed to return it's response.
   *
   * @param c The continuation to return the reponse to.
   */
  public abstract void returnResponse(Continuation c, Environment env, String instance);
  
  /**
   * Method which is designed to be overridden by subclasses if they need
   * to keep track of where they've been.
   *
   * @param handle The current local handle
   */
  public void addHop(NodeHandle handle) {
  }
}

