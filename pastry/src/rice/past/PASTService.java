package rice.past;

import rice.storage.StorageManager;

/**
 * @(#) PASTService.java
 *
 * This interface is exported by PAST for any applications or components
 * which need to store replicated copies of documents on the Pastry
 * network.  It provides the same interface as the local StorageManager.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface PASTService {
  /**
   * Inserts an object with the given ID into distributed storage.
   * Asynchronously returns a Boolean as the result to the provided
   * ReceiveResultCommand, indicating whether the insert was successful.
   * 
   * @param id Pastry key identifying the object to be stored
   * @param obj Persistable object to be stored
   * @param authorCred Author's credentials
   * @param command Command to be performed when the result is received
   * @return unique identifier allowing application to recognize the result
   * of this method call
   */
  public ResultIdentifier insert(NodeId id, Serializable obj, 
                                 Credentials authorCred,
                                 ReceiveResultCommand command);
  
  /**
   * Stores an update to the object with the given ID.
   * Asynchronously returns a Boolean as the result to the provided
   * ReceiveResultCommand, indicating whether the insert was successful.
   * 
   * @param id Pastry key of original object to be updated
   * @param update Persistable update to the original object
   * @param authorCred Update Author's credentials
   * @param command Command to be performed when the result is received
   * @return unique identifier allowing application to recognize the result
   * of this method call
   */
  public ResultIdentifier update(NodeId id, Serializable update, 
                                 Credentials authorCred, 
                                 ReceiveResultCommand command);
  
  /**
   * Retrieves the object and all associated updates with the given ID.
   * Asynchronously returns a StorageObject as the result to the provided
   * ReceiveResultCommand.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   * @return unique identifier allowing application to recognize the result
   * of this method call
   */
  public ResultIdentifier lookup(NodeId id, ReceiveResultCommand command);
  
  /**
   * Determines whether an object is currently stored at the given ID.
   * Asynchronously returns a Boolean as the result to the provided
   * ReceiveResultCommand, indicating whether the object exists.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   * @return unique identifier allowing application to recognize the result
   * of this method call
   */
  public ResultIdentifier exists(NodeId id, ReceiveResultCommand command);
  
  /**
   * Reclaims the storage used by the object with the given ID.
   * Asynchronously returns a Boolean as the result to the provided
   * ReceiveResultCommand, indicating whether the delete was successful.
   * 
   * @param id Pastry key of original object
   * @param authorCred Author's credentials
   * @return unique identifier allowing application to recognize the result
   * of this method call
   */
  public ResultIdentifier delete(NodeId id, Credentials authorCred,
                                 ReceiveResultCommand command);
  
}