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

import java.util.Observable;

import rice.*;
import rice.post.messaging.*;

/**
 * This class is a superclass for clients running on top
 * of the Post object.
 * 
 * @version $Id$
 */
@SuppressWarnings("unchecked")
public abstract class PostClient extends Observable {

  /**
   * Builds a PostClient.
   */
  public PostClient() {
  }
  
  /**
   * This method is how the Post object informs the clients
   * that there is an incoming notification. 
   *
   * @param nm The incoming notification.
   * @param command THe command to return whether or not the notification should be accepted (Boolean true or false)
   */
  public abstract void notificationReceived(NotificationMessage nm, Continuation command);
  
  /**
   * This method is periodically invoked by Post in order to get a list of
   * all handles under which the application has live objects.  This used to
   * implement the garbage collection service, thus, the application must
   * ensure that all data which it is still interested in is returned.
   *
   * The applications should return a ContentHashReference[] containing all of 
   * the handles The application is still interested in to the provided continatuion.
   */
  public abstract void getContentHashReferences(Continuation command);
  
  /**
   * This method is periodically invoked by Post in order to get a list of
   * all mutable data which the application is interested in.
   *
   * The applications should return a Log[] containing all of 
   * the data The application is still interested in to the provided continatuion.
   */
  public abstract void getLogs(Continuation command);

  /**
   * Returns the address of this PostClient.  This method is
   * automatically provided in order to allow address to happen
   * transparently.
   *
   * @return The unique address of this PostClient.
   */
  public final PostClientAddress getAddress() {
    return PostClientAddress.getAddress(this);
  }
  
}
