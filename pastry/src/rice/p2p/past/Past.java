
package rice.p2p.past;

import rice.*;
import rice.p2p.commonapi.*;

/**
 * @(#) Past.java
 * 
 * This interface is exported by all instances of Past.  An instance
 * of Past provides a distributed hash table (DHT) service.  Each
 * instance stores tuples consisting of a key and an object of a
 * particular type, which must implement the interface PastContent.
 *
 * Past is event-driven, so all methods are asynchronous
 * and receive their results using the command pattern.
 *
 * @version $Id$
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public interface Past {

  /**
   * Inserts an object with the given ID into this instance of Past.
   * Asynchronously returns a PastException to command, if the
   * operation was unsuccessful.  If the operation was successful, a
   * Boolean[] is returned representing the responses from each of
   * the replicas which inserted the object.
   * 
   * @param obj the object to be inserted
   * @param command Command to be performed when the result is received
   */
  public void insert(PastContent obj, Continuation command);
 
  /**
   * Retrieves the object stored in this instance of Past with the
   * given ID.  Asynchronously returns a PastContent object as the
   * result to the provided Continuation, or a PastException. This
   * method is provided for convenience; its effect is identical to a
   * lookupHandles() and a subsequent fetch() to the handle that is
   * nearest in the network.
   * 
   * The client must authenticate the object. In case of failure, an
   * alternate replica of the object can be obtained via
   * lookupHandles() and fetch().
   * 
   * This method is not safe if the object is immutable and storage
   * nodes are not trusted. In this case, clients should used the
   * lookUpHandles method to obtains the handles of all primary
   * replicas and determine which replica is fresh in an
   * application-specific manner.
   *
   * By default, this method attempts to cache the result locally for
   * future use.  Applications which do not desire this behavior should
   * use the lookup(id, boolean, command) method.
   *
   * @param id the key to be queried
   * @param command Command to be performed when the result is received
   */
  public void lookup(Id id, Continuation command);
  
  /**
   * Retrieves the object stored in this instance of Past with the
   * given ID.  Asynchronously returns a PastContent object as the
   * result to the provided Continuation, or a PastException. This
   * method is provided for convenience; its effect is identical to a
   * lookupHandles() and a subsequent fetch() to the handle that is
   * nearest in the network.
   * 
   * The client must authenticate the object. In case of failure, an
   * alternate replica of the object can be obtained via
   * lookupHandles() and fetch().
   * 
   * This method is not safe if the object is immutable and storage
   * nodes are not trusted. In this case, clients should used the
   * lookUpHandles method to obtains the handles of all primary
   * replicas and determine which replica is fresh in an
   * application-specific manner.
   *
   * This method also allows applications to specify if the result should
   * be cached locally.
   *
   * @param id the key to be queried
   * @param cache Whether or not the result should be cached
   * @param command Command to be performed when the result is received
   */
  public void lookup(Id id, boolean cache, Continuation command);

  /**
   * Retrieves the handles of up to max replicas of the object stored
   * in this instance of Past with the given ID.  Asynchronously
   * returns an array of PastContentHandles as the result to the
   * provided Continuation, or a PastException.  
   * 
   * Each replica handle is obtained from a different primary storage
   * root for the the given key. If max exceeds the replication factor
   * r of this Past instance, only r replicas are returned.
   *
   * This method will return a PastContentHandle[] array containing all
   * of the handles.
   *
   * @param id the key to be queried
   * @param max the maximal number of replicas requested
   * @param command Command to be performed when the result is received 
   */
  public void lookupHandles(Id id, int max, Continuation command);
  
  /**
   * Retrieves the handle for the given object stored on the requested 
   * node.  Asynchronously returns a PostContentHandle (or null) to
   * the provided continuation.
   *
   * @param id the key to be queried
   * @param handle The node on which the handle is requested
   * @param command Command to be performed when the result is received 
   */
  public void lookupHandle(Id id, NodeHandle handle, Continuation command);
  
  /**
   * Retrieves the object associated with a given content handle.
   * Asynchronously returns a PastContent object as the result to the
   * provided Continuation, or a PastException.
   * 
   * The client must authenticate the object. In case of failure, an
   * alternate replica can be obtained using a different handle obtained via
   * lookupHandles().
   * 
   * @param handle the key to be queried
   * @param command Command to be performed when the result is received 
   */
  public void fetch(PastContentHandle handle, Continuation command);
  
  /**
   * get the nodeHandle of the local Past node
   *
   * @return the nodehandle
   */
  public NodeHandle getLocalNodeHandle();

  /**
   * Returns the number of replicas used in this Past
   *
   * @return the number of replicas for each object
   */
  public int getReplicationFactor();

}

