package rice.email.log;

import java.util.*;

import rice.post.log.*;
import rice.email.*;

/**
 * An anti-emails node, serves to cancel out the mail node that it
 * matches up to.
 * @author Joe Montgomery
 */
public class DeleteMailsLogEntry extends EmailLogEntry {
  StoredEmail[] _storedEmails;
  
  /**
   * Constructor for DeleteMailLogEntry.  For the given array of emails, creates a node which serves
   * as a marker that the previous occurences of the emails in the chain
   * should be disregarded.
   *
   * @param email the email to store
   */
  public DeleteMailsLogEntry(StoredEmail[] emails) {
    _storedEmails = emails;
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
    buffer.append("DeleteMailsLogEntry[");
    
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
    if (! (o instanceof DeleteMailsLogEntry))
      return false;
    
    return Arrays.equals(((DeleteMailsLogEntry) o)._storedEmails, _storedEmails);
  }

  public long getInternalDate() {
    // XXX newest or oldest?
    long n = 0;
    for (int i=0; i<_storedEmails.length; i++) {
      long d = _storedEmails[i].getInternalDate();
      if (d < n)
        n = d;
    }
    return n;
  }

  public int getMaxUID() {
    int n = 0;
    for (int i=0; i<_storedEmails.length; i++) {
      int d = _storedEmails[i].getUID();
      if (d > n)
        n = d;
    }
    return n;
  }

}

