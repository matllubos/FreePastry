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
   * Stores a file with the given fileId in local storage.
   * @param fileId Pastry key identifying this file
   * @param file Persistable file to be stored
   * @param authorCred Author's credentials
   * @return true if store succeeds, false otherwise
   */
  public boolean store(NodeId fileId, Persistable file, Credentials authorCred) {
    // Check if a file already exists
    PersistenceID pid = _lookupKey(fileId);
    if (pid != null) {
      // Only overwrite if appropriate credentials
      StorageObjectImpl object = (StorageObjectImpl) _storage.getObject(pid);
      if (!_canDelete(object, authorCred)) {
        // No permission to overwrite
        return false;
      }
      else {
        // Otherwise, author can overwrite successfully
        if (!delete(fileId, authorCred)) {
          // Delete must have failed for another reason
          return false;
        }
      }
    }

    StorageObjectImpl object = new StorageObjectImpl(file, authorCred);
    pid = _storage.register(object);
    _registerKey(fileId, pid);
    return _storage.makePersistent(object, pid);
  }
  
  /**
   * Stores an update to the file with ID fileId.
   * @param fileId Pastry key of original file to be updated
   * @param update Persistable update to the original file
   * @return true if update was successful, false if no file was found
   */
  public boolean update(NodeId fileId, Persistable update) {
    PersistenceID pid = _lookupKey(fileId);
    if (pid == null) {
      return false;
    }
    StorageObjectImpl object = (StorageObjectImpl) retrieve(fileId);
    if (object != null) {
      object.addUpdate(update);
      return _storage.makePersistent(object, pid);
    }
    else {
      return false;
    }
  }
  
  /**
   * Retrieves the file and all associated updates with ID fileId.
   * @param fileId Pastry key of original file
   * @return StorageObject with original file and a Vector of all
   * updates to the file, or null if no file was found
   */
  public StorageObject retrieve(NodeId fileId) {
    PersistenceID pid = _lookupKey(fileId);
    StorageObject object = null;
    
    if (pid != null) {
      object = (StorageObject) _storage.getObject(pid);
    }
    return object;
  }
  
  /**
   * Removes the file with ID fileId from storage.
   * @param fileId Pastry key of original file
   * @param authorCred Author's credentials
   * @return true if file was deleted, 
   * false if no file was found or inappropriate credentials
   */
  public boolean delete(NodeId fileId, Credentials authorCred) {
    PersistenceID pid = _lookupKey(fileId);
    boolean successful = false;
    
    if (pid != null) {
      StorageObjectImpl object = (StorageObjectImpl) _storage.getObject(pid);
      // Only delete if appropriate credentials
      if (_canDelete(object, authorCred)) {
        _removeKey(fileId);
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
   * @param obj StorageObject to delete
   * @param cred Credentials of user wishing to delete obj
   */
  protected boolean _canDelete(StorageObjectImpl obj, Credentials cred) {
    Credentials authorCred = obj.getAuthorCredentials();
    return ( (authorCred == null) || (authorCred.equals(cred)) );
  }
}