
package rice.p2p.splitstream;

/**
 * This interface allows an application running on top of SplitStream to be notified of events.
 *
 * @version $Id$
 * @author Ansley Post
 * @author Alan Mislove
 */
public interface SplitStreamClient {

  /**
   * This is a call back into the application to notify it that one of the stripes was unable to to
   * find a parent, and thus unable to recieve data.
   *
   * @param s The stripe which the join failed on
   */
  public void joinFailed(Stripe s);

  /**
   * Is called when data is received on a stripe which this client has registered interest
   *
   * @param data The data that was received
   * @param s The stripe the data as received on
   */
  public void deliver(Stripe s, byte[] data);

}

