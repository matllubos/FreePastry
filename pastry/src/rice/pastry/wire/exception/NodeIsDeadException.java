/*
 *  Created on Dec 23, 2003
 *
 *  To change the template for this generated file go to
 *  Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package rice.pastry.wire.exception;

/**
 * @version $Id$
 * @author jeffh To change the template for this generated type comment go to
 *      Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class NodeIsDeadException extends RuntimeException {

  /**
   * DESCRIBE THE FIELD
   */
  public Exception originalException;

  /**
   * Constructor for NodeIsDeadException.
   *
   * @param e DESCRIBE THE PARAMETER
   */
  public NodeIsDeadException(Exception e) {
    originalException = e;
  }

}
