package rice.storage;

import rice.pastry.security.Credentials;
import rice.pastry.NodeId;

import ObjectWeb.Persistence.Persistable;

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
  public Persistable getOriginal();

  /**
   * Returns the Credentials of the author of the original file.
   * NOT NEEDED IN INTERFACE: package protected access on object
   *
  Credentials getAuthorCredentials();
  */
  
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
   * NOT NEEDED: Authors of updates are handled at application level.
   *
  public Vector getUpdateCredentials();
  */
  
}
