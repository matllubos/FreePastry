
package rice.post.storage;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;
import rice.p2p.past.messaging.*;
import rice.post.*;
import rice.persistence.*;

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
   * @param hint A hint where the key may be
   * @param past The local past instance 
   * @param command The command to call with the replica to store
   */
  public void fetch(final Id id, final NodeHandle hint, final Cache backup, final Past past, Continuation command) {
    past.lookupHandles(id, past.getReplicationFactor()+1, new StandardContinuation(command) {
      public void receiveResult(Object o) {
        PastContentHandle[] handles = (PastContentHandle[]) o;
      
        if ((handles == null) || (handles.length == 0)) {
          System.out.println("ERROR: Unable to fetch replica of id " + id + " - handles:" + handles);
          parent.receiveException(new PostException("Unable to fetch data - returned unexpected null."));
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
        
        if (handle != null) 
          past.fetch(handle, parent);
        else 
          past.lookupHandle(id, hint, new StandardContinuation(parent) {
            public void receiveResult(Object o) {
              if (o != null) 
                past.fetch((PastContentHandle) o, parent);
              else
                parent.receiveResult(null);
            }
          });
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

