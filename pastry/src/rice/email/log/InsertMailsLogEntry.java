package rice.email.log;

import java.util.*;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an list of emails in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 * @author Joe Montgomery
 */
public class InsertMailsLogEntry extends EmailLogEntry {
  
  StoredEmail[] _storedEmails;
  
  /**
  * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public InsertMailsLogEntry(StoredEmail[] emails) {
    _storedEmails = emails;
  }
  
  /**
    * Returns the emails which this log entry references
   *
   * @return The emails inserted
   */
  public StoredEmail[] getStoredEmails() {
    return _storedEmails;
  }
  
  /**
   * ToString for this entry
   *
   * @return A String
   */
  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("InsertMailsLogEntry[");
    
    for (int i=0; i<_storedEmails.length; i++) 
      buffer.append(_storedEmails[i].getUID() + ", ");
    
    return buffer.toString() + "]";
  }
  
  /**
   * Equals method
   *
   * @param o The object to compare to
   * @return Whether or not we are equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof InsertMailsLogEntry))
      return false;
    
    return Arrays.equals(((InsertMailsLogEntry) o)._storedEmails, _storedEmails);
  }
}






