package rice.email;

import java.security.*;

import rice.pastry.*;
import rice.post.storage.*;

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
   * This method dynamically builds an appropriate HashReference for
   * this type of PostData given a location and key.  
   *
   * @param location the location of the data
   * @param key the key of the data
   */
  public ContentHashReference buildContentHashReference(NodeId location, Key key){
    return null;
  }

  /**
   * This method dynamically builds an appropriate SignedReference for
   * this type of PostData given a location and key.  
   *
   * @param location the location of the data
   */
  public SignedReference buildSignedReference(NodeId location){
    return null;
  }

  /**
   * Returns where the email body is stored in PAST.
   */
  public String getContent() {
    return null;
  }

}
