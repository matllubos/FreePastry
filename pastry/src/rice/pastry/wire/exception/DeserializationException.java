
package rice.pastry.wire.exception;

import java.io.*;

/**
 * Class which represents an exception occuring during the deserialization of a
 * Pastry message.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class DeserializationException extends IOException {

  /**
   * Constructs an DeserializationException with a given message
   *
   * @param message The message of this exception
   */
  public DeserializationException(String message) {
    super(message);
  }
}

