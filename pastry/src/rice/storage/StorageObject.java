package rice.storage;

import java.io.*;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

import java.util.Vector;

/**
 * @(#) StorageObject.java
 *
 * StorageObject keeps track of a Persistable object which has
 * been stored on the local node, along with all updates that
 * have been stored to it.
 *
 * @version $Id$
 * @author Charles Reis
 */
public interface StorageObject {
  
  /**
   * Returns the original Persistable object.
   */
  public Serializable getOriginal();

  /**
   * Returns the Credentials of the author of the original file.
   * 
   * TO DO: Resolve security issues with checking credentials!
   * (Currently, anyone can delete a StorageObject using the
   * credentials supplied by this method!)
   */
  public Credentials getAuthorCredentials();
  
  /**
   * Returns a Vector of Persistable updates to the original object.
   * Applying the updates to the original objects is application
   * specific.
   */
  public Vector getUpdates();

  /**
   * Returns the Credentials for each of the updates to the
   * original object.
   * 
   * TO DO: Resolve security issues with checking credentials!
   */
  public Vector getUpdateCredentials();
  
  
  /**
   * Returns a Vector with the original Persistable object in the first position,
   * followed by all Persistable updates.  Purely a convenience method.
   */
  public Vector getAllUpdates();

  /**
   * Returns a Vector with the author Credentials of the original
   * object in the first position, followed by the author Credentials
   * of all updates.  Purely a convenience method.
   */
  public Vector getAllAuthorCredentials();

  /**
   * Adds an update to the original object.
   * @param update update to the original
   * @param authorCred Credentials of author of update
   */
  public void addUpdate(Serializable update, Credentials authorCred);
}
