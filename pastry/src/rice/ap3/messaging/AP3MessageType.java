package rice.ap3.messaging;

/**
 * @(#) AP3MessageType.java
 *
 * Defines the types a message can be.
 * Request: content request from a node
 * Response: generic reply to a request or callback
 * Callback: special request for a node to respond with its NodeHandle
 *
 * @version $Id$
 * @author Gaurav Oberoi
 */
public interface AP3MessageType {

  public static final int REQUEST = 1;
  public static final int RESPONSE = 2;
  public static final int CALLBACK = 3;	
}
