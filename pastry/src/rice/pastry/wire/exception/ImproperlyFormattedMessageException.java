
package rice.pastry.wire.exception;

import java.io.*;

/**
 * Class which represents an exception occuring during the parsing of a Pastry
 * message sent through the Socket protocol.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class ImproperlyFormattedMessageException extends IOException {

  /**
   * Constructs an ImproperlyFormattedMessageException with a given message
   *
   * @param message The message of this exception
   */
  public ImproperlyFormattedMessageException(String message) {
    super(message);
  }
}

