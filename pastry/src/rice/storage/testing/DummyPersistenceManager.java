package rice.storage.testing;

import rice.storage.*;

import ObjectWeb.Persistence.*;
import ObjectWeb.Security.Credentials;

import java.util.*;


/**
 * A test implementation of ObjectWeb's PersistenceManager.
 *
 * @version $Id$
 * @author Charlie Reis
 *
 * @deprecated This version of storage has been deprecated - please use the version
 *   located in the rice.persistence package.
 */

public class DummyPersistenceManager implements PersistenceManager {
  protected long _idCounter;
  
  protected Hashtable _idObjectTable, _objectIDTable;
  protected Hashtable _persistedObjects;
  
  public DummyPersistenceManager() {
    _idCounter = 0;
    _idObjectTable = new Hashtable();
    _objectIDTable = new Hashtable();
    _persistedObjects = new Hashtable();
    
  }
  
  /**
   * Register the given <code>Persistable</code> object with the
   * local persistence manager. Assign a persistence id
   * to this object. The persistence id is unique in time 
   * and will persist across crashes of the local <code>Proxy</code>.     
   * Do no security checking for registration.
   *
   * <p> If an object is registered twice, it should return the same
   * <code>PersistenceID</code> object.
   *
   * @param obj The <code>Persistable</code> object to register.
   * @return A <code>PersistenceID</code> object assigned to <code>obj</code>.
   */
  public synchronized PersistenceID register(Persistable obj) {
    _idCounter++;
    PersistenceID id = new DummyPersistenceID(_idCounter);
    if (_objectIDTable.containsKey(obj)) {
      return (PersistenceID) _objectIDTable.get(obj);
    }
    _objectIDTable.put(obj, id);
    _idObjectTable.put(id.toString(), obj);
    return id;
  }
  
  
  /**
   * Unregister a <code>Persistable object</code>. This object will
   * no longer use the services of the <code>PersistenceManager</code>.
   * Automatically cause all storage assigned to the object to be
   * reclaimed. Render the object's persistence id invalid for all
   * time. The object can no longer be restored after a crash.
   *
   * @param pid The object's persistence id.
   */
  public synchronized void unregister(PersistenceID pid) {
    if (_idObjectTable.containsKey(pid)) {
      Object obj = _idObjectTable.remove(pid);
      _persistedObjects.remove(pid);
      _objectIDTable.remove(obj);
    }
  }
  
  
  /**
   * Make an object persistent atomically. Will save a serialized image
   * of the given object on stable storage, in a manner that allows the
   * object to be re-activated after a failure and restart of the local
   * proxy.
   * <p>
   * A transcation is implemented to make this action atomic. 
   * Shadow redirection file is used to make transaction possible.
   * <p>
   * If the object is already persistent, this method will 
   * simply update the object's serialized image.
   *
   * @param obj The object to be made persistent.
   * @param pid The object's persistence id.
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public synchronized boolean makePersistent(Persistable obj, PersistenceID pid) {
    _persistedObjects.put(pid, obj);
    return true;
  }
  
  
  /**
   * Request to remove the object from the list of persistent objects.
   * Delete the serialized image of the object from stable storage.
   *
   * <p> If the object was not in the persistent list in the first place,
   * nothing happens and <code>false</code> is returned.
   *
   * @param pid The object's persistence id
   * @return <code>true</code> if the action succeeds, else
   * <code>false</code>.
   */
  public synchronized boolean makeNonPersistent(PersistenceID pid) {
    Object obj = _persistedObjects.remove(pid);
    return (obj != null);
  }
  
  
  /**
   * Provide automatic recovery of persistent objects after a crash.
   * All objects previously made persistent will be re-activated 
   * from their serial images found on persistent storage.
   * The object will be restored to exact state at the time of its
   * last call to <code>makePersistent</code>.  It will be rebound 
   * in the namespace to the names it was bound to at its last call
   * to <code>makePersistent</code> and re-registered with the cache
   * manager.
   *
   * <p> Once a new object has been registered, this function can no
   * longer be called.
   *
   * @return The number of objects recovered.
   */
  public synchronized int recoverPersistentObjects() {
    return _persistedObjects.size();
  }
  
  
  /**
   * Return the <code>Persistable</code> object identified by the
   * given persistence id.
   *
   * @param pid The persistence id of the object in question.
   * @return The <code>Persistable</code> object, or
   * <code>null</code> if the pid is invalid.
   */
  public synchronized Persistable getObject(PersistenceID pid) {
    return (Persistable) _persistedObjects.get(pid);
  }
  
  
  /**
   * Create a private persistent storage object that a persistent object can
   * use to store and retrieve other objects.  Highly useful for content
   * objects to shrink their size or store persistent accounting
   * information.  Passing in the same key twice will result in
   * references to equivalent <code>PersistentStorage</code> objects.
   * 
   * @param pid That persistence object's <code>PersistenceID</code>.
   * @param key The key to the generated persistence storage object.
   */
  public PersistentStorage getStorage(PersistenceID pid, String key) {
    throw new RuntimeException("getStorage not implemented.");
  }
  
  /**
   * Create a private persistent storage object that a persistent object can
   * use to store and retrieve other objects.  Highly useful for content
   * objects to shrink their size or store persistent accounting
   * information.  Passing in the same key twice will result in
   * references to equivalent <code>PersistentStorage</code> objects.
   *
   * @param obj The persistence object requiring the persistent storage.
   * @param pid That persistence object's <code>PersistenceID</code>.     
   * @deprecated use <code>getStorage(PersistenceID pid, String key)</code>
   * instead.
   */
  public PersistentStorage getStorage(Persistable obj, PersistenceID pid) {
    throw new RuntimeException("getStorage not implemented.");
  }
  
  /**
   * Tell the persistence manager to free up a persistent storage from the
   * disk. Only the object that owns the storage may call this function.
   * If the input storage object has already been freed, return
   * <code>true</code>.
   *
   * @param storage The persistent storage object to be freed.
   * @return <code>true</code> if the action succeeds, else <code>false</code>.
   */
  public boolean free(PersistentStorage storage) {
    throw new RuntimeException("free not implemented.");
  }
  
  
  /**
   * Return the <code>PersistenceConfigs</code> object for this
   * persistence manager.
   *
   * @param userCredentials The caller's credentials.
   * @return A <code>PersistenceConfigs</code> object for this
   *         persistence manager.
   */
  public PersistenceConfigs getPersistenceConfigs(Credentials userCredentials) {
    throw new RuntimeException("getPersistenceConfigs not implemented.");
  }
  
  
  /**
   * This class provides an implementation for PersistenceID.
   */
  class DummyPersistenceID extends PersistenceID {
    
    /**
     * @serial long id
     * A long integer (64 bits) used as the actual id.
     */
    private long id;
    
    /**
     * Construct a PersistenceID using a long integer.
     * 
     * @param _id A long integer as the actual id.
     */
    public DummyPersistenceID(long _id) {
      id = _id;
    }
    
    /**
     * Get the long integer id.
     * 
     * @return the long integer id
     */
    public long getId() {
      return id;
    }
    
    /**
     * Check whether two PersistenceID instances are equal.
     * 
     * @param pid Another PersistenceID instance
     * @return Whether the two PersistenceID instances are equal.
     */
    public boolean equals(PersistenceID pid) {
      if (id == ((DummyPersistenceID) pid).getId())
        return true;
      return false;
    }
    
    /* Get the string format of the id.
     * 
     * @return A string for the long integer id
     */
    public String toString() {
      Long l = new Long(id);
      return l.toString();
    }
  }
}