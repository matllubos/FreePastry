package rice.storage;

import java.io.*;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

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
public class StorageObjectImpl implements StorageObject, Serializable {
  
  private final Serializable _original;
  private final Credentials _authorCred;
  private final Vector _updates;
  private final Vector _updateCredentials;
  
  /**
   * Create a new StorageObject for the given Persistable object,
   * using the supplied credentials.
   * @param original The persistable object to store
   * @param cred Credentials of the author of the original object
   */
  public StorageObjectImpl(Serializable original, Credentials cred) {
    _original = original;
    _authorCred = cred;
    _updates = new Vector();
    _updateCredentials = new Vector();
  }
  
  /**
   * Returns the original Persistable object.
   */
  public Serializable getOriginal() {
    return _original;
  }
  
  /**
   * Returns the Credentials of the author of the original file.
   * 
   * TO DO: Resolve security issues with checking credentials!
   * (Currently, anyone can delete a StorageObject using the
   * credentials supplied by this method!)
   */
  public Credentials getAuthorCredentials() {
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
   * Returns the Credentials for each of the updates to the
   * original object.
   * 
   * TO DO: Resolve security issues with checking credentials!
   */
  public Vector getUpdateCredentials() {
    return _updateCredentials;
  }
  
  
  /**
   * Returns a Vector with the original Serializable object in the first position,
   * followed by all Serializable updates.  Purely a convenience method.
   */
  public Vector getAllUpdates() {
    Vector all = new Vector();
    all.add(_original);
    all.addAll(_updates);
    return all;
  }

  /**
   * Returns a Vector with the author Credentials of the original
   * object in the first position, followed by the author Credentials
   * of all updates.  Purely a convenience method.
   */
  public Vector getAllAuthorCredentials() {
    Vector all = new Vector();
    all.add(_authorCred);
    all.addAll(_updateCredentials);
    return all;
  }
  
  
  /**
   * Adds an update to the original object.
   * @param update Persistable update to the original
   * @param authorCred Credentials of author of update
   */
  public void addUpdate(Serializable update, Credentials authorCred) {
    _updates.add(update);
    _updateCredentials.add(authorCred);
  }
}