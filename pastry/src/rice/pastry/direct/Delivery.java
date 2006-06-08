/*
 * Created on Jan 30, 2006
 */
package rice.pastry.direct;

public interface Delivery {
  /**
   * What to do when time to deliver.
   *
   */
  public void deliver();
  /**
   * Preserve order.
   * @return
   */
  public int getSeq();
}
