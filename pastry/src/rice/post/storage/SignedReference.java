package rice.post.storage;

import java.security.*;
import java.io.*;

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
public class SignedReference implements Serializable {
  
  /**
   * Location where this data is stored in PAST.
   */
  private NodeId location;

  /**
   * Contructs a PostDataReference object given
   * the address of the object.
   *
   * @param location The location in PAST of the PostData object
   */
  public SignedReference(NodeId location) {
    this.location = location;
  }

  /**
   * @return The location of the data referenced by this object
   */
  public NodeId getLocation() {
    return location;
  }

}
