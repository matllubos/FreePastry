
package rice.pastry.wire.exception;

import java.io.*;

/**
 * Class which represents an exception occuring during the serialization of a
 * Pastry message.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class SerializationException extends IOException {

  /**
   * Constructs an SerializationException with a given message
   *
   * @param message The message of this exception
   */
  public SerializationException(String message) {
    super(message);
  }
}

