package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * An Email Deletion Event.  Signifies that the referenced Email was
 * deleted. 
 * @author Joe Montgomery
 */
public class DeleteMailEvent implements Event{
  Email _email ;
    
  /**
   * Constructor for the DeleteMailEvent.  
   *
   * @param email the email to store
   */
  public DeleteMailEvent(Email email) {
    _email = email;
  }

  /**
   * Returns the email which this event references.
   *
   * @return The email referenced
   */
  public Email getEmail() {
    return _email;
  }

  /**
   * Returns a String representation of this Event.
   * @return the String for this Event
   */
  public String toString() {
    return "Delete Email Event for: " + _email.getSubject();    
  }
}
