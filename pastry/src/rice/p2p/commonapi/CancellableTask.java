/*
 * Created on Jul 27, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.p2p.commonapi;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface CancellableTask {
  public void run();
  public boolean cancel();
  public long scheduledExecutionTime();
}
