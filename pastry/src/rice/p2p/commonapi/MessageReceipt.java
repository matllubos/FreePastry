package rice.p2p.commonapi;

import org.mpisws.p2p.transport.MessageRequestHandle;

/**
 * Returned by a call to endpoint.route().  
 * 
 * Can be used to cancel a message.
 * 
 * @author Jeff Hoye
 *
 */
public interface MessageReceipt extends Cancellable {
  public Message getMessage();
  public Id getId();
  public NodeHandle getHint();
}
