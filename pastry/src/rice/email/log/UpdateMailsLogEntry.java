package rice.email.log;

import java.util.*;

import rice.post.log.*;
import rice.email.*;

/**
 * Adds the flags to the log entry
 * @author 
 */
public class UpdateMailsLogEntry extends EmailLogEntry {
  
  StoredEmail[] _storedEmails;
  
  /**
  * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public UpdateMailsLogEntry(StoredEmail[] emails) {
    _storedEmails = emails; 
    
    for (int i=0; i<_storedEmails.length; i++)
      _storedEmails[i] = (StoredEmail) _storedEmails[i].clone();
  }
  
  /**
    * Returns the email which this log entry references
   *
   * @return The email inserted
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
    buffer.append("UpdateMailsLogEntry[");
    
    for (int i=0; i<_storedEmails.length; i++) 
      buffer.append(_storedEmails[i] + ", ");
    
    return buffer.toString() + "]";
  }
  
  /**
   * Equals method
   *
   * @param o The object to compare to
   * @return Whether or not we are equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof UpdateMailsLogEntry))
      return false;
    
    return Arrays.equals(((UpdateMailsLogEntry) o)._storedEmails, _storedEmails);
  }
  
}
