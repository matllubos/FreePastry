package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
* This interface is designed to serve as an abstraction of a
 * data object stored in Post.  This object will be stored
 * in an encrypted state at a location in the network.  Users
 * can access this object by having a copy of the corresponding
 * Reference object, which contains the location and
 * possibly the key of this object.
 * 
 * @version $Id$
 */
public class GroupData implements PostData {
  
  // The list of Postdata objects we are storing
  protected PostData[] data;
  
  /**
   * Builds a GroupData given a collection of PostData
   *
   * @param data The data
   */
  public GroupData(PostData[] data) {
    this.data = data;
  }
  
  /**
   * Returns the data
   *
   * @returns the data
   */
  public PostData[] getData() {
    return data;
  }
  
  /**
  * This method dynamically builds an appropriate SignedReference
   * for this type of PostData given a location.
   *
   * @param location The location of the data
   * @return A pointer to the data
   */
  public SignedReference buildSignedReference(Id location) {
    return new SignedReference(location);
  }
  
  /**
  * This method dynamically builds an appropriate ContentHashReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public ContentHashReference buildContentHashReference(Id[] location, byte[][] key) {
    throw new UnsupportedOperationException("GroupData cannot be stored as a ContentHash");
  }
  
  /**
    * This method dynamically builds an appropriate SecureReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public SecureReference buildSecureReference(Id location, byte[] key) {
    throw new UnsupportedOperationException("GroupData cannot be stored as a Secure");
  }
}
