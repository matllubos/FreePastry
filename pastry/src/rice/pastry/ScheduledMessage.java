
package rice.pastry;

import rice.environment.logging.Logger;
import rice.pastry.messaging.Message;
import rice.selector.TimerTask;

/**
 * A class that represents scheduled message events
 *
 * @version $Id$
 *
 * @author Peter Druschel
 */
public class ScheduledMessage extends TimerTask {
  private PastryNode localNode;
  private Message msg;

  /**
   * Constructor
   *
   * @param the message
   */
  public ScheduledMessage(PastryNode pn, Message msg) {
      localNode = pn;
      this.msg = msg;
  }

  /**
   * Returns the message
   *
   * @return the message
   */
  public Message getMessage() {
      return msg;
  }

	public PastryNode getLocalNode() {
		return localNode;
	}

  /**
   * deliver the message
   */
  public void run() {
    try {
    	// timing with cancellation
    	Message m = msg;
    	if (m != null)
        localNode.receiveMessage(msg);
    } catch (Exception e) {
      Logger logger = localNode.getEnvironment().getLogManager().getLogger(getClass(), null);
      logger.log(Logger.WARNING, "Delivering " + this + " caused exception " + e);
      logger.logException(Logger.WARNING, e);
    }
  }

	public String toString() {
		return "SchedMsg for "+msg;	
	}
  /* (non-Javadoc)
   * @see rice.p2p.commonapi.CancellableTask#cancel()
   */
  public boolean cancel() {
		// memory management
		msg = null;
		localNode = null;
    return super.cancel();
  }

}

