package rice.pastry.wire;

import java.nio.channels.SelectionKey;

/**
 * The purpose of this class is to be able to have a key
 * in the SelectorManager without an attachment while it 
 * is pending being attached.  If a StaleSKH is attached,
 * then the key will be cancelled next time it is read.
 * 
 * @author Jeff Hoye
 */
public class StaleSKH implements SelectionKeyHandler {

  /**
   * Constructor for StaleSKH.
   */
  public StaleSKH() {
    super();
  }

  public void accept(SelectionKey key) {
  }

  public void connect(SelectionKey key) {
  }

  public void read(SelectionKey key) {
    key.cancel();
  }

  public void write(SelectionKey key) {
  }

}
