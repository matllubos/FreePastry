/*
 * Created on Feb 19, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire;

import java.nio.channels.SelectionKey;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class StaleSKH implements SelectionKeyHandler {

  /**
   * 
   */
  public StaleSKH() {
    super();
    // TODO Auto-generated constructor stub
  }

  /* (non-Javadoc)
   * @see rice.pastry.wire.SelectionKeyHandler#accept(java.nio.channels.SelectionKey)
   */
  public void accept(SelectionKey key) {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see rice.pastry.wire.SelectionKeyHandler#connect(java.nio.channels.SelectionKey)
   */
  public void connect(SelectionKey key) {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see rice.pastry.wire.SelectionKeyHandler#read(java.nio.channels.SelectionKey)
   */
  public void read(SelectionKey key) {
    // TODO Auto-generated method stub
    key.cancel();
  }

  /* (non-Javadoc)
   * @see rice.pastry.wire.SelectionKeyHandler#write(java.nio.channels.SelectionKey)
   */
  public void write(SelectionKey key) {
    // TODO Auto-generated method stub

  }

}
