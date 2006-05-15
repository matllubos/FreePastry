package rice.email;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents a part of an email with headers
 *
 * @author Alan Mislove
 */
public class EmailMessagePart extends EmailHeadersPart {
  public static final short TYPE = 2;

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
  
  public EmailMessagePart(InputBuffer buf, Endpoint endpoint) throws IOException {
    super(buf, endpoint); 
  }
  
  public short getRawType() {
    return TYPE; 
  }

}
