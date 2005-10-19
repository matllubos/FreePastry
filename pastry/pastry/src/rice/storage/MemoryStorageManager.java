package rice.storage;

import java.io.*;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

import java.util.Hashtable;

/**
* @(#) StorageManagerImpl.java
 *
 * Object responsible for storing and retrieving persistable files on
 * the local node.  Has a notion of appending updates to existing
 * stored files, which are returned with the retrieved object as a
 * sort of log.
 * <p>
 *
 * Uses ObjectWeb's Persistence manager to store and retrieve files.
 *
 * @version $Id$
 * @author Charles Reis
 *
 * @deprecated This version of storage has been deprecated - please use the version
 *   located in the rice.persistence package.
 */
public class MemoryStorageManager implements StorageManager {

  private Hashtable keys;

  /**
   * Constructs a new MemoryStorageManager, which stores data in memory.
   *
   * @param storage An existing PersistenceManager to use
   */
  public MemoryStorageManager() {
    keys = new Hashtable();  // TO DO: try to retrieve from persistence
  }

  /**
   * Inserts an object with the given ID in local storage.
   * @param id Pastry key identifying this object
   * @param obj Persistable object to be stored
   * @param authorCred Author's credentials
   * @return true if store succeeds, false otherwise
   */
  public boolean insert(NodeId id, Serializable obj, Credentials authorCred) {

    StorageObject so = (StorageObject) keys.get(id);

    if (so != null) {
      if (! _canDelete(so, authorCred)) {
        // No permission to overwrite
        return false;
      } else {
        // Otherwise, author can overwrite successfully
        if (!delete(id, authorCred)) {
          // Delete must have failed for another reason
          return false;
        }
      }
    }

    so = new StorageObjectImpl(obj, authorCred);
    keys.put(id, so);

    return true;
  }

  /**
   * Stores an update to the object with the given ID.
   * @param id Pastry key of original object to be updated
   * @param update Persistable update to the original object
   * @param authorCred Update Author's credentials
   * @return true if update was successful, false if no object was found
   */
  public boolean update(NodeId id, Serializable update, Credentials authorCred) {
    StorageObject so = (StorageObject) keys.get(id);
    
    if (so == null) {
      return false;
    }

    so.addUpdate(update, authorCred);

    return true;
  }

  /**
   * Retrieves the object and all associated updates with the given ID.
   * @param id Pastry key of original object
   * @return StorageObject with original object and a Vector of all
   * updates to the object, or null if no object was found
   */
  public StorageObject lookup(NodeId id) {
    return (StorageObject) keys.get(id);
  }

  /**
    * Returns whether an object is currently stored at the given ID.
   * @param id Pastry key of original object
   * @return true if an object was found, false otherwise
   */
  public boolean exists(NodeId id) {
    return (keys.get(id) != null);
  }

  /**
    * Removes the object with the given ID from storage.
   * @param id Pastry key of original object
   * @param authorCred Author's credentials
   * @return true if object was deleted, false if no object was found
   */
  public boolean delete(NodeId id, Credentials authorCred) {
    StorageObject so = (StorageObject) keys.get(id);

    if (so == null) {
      return false;
    }
    
    // Only delete if appropriate credentials
    if (_canDelete(so, authorCred)) {
      keys.remove(id);
      return true;
    }
    
    return false;
  }

  /**
    * Returns whether the given object can be deleted from storage
   * by a user with the supplied credentials.
   * NOTE:  Currently only checks to see if the supplied credentials
   * are equal to the author's credentials, or if the author's
   * credentials were null.
   *
   * TO DO:
   * The StorageManager needs a SecurityManager to handle this behavior!
   *
   * @param obj StorageObject to delete
   * @param cred Credentials of user wishing to delete obj
   */
  protected boolean _canDelete(StorageObject obj, Credentials cred) {
    Credentials authorCred = obj.getAuthorCredentials();
    return ( (authorCred == null) || (authorCred.equals(cred)) );
  }
}