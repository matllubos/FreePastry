
package rice.p2p.commonapi;

/**
 * This class represents a task which can be cancelled by the
 * caller.
 *
 * @author Alan Mislove
 * @author Jeff Hoye
 */
public interface CancellableTask {
  
  public void run();
  
  /**
   * 
   * @return true if it was cancelled, false if it was already complete, or cancelled.
   */
  public boolean cancel();
  
  public long scheduledExecutionTime();
  
}
