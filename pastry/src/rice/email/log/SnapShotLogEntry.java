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
 * Serves as a summary of the log chain up to the current point.  Lets
 * the email reader display the current emails without having to read
 * through the entire chain.
 * @author Joe Montgomery
 */
public class SnapShotLogEntry extends EmailLogEntry {
  
  // stores the emails of the current folder
  private StoredEmail[] _emails;
  
  // the location of the most recent log entry included in this snapshot
  private LogEntry entry;
    
  /**
   * Constructor for SnapShot.  For the given email, creates an
   * entry which can be used in a log chain.  The next field is the
   * next LogNode in the chain.
   *
   * @param email the email to store
   * @param top The top of the current log
   */
  public SnapShotLogEntry(StoredEmail[] emails, LogEntry top) {
    _emails = emails;
    entry = top;
  }

  /**
   * Returns all of the emails that the SnapShot contains.
   *
   * @return the valid emails at the point of the SnapShot
   */
  public StoredEmail[] getStoredEmails() {
    return _emails;
  }
  
  /**
   * Returns the most recent entry in the log, at the time of the snapshot
   *
   * @return The most recent log entry reference
   */
  public LogEntry getTopEntry() {
    return entry;
  }
  
  /**
   * Equals method
   *
   * @param o The object to compare to
   * @return Whether or not we are equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof SnapShotLogEntry))
      return false;
    
    return Arrays.equals(((SnapShotLogEntry) o)._emails, _emails);
  }

  public long getInternalDate() {
    return ((EmailLogEntry)entry).getInternalDate();
  }
  
  // not sure if this is the right approach for snapshots
  public int getMaxUID() {
    int n = 0;
    for (int i=0; i<_emails.length; i++) {
      int d = _emails[i].getUID();
      if (d > n)
        n = d;
    }
    return n;
  }

}
