package rice.storage;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

import ObjectWeb.Persistence.*;

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
 */
public class StorageManagerImpl implements StorageManager {
  private final PersistenceManager _storage;
  private Hashtable _registeredKeys;
  
  /**
   * Constructs a new StorageManager, which uses the given PersistenceManager
   * to store Persistable objects.
   * @param storage An existing PersistenceManager to use
   */
  public StorageManagerImpl(PersistenceManager storage) {
    _storage = storage;
    _registeredKeys = new Hashtable();  // TO DO: try to retrieve from persistence
  }
  
  /**
   * Maps the given Pastry key to the given persistence ID,
   * so incoming requests can be matched to persisted objects.
   */
  protected void _registerKey(NodeId fileId, PersistenceID pid) {
    // TO DO: persist this mapping!
    _registeredKeys.put(fileId, pid);
  }
  
  /**
   * Looks up the PersistenceId corresponding to the given
   * Pastry key.  If no match is found, returns null.
   * @param fileId Pastry key of object being retrieved
   * @return Corresponding PersistenceID, or null if no match
   */
  protected PersistenceID _lookupKey(NodeId fileId) {
    return (PersistenceID) _registeredKeys.get(fileId);
  }
  
  /**
   * Removes the given Pastry key / PersistenceID mapping
   * after an object has been deleted.
   */
  protected void _removeKey(NodeId fileId) {
    _registeredKeys.remove(fileId);
  }
  
  
  /**
   * Inserts an object with the given ID in local storage.
   * @param id Pastry key identifying this object
   * @param obj Persistable object to be stored
   * @param authorCred Author's credentials
   * @return true if store succeeds, false otherwise
   */
  public boolean insert(NodeId id, Persistable obj, Credentials authorCred) {
    // Check if a file already exists
    PersistenceID pid = _lookupKey(id);
    if (pid != null) {
      // Only overwrite if appropriate credentials
      StorageObjectImpl sObject = (StorageObjectImpl) _storage.getObject(pid);
      if (!_canDelete(sObject, authorCred)) {
        // No permission to overwrite
        return false;
      }
      else {
        // Otherwise, author can overwrite successfully
        if (!delete(id, authorCred)) {
          // Delete must have failed for another reason
          return false;
        }
      }
    }

    StorageObjectImpl sObject = new StorageObjectImpl(obj, authorCred);
    pid = _storage.register(sObject);
    _registerKey(id, pid);
    return _storage.makePersistent(sObject, pid);
  }
  
  /**
   * Stores an update to the object with the given ID.
   * @param id Pastry key of original object to be updated
   * @param update Persistable update to the original object
   * @param authorCred Update Author's credentials
   * @return true if update was successful, false if no object was found
   */
  public boolean update(NodeId id, Persistable update, Credentials authorCred) {
    PersistenceID pid = _lookupKey(id);
    if (pid == null) {
      return false;
    }
    StorageObjectImpl sObject = (StorageObjectImpl) lookup(id);
    if (sObject != null) {
      sObject.addUpdate(update, authorCred);
      return _storage.makePersistent(sObject, pid);
    }
    else {
      return false;
    }
  }
  
  /**
   * Retrieves the object and all associated updates with the given ID.
   * @param id Pastry key of original object
   * @return StorageObject with original object and a Vector of all
   * updates to the object, or null if no object was found
   */
  public StorageObject lookup(NodeId id) {
    PersistenceID pid = _lookupKey(id);
    StorageObject sObject = null;
    
    if (pid != null) {
      sObject = (StorageObject) _storage.getObject(pid);
    }
    return sObject;
  }
  
  /**
   * Removes the object with the given ID from storage.
   * @param id Pastry key of original object
   * @param authorCred Author's credentials
   * @return true if object was deleted, false if no object was found
   */
  public boolean delete(NodeId id, Credentials authorCred) {
    PersistenceID pid = _lookupKey(id);
    boolean successful = false;
    
    if (pid != null) {
      StorageObjectImpl sObject = (StorageObjectImpl) _storage.getObject(pid);
      // Only delete if appropriate credentials
      if (_canDelete(sObject, authorCred)) {
        _removeKey(id);
        successful = _storage.makeNonPersistent(pid);
        _storage.unregister(pid);
      }
    }
    return successful;
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
  protected boolean _canDelete(StorageObjectImpl obj, Credentials cred) {
    Credentials authorCred = obj.getAuthorCredentials();
    return ( (authorCred == null) || (authorCred.equals(cred)) );
  }
}