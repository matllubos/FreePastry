/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate

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

package rice.post.storage;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;
import rice.post.*;

/**
 * @(#) PastPolicy.java 
 *
 * This policy represents Post's logic for fetching mutable data.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class PostPastPolicy implements PastPolicy {
  
  /**
   * This method fetches the object via a lookup() call.
   *
   * @param id The id to fetch
   * @param past The local past instance 
   * @param command The command to call with the replica to store
   */
  public void fetch(Id id, final Past past, Continuation command) {
    past.lookupHandles(id, past.getReplicationFactor(), new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PastContentHandle[] handles = (PastContentHandle[]) o;
      
        if ((handles == null) || (handles.length == 0)) {
          parent.receiveException(new PostException("Unable to fetch data - returned null."));
          return;
        }
        
        long latest = 0;
        StorageServiceDataHandle handle = null;
        
        for (int i=0; i<handles.length; i++) {
          StorageServiceDataHandle thisH = (StorageServiceDataHandle) handles[i];
          
          if ((thisH != null) && (thisH.getTimestamp() > latest)) {
            latest = thisH.getTimestamp();
            handle = thisH;
          }
        }
        
        if (handle != null) {
          past.fetch(handle, parent);
        } else {
          parent.receiveException(new PostException("Unable to fetch data - all replicas were null."));
        }
      }
    });
  }
  
  /**
   * This method always return true;
   *
   * @param content The content about to be stored
   * @return Whether the insert should be allowed
   */
  public boolean allowInsert(PastContent content) {
    return true;
  }
}

