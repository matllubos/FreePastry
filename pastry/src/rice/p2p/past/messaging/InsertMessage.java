
package rice.p2p.past.messaging;

import rice.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) InsertMessage.java
 *
 * This class represents a message which is an insert request in past.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public class InsertMessage extends ContinuationMessage {

  // serailver for bward compatibility
  static final long serialVersionUID = -7027957470028259605L;
  
  // the data to insert
  protected PastContent content;
  
  /**
   * Constructor which takes a unique integer Id, as well as the
   * data to be stored
   *
   * @param uid The unique id
   * @param content The content to be inserted
   * @param source The source address
   * @param dest The destination address
   */
  public InsertMessage(int uid, PastContent content, NodeHandle source, Id dest) {
    super(uid, source, dest);

    this.content = content;
  }

  /**
   * Method which returns the content
   *
   * @return The contained content
   */
  public PastContent getContent() {
    return content;
  }
  
  /**
   * Method which builds a response for this message, using the provided
   * object as a result.
   *
   * @param o The object argument
   */
  public void receiveResult(Object o) {
    super.receiveResult(o);
    content = null;
  }
  
  /**
   * Method which builds a response for this message, using the provided
   * exception, which was thrown
   *
   * @param e The exception argument
   */
  public void receiveException(Exception e) {
    super.receiveException(e);
    content = null;
  }

  /**
    * Returns a string representation of this message
   *
   * @return A string representing this message
   */
  public String toString() {
    return "[InsertMessage for " + content + "]";
  }
}

