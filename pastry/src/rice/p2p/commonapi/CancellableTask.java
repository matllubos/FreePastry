
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
  
  public boolean cancel();
  
  public long scheduledExecutionTime();
  
}
