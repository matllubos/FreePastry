/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
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






