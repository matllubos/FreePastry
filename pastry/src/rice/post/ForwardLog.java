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
package rice.post;

import java.io.*;
import java.security.*;
import java.security.cert.*;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.post.log.*;
import rice.post.storage.*;
import rice.post.security.*;

/**
 * This class represents the log of forwarding addresses for a given user.
 * Other nodes which are sending notifications to this node should also send
 * notifications to all of the addresses listed in this log.
 * 
 * @version $Id$
 */
@SuppressWarnings("unchecked")
public class ForwardLog extends Log {
  
  /**
   * Serialver for backwards compatibility
   */
  public static final long serialVersionUID = 5516854362333868152L;
  
  /**
   * The universal name for this log
   */
  public static final String FORWARD_NAME = "NotificationForward";
  
  /**
   * The list of addresses to forward to
   */
  protected String[] addresses;
  
  /**
   * Constructor for ForwardLog.  Package protected: only Post can create
   * a ForwardLog.
   *
   * @param user The user whom this PostLog is for
   * @param key The user's public key.
   * @param cert This user's certification
   * @param post The local Post service
   * @param command The command to call once done
   */
  public ForwardLog(final PostLog log, String[] addresses, Id location, Post post, Continuation command) {
    super(FORWARD_NAME, location, post);
    
    this.addresses = addresses;
    
    sync(new StandardContinuation(command) {
      public void receiveResult(Object o) {
        log.addChildLog(ForwardLog.this, parent);
      }
    });
  }
  
  /**
   * Returns the list of forward addresses
   *
   * @return The forward addresses
   */
  public String[] getAddresses() {
    return addresses;
  }
  
  /**
   * Updates the list of addresses
   *
   * @param addresses The new list of addresses
   */
  public void setAddresses(String[] addresses, Continuation command) {
    this.addresses = addresses;
    sync(command);
  }
  
  /**
   * Returns whether or not this log should be cached
   *
   * @return Whether or not this log should be cached
   */
  public boolean cache() {
    return false;
  }
  
  public String toString() {
    return "ForwardLog[" + addresses + "]";
  }
}

