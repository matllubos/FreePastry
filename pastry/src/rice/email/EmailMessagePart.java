package rice.email;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents a part of an email with headers
 *
 * @author Alan Mislove
 */
public class EmailMessagePart extends EmailHeadersPart {

  /**
   * Constructor. Takes in a emailData representing the headers and
   * a EmailContentPart representing the content
   *
   * @param headers The headers of this part
   * @param content The content of this part
   */
  public EmailMessagePart(EmailData headers, EmailContentPart content) {
    super(headers, content);
  }

}
