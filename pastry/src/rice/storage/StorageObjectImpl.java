package rice.storage;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

import ObjectWeb.Persistence.Persistable;
import ObjectWeb.Persistence.PersistenceID;

import java.util.Vector;
import java.io.Serializable;

/**
 * @(#) StorageObjectImpl.java
 *
 * StorageObject keeps track of a Persistable object which has
 * been stored on the local node, along with all updates that
 * have been stored to it.
 *
 * @version $Id$
 * @author Charles Reis
 */
public class StorageObjectImpl implements StorageObject, Persistable, Serializable {
  private final Persistable _original;
  private final Credentials _authorCred;
  private final Vector _updates;
  //private final Vector _updateCredentials;
  
  /**
   * Create a new StorageObject for the given Persistable object,
   * using the supplied credentials.
   * @param original The persistable object to store
   * @param cred Credentials of the author of the original object
   */
  public StorageObjectImpl(Persistable original, Credentials cred) {
    _original = original;
    _authorCred = cred;
    _updates = new Vector();
    //_updateCredentials = new Vector();
  }
  
  /**
   * Returns the original Persistable object.
   */
  public Persistable getOriginal() {
    return _original;
  }
  
  /**
   * Returns the Credentials of the author of the original file.
   * Package protected: this is not accessible to most users.
   */
  Credentials getAuthorCredentials() {
    return _authorCred;
  }
  
  /**
   * Returns a Vector of Persistable updates to the original object.
   * Applying the updates to the original objects is application
   * specific.
   */
  public Vector getUpdates() {
    return _updates;
  }
  
  /**
   * Returns a Vector of Credentials objects corresponding to each
   * update in the Updates vector.
   * 
   * NOT NEEDED: Authors of updates are handled at application level.
   *
  public Vector getUpdateCredentials() {
    return _updateCredentials;
  }
  */
  
  /**
   * Adds an update to the original object.
   * @param update Persistable update to the original
   */
  public void addUpdate(Persistable update) {
    _updates.add(update);
    //_updateCredentials.add(cred);
  }
  
  
  // ---------- Persistable Methods ---------

  /**
   * Returns the Object's own credentials.
   *
   * @return The objects's credential object
   */
  public ObjectWeb.Security.Credentials getCredentials() {
    return _original.getCredentials();
  }

  
  /**
   * Called by the <code>PersistenceManager</code> in the context of the
   * <code>recoverPersistentObjects</code> method just after an object was
   * recovered. Passes the object's new <code>PersistanceID</code> object as an
   * argument.
   *
   * <p> The object can use this pid to access any <code>PersistentStorage</code>
   * objects it may own.
   *
   * @param pid The object's new <code>PersistenceID</code>
   */
  public void reActivate(PersistenceID pid) {
  }
  
}