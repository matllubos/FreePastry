package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.pastry.*;

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
public interface PostData extends Serializable {

  /**
   * This method dynamically builds an appropriate SignedReference
   * for this type of PostData given a location.
   *
   * @param location The location of the data
   * @return A pointer to the data
   */
  public SignedReference buildSignedReference(Id location);

  /**
   * This method dynamically builds an appropriate ContentHashReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public ContentHashReference buildContentHashReference(Id location, byte[] key);

  /**
    * This method dynamically builds an appropriate SecureReference
   * for this type of PostData given a location and key.
   *
   * @param location The location of the data
   * @param key The for the data
   * @return A pointer to the data
   */
  public SecureReference buildSecureReference(Id location, byte[] key);  
}
