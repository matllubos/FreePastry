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
public class SignedReference {

  /**
   * Contructs a PostDataReference object given
   * the address of the object.
   *
   * @param location The location in PAST of the PostData object
   */
  protected SignedReference(NodeId location) {
  }

  /**
   * Returns the location of the data referenced by this object
   *
   * @return The location of the data
   */
  public NodeId getLocation() {
    return null;
  }

}
