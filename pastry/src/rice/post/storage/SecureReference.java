package rice.post.storage;

import java.security.*;
import java.io.*;

import rice.pastry.*;
import rice.past.*;

/**
 * This class serves as a reference to a PostObject
 * stored in the Post system.  This class knows both the
 * location in the network and the encryption key of the
 * corresponding PostData object.  In this scheme, the
 * corresponding Postdata object has been stored using a
 * secure scheme
 *
 * @version $Id$
 */
public class SecureReference implements Serializable {

  /**
   * Location where this data is stored in PAST.
   */
  private NodeId location;

  /**
   * Key used to sign the content hash.
   */
  private Key key;

  /**
   * Contructs a SecureReference object given
   * the address and encryption key of the object.
   *
   * @param location The location in PAST of the PostData object
   * @param key The encryption key of the PostData object
   */
  public SecureReference(NodeId location, Key key) {
    this.location = location;
    this.key = key;
  }

  /**
   * @return The location of the data referenced by this object
   */
  public NodeId getLocation() {
    return location;
  }

  /**
   * @return The encryption key for the data
   */
  public Key getKey() {
    return key;
  }

  public boolean equals(Object o) {
    if (! (o instanceof SecureReference))
      return false;

    SecureReference ref = (SecureReference) o;

    return (location.equals(ref.getLocation()) && key.equals(ref.getKey()));
  }

}
