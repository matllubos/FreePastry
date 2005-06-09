package rice.pastry.messaging;

/**
 * An interface to an agent which can receive messages.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public interface MessageReceiver {
  /**
   * Passes a message along to entity which is this message receiver.
   * 
   * @param msg the message.
   */

  public void receiveMessage(Message msg);
}