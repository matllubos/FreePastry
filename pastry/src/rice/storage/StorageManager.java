package rice.storage;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

import ObjectWeb.Persistence.Persistable;

/**
 * @(#) StorageManager.java
 *
 * Object responsible for storing and retrieving persistable files on
 * the local node.  Has a notion of appending updates to existing
 * stored files, which are returned with the retrieved object as a
 * sort of log.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface StorageManager {
  
  /**
   * Stores a file with the given fileId in local storage.
   * @param fileId Pastry key identifying this file
   * @param file Persistable file to be stored
   * @param authorCred Author's credentials
   * @return true if store succeeds, false otherwise
   */
  public boolean store(NodeId fileId, Persistable file, Credentials authorCred);
  
  /**
   * Stores an update to the file with ID fileId.
   * @param fileId Pastry key of original file to be updated
   * @param update Persistable update to the original file
   * @return true if update was successful, false if no file was found
   */
  public boolean update(NodeId fileId, Persistable update);
  
  /**
   * Retrieves the file and all associated updates with ID fileId.
   * @param fileId Pastry key of original file
   * @return StorageObject with original file and a Vector of all
   * updates to the file, or null if no file was found
   */
  public StorageObject retrieve(NodeId fileId);
  
  /**
   * Removes the file with ID fileId from storage.
   * @param fileId Pastry key of original file
   * @param authorCred Author's credentials
   * @return true if file was deleted, false if no file was found
   */
  public boolean delete(NodeId fileId, Credentials authorCred);
  
}