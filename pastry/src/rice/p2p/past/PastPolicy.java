
package rice.p2p.past;

import rice.*;
import rice.Continuation.*;
import rice.p2p.commonapi.*;
import rice.p2p.past.messaging.*;
import rice.persistence.*;

/**
 * @(#) PastPolicy.java This interface represents a policy for Past, which is asked whenever
 * the local node is told to replicate or validate an item.  This allows for applications to
 * control replication and object validate behavior, permitting behavior specific for
 * mutable or self-authenticating data.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public interface PastPolicy {
  
  /**
   * This method is called when Past is told to fetch a key.  This method allows the application
   * to specify how a replica is fetched and authenticated.  The client should fetch the object
   * (possibly using the past instance provided) and then return the object to the provided
   * continuation.  The client *MUST* call the continuation at some point in the future, even if
   * the request is lost.
   *
   * @param id The id to fetch
   * @param past The local past instance 
   * @param backup The backup cache, where the object *might* be located
   * @param command The command to call with the replica to store
   */
  public void fetch(Id id, NodeHandle hint, Cache backup, Past past, Continuation command);
  
  /**
   * This method is call before an insert() is processed on the local node.  This allows applications
   * to make a decision on whether or not to store the replica.  Unless you know what you are doing,
   * don't return anything but 'true' here.
   *
   * @param content The content about to be stored
   * @return Whether the insert should be allowed
   */
  public boolean allowInsert(PastContent content);
  
  /**
   * The default policy for Past, which fetches any available copy of a replicated object and
   * always allows inserts locally.
   *
   * @author Alan Mislove
   */
  public static class DefaultPastPolicy implements PastPolicy {
    
    /**
     * This method fetches the object via a lookup() call.
     *
     * @param id The id to fetch
     * @param hint A hint as to where the key might be
     * @param backup The backup cache, where the object *might* be located
     * @param past The local past instance 
     * @param command The command to call with the replica to store
     */
    public void fetch(final Id id, final NodeHandle hint, final Cache backup, final Past past, Continuation command) {
      if ((backup != null) && backup.exists(id)) {
        backup.getObject(id, command);
      } else {
        past.lookup(id, false, new StandardContinuation(command) {
          public void receiveResult(Object o) {
            if (o != null) 
              parent.receiveResult(o);
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
}

