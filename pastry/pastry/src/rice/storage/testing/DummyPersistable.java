package rice.storage.testing;

import rice.storage.*;

import ObjectWeb.Persistence.*;
import ObjectWeb.Security.*;

import java.util.*;
import java.io.Serializable;


/**
 * A test implementation of ObjectWeb's Persistable interface.
 *
 * @version $Id$
 * @author Charlie Reis
 *
 * @deprecated This version of storage has been deprecated - please use the version
 *   located in the rice.persistence package.
 */

public class DummyPersistable implements Persistable, Serializable {
  private String _contents;
  private Credentials _cred;
  private static final CredentialsFactory _credFactory =
    new CredentialsFactory();
  
  /**
   * Creates a new persistable object containing a string.
   * @param contents String to persist
   * @param cred Credentials for object
   */
  public DummyPersistable(String contents, Credentials cred) {
    _contents = contents;
    _cred = cred;
  }
  
  /**
   * Creates a new persistable object containing a string.
   * @param contents String to persist
   */
  public DummyPersistable(String contents) {
    this(contents, 
         _credFactory.createCredential(CredentialsFactory.CONTENT_CREDENTIAL));
  }
  
  /**
   * Returns the contents of this persistable object.
   */
  public String getContents() {
    return _contents;
  }
  
  /**
   * Returns the Object's credentials.
   *
   * @return The objects's credential object
   */
  public Credentials getCredentials() {
    return _cred;
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
  
  public boolean equals(Object o) {
    if (o instanceof DummyPersistable) {
      return _contents.equals( ((DummyPersistable)o).getContents() );
    }
    return false;
  }
  
}