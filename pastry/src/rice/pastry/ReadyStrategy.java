/*
 * Created on Jun 13, 2006
 */
package rice.pastry;

public interface ReadyStrategy {
  public void setReady(boolean r);
  public boolean isReady();
  /**
   * Called when it is time to take over as the renderstrategy.
   *
   */
  public void start();
}
