package rice.past;

import java.io.Serializable;

import rice.pastry.NodeId;
import rice.pastry.security.Credentials;
import rice.storage.StorageObject;

// temporary
import rice.storage.StorageManager;


/**
 * @(#) PASTService.java
 *
 * This interface is exported by PAST for any applications or components
 * which need to store replicated copies of documents on the Pastry
 * network.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface PASTService extends StorageManager {
  
}


/**
 * This is the new event-driven interface for the PAST service.
 * All methods are asynchronous, and receive their results using
 * the command pattern.
 */
interface NewPASTService {
  
  /**
   * Inserts an object with the given ID into distributed storage.
   * Asynchronously returns a boolean as the result to the provided
   * InsertResultCommand, indicating whether the insert was successful.
   * 
   * @param id Pastry key identifying the object to be stored
   * @param obj Persistable object to be stored
   * @param authorCred Author's credentials
   * @param command Command to be performed when the result is received
   */
  public void insert(NodeId id, Serializable obj, Credentials authorCred,
                     InsertResultCommand command);
  
  /**
   * Stores an update to the object with the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * UpdateResultCommand, indicating whether the insert was successful.
   * 
   * @param id Pastry key of original object to be updated
   * @param update Persistable update to the original object
   * @param authorCred Update Author's credentials
   * @param command Command to be performed when the result is received
   */
  public void update(NodeId id, Serializable update, Credentials authorCred, 
                     UpdateResultCommand command);
  
  /**
   * Retrieves the object and all associated updates with the given ID.
   * Asynchronously returns a StorageObject as the result to the provided
   * LookupResultCommand.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   */
  public void lookup(NodeId id, LookupResultCommand command);
  
  /**
   * Determines whether an object is currently stored at the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * ExistsResultCommand, indicating whether the object exists.
   * 
   * @param id Pastry key of original object
   * @param command Command to be performed when the result is received
   */
  public void exists(NodeId id, ExistsResultCommand command);
  
  /**
   * Reclaims the storage used by the object with the given ID.
   * Asynchronously returns a boolean as the result to the provided
   * DeleteResultCommand, indicating whether the delete was successful.
   * 
   * @param id Pastry key of original object
   * @param authorCred Author's credentials
   */
  public void delete(NodeId id, Credentials authorCred,
                     DeleteResultCommand command);
  
}


/**
 * Command to perform after an insert is completed.
 */
interface InsertResultCommand {
  /**
   * Called when a call to insert completes.
   * @param success Whether the insert was successful
   */
  public void receiveInsertResult(boolean success);
}

/**
 * Command to perform after an update is completed.
 */
interface UpdateResultCommand {
  /**
   * Called when a call to update completes.
   * @param success Whether the update was successful
   */
  public void receiveUpdateResult(boolean success);
}

/**
 * Command to perform after a lookup is completed.
 */
interface LookupResultCommand {
  /**
   * Called when a call to lookup completes.
   * @param result The object stored at the requested address
   */
  public void receiveLookupResult(StorageObject result);
}

/**
 * Command to perform after an existence check is completed.
 */
interface ExistsResultCommand {
  /**
   * Called when a call to exists completes.
   * @param success Whether the desired object exists
   */
  public void receiveExistsResult(boolean exists);
}

/**
 * Command to perform after a delete is completed.
 */
interface DeleteResultCommand {
  /**
   * Called when a call to delete completes.
   * @param success Whether the delete was successful
   */
  public void receiveDeleteResult(boolean successful);
}
