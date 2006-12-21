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
package rice.email;

import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.post.log.*;
import rice.post.storage.*;

/**
 * Serves as a summary of the log chain up to the current point.  Lets
 * the email reader display the current emails without having to read
 * through the entire chain.
 * @author Joe Montgomery
 */
public class SnapShot implements PostData {
  
  // serialver
  private static final long serialVersionUID = 9034592939830134320L;
  
  // stores the emails of the current folder
  private StoredEmail[] emails;
  
  // the most recent log entry, as of the snapshot
  private LogEntry entry;
  
  /**
   * Constructor for SnapShot.  For the given email, creates an
   * entry which can be used in a log chain.  The next field is the
   * next LogNode in the chain.
   *
   * @param email the email to store
   * @param top The top of the current log
   */
  public SnapShot(StoredEmail[] emails, LogEntry entry) {
    this.emails = emails;
    this.entry = entry;
  }
  
  /**
    * Returns all of the emails that the SnapShot contains.
   *
   * @return the valid emails at the point of the SnapShot
   */
  public StoredEmail[] getStoredEmails() {
    return emails;
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
    if (! (o instanceof SnapShot))
      return false;
    
    return Arrays.equals(((SnapShot) o).emails, emails);
  }
  
  /**
   * This method dynamically builds an appropriate SignedReference
   * for this type of PostData given a location.
   *
   * @param location The location of the data
   * @return A pointer to the data
   */
  public SignedReference buildSignedReference(Id location) {
    throw new UnsupportedOperationException("Snapshots must be stored content-hash!");
  }
  
  /**
    * This method dynamically builds an appropriate ContentHashReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public ContentHashReference buildContentHashReference(Id[] location, byte[][] key) {
    return new ContentHashReference(location, key);
  }
  
  /**
    * This method dynamically builds an appropriate SecureReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public SecureReference buildSecureReference(Id location, byte[] key) {
    throw new UnsupportedOperationException("Snapshots must be stored content-hash!");
  }
  
}
