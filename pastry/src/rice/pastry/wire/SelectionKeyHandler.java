
package rice.pastry.wire;

import java.nio.channels.*;

/**
 * This interface is designed to be a callback mechanism from the
 * SelectorManager. Once the manager has determines that something has happened,
 * it informs the appropriate SelectionKeyHandler via this interface. The
 * SelectionKeyHandler which is interested in being notified of events relating
 * to the SelectionKey should attach itself to the SelectionKey via the attach()
 * method. The SelectorManager will then call that SelectionKeyHandler's
 * methods.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public interface SelectionKeyHandler {

  /**
   * Method which is called when the key becomes acceptable.
   *
   * @param key The key which is acceptable.
   */
  public void accept(SelectionKey key);

  /**
   * Method which is called when the key becomes connectable.
   *
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key);

  /**
   * Method which is called when the key becomes readable.
   *
   * @param key The key which is readable.
   */
  public void read(SelectionKey key);

  /**
   * Method which is called when the key becomes writable.
   *
   * @param key The key which is writable.
   */
  public void write(SelectionKey key);

}
