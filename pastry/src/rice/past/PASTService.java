package rice.past;

import java.io.Serializable;

import rice.*;
import rice.pastry.NodeId;
import rice.pastry.security.Credentials;


/**
 * @(#) PASTService.java
 * 
 * This interface is exported by PAST for any applications or components
 * which need to store replicated copies of documents on the Pastry
 * network.
 * 
 * The PAST service is event-driven, so all methods are asynchronous
 * and receive their results using the command pattern.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface PASTService {
  
  /**
   * Inserts an object with the given ID into distributed storage.
   * Asynchronously returns a boolean as the result to the provided
   * Continuation, indicating whether the insert was successful.
   * 
   * @param id Pastry key identifying the object to be stored
   * @param obj Persistable object to be stored
   * @param authorCred Author's credentials
   * @param command Command to be performed when the result is received
   */
  public void insert(NodeId id, Serializable obj, Credentials authorCred,
                     Continuation command);
  
  /**
   * Retrieves the object and all associated updates with the given ID.
   * Asynchronously returns a StorageObject as the result to the provided
   * Continuation.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   */
  public void lookup(NodeId id, Continuation command);
  
  /**
   * Determines whether an object is currently stored at the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * Continuation, indicating whether the object exists.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   */
  public void exists(NodeId id, Continuation command);
  
  /**
   * Reclaims the storage used by the object with the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * Continuation, indicating whether the delete was successful.
   * 
   * @param id Pastry key of original object
   * @param authorCred Author's credentials
   */
  public void delete(NodeId id, Credentials authorCred,
                     Continuation command);
  
}