package rice.post.email;

import java.security.*;

import rice.pastry.*;
import rice.post.*;

/**
 * Represents the attachment to an email.
 */

public class EmailAttachment implements PostData {

  /**
   * Constructor. Takes in a byte[] representing the data of the
   * attachment
   *
   * @param data The byte[] representation
   */
  public EmailAttachment(byte[] data) {
  }

  /**
   * Returns the data of this attachment
   *
   * @param The data stored in this attachment
   */
  public byte[] getData() {
    return null;
  }

}
