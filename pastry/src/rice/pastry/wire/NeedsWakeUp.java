 
package rice.pastry.wire;

/**
 * This interface was designed to pull wakeUp out of 
 * all of the SelectionKeyHandlers.  This way we only call
 * wakeup on object that will need it.
 * 
 * @author Jeff Hoye 
 */
public interface NeedsWakeUp {

  /**
   * this method is routinely called on the 
   * SelectorManager's thread.
   */
  public void wakeup();
}
