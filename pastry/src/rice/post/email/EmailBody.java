package rice.post.email;

import java.security.*;

import rice.pastry.*;
import rice.post.*;

/**
 * Represents the textual body of an email message.
 */

public class EmailBody implements PostData {

  /**
   * Constructor. Takes in the content of this email body
   * (the actual text of the email body).
   */
  public EmailBody(String content) {
  }

  /**
   * Returns where the email body is stored in PAST.
   */
  public String getContent() {
    return null;
  }

}
