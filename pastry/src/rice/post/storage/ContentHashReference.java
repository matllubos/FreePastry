package rice.post.storage;

import java.security.*;

import rice.pastry.*;
import rice.past.*;

/**
 * This class serves as a reference to a PostObject
 * stored in the Post system.  This class knows both the
 * location in the network and the encryption key of the
 * corresponding PostData object.
 */
public class ContentHashReference {

  /**
   * Contructs a PostDataReference object given
   * the address and encryption key of the object.
   *
   * @param location The location in PAST of the PostData object
   * @param key The encryption key of the PostData object
   */
  protected ContentHashReference(NodeId location, Key key) {
  }

  /**
   * Returns the location of the data referenced by this object
   *
   * @return The location of the data
   */
  public NodeId getLocation() {
    return null;
  }

  /**
   * Returns the encryption key for this object
   *
   * @return The encrypted key of the data
   */
  public Key getKey() {
    return null;
  }

}
