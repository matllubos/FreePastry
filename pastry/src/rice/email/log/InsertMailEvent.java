package rice.email.log;

import rice.email.*;
/**
 * An Email Creation Event (ECE).  Signifies that the referenced Email was
 * created. 
 * @author Joe Montgomery
 */
public class InsertMailEvent implements Event {
  Email _email;
    
  /**
   * Constructor for the Event.  
   *
   * @param email the email to that was inserted
   */
  public InsertMailEvent(Email email) {
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
    return "Insert Email Event for " + _email;    
  }
}
