package rice.post.storage;

import java.security.*;
import java.io.*;
import java.util.*;

import rice.pastry.*;
import rice.past.*;

/**
 * This class serves as a reference to a PostObject
 * stored in the Post system.  This class knows both the
 * location in the network and the encryption key of the
 * corresponding PostData object.
 * 
 * @version $Id$
 */
public class ContentHashReference implements Serializable {
  
  /**
   * Location where this data is stored in PAST.
   */
  private Id location;
  
  /**
   * Key used to sign the content hash.
   */
  private byte[] key;

  /**
   * Contructs a PostDataReference object given
   * the address and encryption key of the object.
   *
   * @param location The location in PAST of the PostData object
   * @param key The encryption key of the PostData object
   */
  public ContentHashReference(Id location, byte[] key) {
    this.location = location;
    this.key = key;
  }

  /**
   * @return The location of the data referenced by this object
   */
  public Id getLocation() {
    return location;
  }

  /**
   * @return The encryption key for the data
   */
  public byte[] getKey() {
    return key;
  }

  public boolean equals(Object o) {
    if (! (o instanceof ContentHashReference))
      return false;

    ContentHashReference ref = (ContentHashReference) o;

    return (location.equals(ref.getLocation()) &&
            Arrays.equals(key, ref.getKey()));
  }
}
