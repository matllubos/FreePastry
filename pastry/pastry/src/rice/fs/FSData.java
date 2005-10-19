package rice.fs;

import java.security.*;
import java.util.*;

import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the data stored in the system.
 * @author Ansley Post
 */
public class FSData implements PostData {
 
  /* The actual data in the FSData */
  protected byte[] _data;
  
  /**
   * Constructor. Takes in a byte[] representing the data of the
   * attachment
   *
   * @param data The byte[] representation
   */
  public FSData(byte[] data) {
    _data = data;
  }

  /**
   * This method dynamically builds an appropriate HashReference for
   * this type of PostData given a location and key.  
   *
   * @param location the location of the data
   * @param key the key of the data
   */
  public ContentHashReference buildContentHashReference(Id location, byte[] key) {
    return new ContentHashReference(location, key);
  }

  /**
   * This method dynamically builds an appropriate SignedReference for
   * this type of PostData given a location.  
   *
   * @param location the location of the data
   * @throws IllegalArgumentException Always
   */
  public SignedReference buildSignedReference(Id location) {
    throw new IllegalArgumentException("FS data is only stored as content-hash blocks.");
  }  

  /**
   * This method is not supported (you CAN NOT store fsdata as a
   * secure block).
   *
   * @param location The location of the data
   * @param key The for the data
   * @throws IllegalArgumentException Always
   */
  public SecureReference buildSecureReference(Id location, byte[] key) {
    throw new IllegalArgumentException("FS data is only stored as content-hash blocks.");
  }  
  
  /**
   * Returns the data of this attachment
   *
   * @param The data stored in this attachment
   */
  public byte[] getData() {
    return _data;
  }

  public boolean equals(Object o) {
    if (! (o instanceof FSData))
      return false;

    return Arrays.equals(_data, ((FSData) o).getData());
  }

}
