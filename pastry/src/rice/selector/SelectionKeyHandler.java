
package rice.selector;

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
 * @version $Id$
 * @author Alan Mislove
 */
public class SelectionKeyHandler {
  
  /**
   * Method which should change the interestOps of the handler's key. This
   * method should *ONLY* be called by the selection thread in the context of a
   * select().
   *
   * @param key The key in question
   */
  public void modifyKey(SelectionKey key) {
    throw new UnsupportedOperationException("modifyKey() cannot be called on " + getClass().getName() + "!");
  }
  
  /**
   * Method which is called when the key becomes acceptable.
   *
   * @param key The key which is acceptable.
   */
  public void accept(SelectionKey key) {
    throw new UnsupportedOperationException("accept() cannot be called on " + getClass().getName() + "!");
  }
  
  /**
   * Method which is called when the key becomes connectable.
   *
   * @param key The key which is connectable.
   */
  public void connect(SelectionKey key) {
    throw new UnsupportedOperationException("connect() cannot be called on " + getClass().getName() + "!");
  }
  
  /**
   * Method which is called when the key becomes readable.
   *
   * @param key The key which is readable.
   */
  public void read(SelectionKey key) {
    throw new UnsupportedOperationException("read() cannot be called on " + getClass().getName() + "!");
  }
  
  /**
   * Method which is called when the key becomes writable.
   *
   * @param key The key which is writable.
   */
  public void write(SelectionKey key) {
    throw new UnsupportedOperationException("write() cannot be called on " + getClass().getName() + "!");
  }
  
}
