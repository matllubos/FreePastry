package rice.post.storage;

import java.security.*;

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
public class ContentHashReference {
  
  /**
   * Location where this data is stored in PAST.
   */
  private NodeId location;
  
  /**
   * Key used to sign the content hash.
   */
  private Key key;

  /**
   * Contructs a PostDataReference object given
   * the address and encryption key of the object.
   *
   * @param location The location in PAST of the PostData object
   * @param key The encryption key of the PostData object
   */
  public ContentHashReference(NodeId location, Key key) {
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

}
