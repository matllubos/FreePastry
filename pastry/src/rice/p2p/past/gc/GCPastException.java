
package rice.p2p.past.gc;

import rice.p2p.past.*;

/**
 * @(#) PastException.java
 * 
 * Any exception specific to Past.
 *
 * @version $Id$
 * @author Peter Druschel 
 */
public class GCPastException extends PastException {

  /**
   * Constructor.
   *
   * @param msg The string representing the error.
   */
  public GCPastException(String msg) {
    super(msg);
  }
  
  public class ObjectNotFoundException extends PastException {
    public ObjectNotFoundException(String msg) {
      super(msg);
    }
  }
}





