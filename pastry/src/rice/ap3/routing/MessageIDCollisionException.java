package rice.ap3.routing;

/**
 * @(#) MessageIDCollisionException.java
 *
 * Occurs if a message id collision is detected.
 * 
 * @version $Id$
 * @author Gaurav Oberoi
 */
public class MessageIDCollisionException extends Exception {

  /**
   * Constructor
   */
  MessageIDCollisionException(String message) {
    super(message);
  }
}
