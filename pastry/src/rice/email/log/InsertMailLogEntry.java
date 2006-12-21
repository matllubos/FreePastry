/*************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate 

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/
package rice.email.log;

import rice.post.log.*;
import rice.email.*;

/**
 * Stores an email in the LogEntry chain.  Holds the email and a pointer
 * to the next LogEntry.
 * @author Joe Montgomery
 */
public class InsertMailLogEntry extends EmailLogEntry {
  
  StoredEmail _storedEmail;
    
  /**
   * Constructor for InsertMailEntry.  For the given email, creates an
   * entry which can be used in a log chain. 
   *
   * @param email the email to store
   */
  public InsertMailLogEntry(StoredEmail email) {
    _storedEmail = email;
  }
  
  /**
   * Returns the email which this log entry references
   *
   * @return The email inserted
   */
  public StoredEmail getStoredEmail() {
    return _storedEmail;
  }
  
  /**
   * ToString for this entry
   *
   * @return A String
   */
  public String toString() {
    return "InsertMailLogEntry[" + _storedEmail.getUID() + "]";
  }
  
  /**
    * Equals method
   *
   * @param o The object to compare to
   * @return Whether or not we are equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof InsertMailLogEntry))
      return false;
    
    return ((InsertMailLogEntry) o)._storedEmail.equals(_storedEmail);
  }

  public long getInternalDate() {
    return _storedEmail.getInternalDate();
  }

  public int getMaxUID() {
    return _storedEmail.getUID();
  }
}






