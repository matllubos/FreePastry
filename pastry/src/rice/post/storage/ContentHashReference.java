package rice.post.storage;

import java.security.*;
import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * This class serves as a reference to a PostObject
 * stored in the Post system.  This class knows both the
 * location in the network and the encryption key of the
 * corresponding PostData object.
 *
 * This class has been extended to support multiple object locations
 * and keys, with each stored in ContentHash form.  This is to allow
 * very large objects to be transparently broken up into a group of
 * smaller objects, and each fragment is stored seperately. Note that
 * a readObject() method has been defined to support the automatic
 * migration of old-style references to the new style.
 * 
 * @version $Id$
 */
public class ContentHashReference implements Serializable {
  
  // serialver, for backwards compatibility
  private static final long serialVersionUID = 5215474536871804216L;
  
  /**
   * Location where this data is stored in PAST.
   */
  private Id[] locations;
  
  /**
   * Key used to sign the content hash.
   */
  private byte[][] keys;

  /**
   * Contructs a PostDataReference object given
   * the address and encryption key of the object.
   *
   * @param location The location in PAST of the PostData object
   * @param key The encryption key of the PostData object
   */
  public ContentHashReference(Id[] locations, byte[][] keys) {
    this.locations = locations;
    this.keys = keys;
  }

  /**
   * @return The locations of the data referenced by this object
   */
  public Id[] getLocations() {
    return locations;
  }

  /**
   * @return The encryption keys for the data
   */
  public byte[][] getKeys() {
    return keys;
  }
  
  public int hashCode() {
    int result = 383727781;
    
    for (int i=0; i<locations.length; i++)
      result ^= locations[i].hashCode();
    
    return result;
  }

  public boolean equals(Object o) {
    if (! (o instanceof ContentHashReference))
      return false;

    ContentHashReference ref = (ContentHashReference) o;

    return Arrays.equals(locations, ref.getLocations());
  }

  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append("[ContentHashRef ");
    
    for (int i=0; i<locations.length; i++)
      result.append(locations[i].toString());
    
    result.append(" ]");
    
    return result.toString();
  }
  
  /**
   * ReadObject overridden in order to support translation from
   * old -> new style references.  
   *
   * @param ois Object Input Stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ObjectInputStream.GetField gf = ois.readFields();
    
    if (! gf.defaulted("locations")) {
      this.locations = (Id[]) gf.get("locations", new Object());
      this.keys = (byte[][]) gf.get("keys", new Object());
    } else {
      if (gf.get("location", null) != null) {
        this.locations = new Id[] { (Id) gf.get("location", new Object()) };
        this.keys = new byte[][] { (byte[]) gf.get("key", new Object()) };
      } else {
        this.locations = new Id[0];
        this.keys = new byte[0][0];
      }
    }
  }
}
