package rice.email;

import java.security.*;
import java.util.*;

import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the attachment to an email.
 * @author Joe Montgomery (?: I think, it was a while ago)
 */
public class EmailData implements PostData {
  byte[] _data;
  
  /**
   * Constructor. Takes in a byte[] representing the data of the
   * attachment
   *
   * @param data The byte[] representation
   */
  public EmailData(byte[] data) {
    _data = data;
  }

  /**
   * This method dynamically builds an appropriate HashReference for
   * this type of PostData given a location and key.  
   *
   * @param location the location of the data
   * @param key the key of the data
   */
  public ContentHashReference buildContentHashReference(NodeId location, Key key) {
    return new EmailDataReference(location, key);
  }

  /**
   * This method dynamically builds an appropriate SignedReference for
   * this type of PostData given a location.  
   *
   * @param location the location of the data
   * @throws IllegalArgumentException Always
   */
  public SignedReference buildSignedReference(NodeId location) {
    throw new IllegalArgumentException("Email data is only stored as content-hash blocks.");
  }  

  /**
   * This method is not supported (you CAN NOT store an emaildata as a
   * secure block).
   *
   * @param location The location of the data
   * @param key The for the data
   * @throws IllegalArgumentException Always
   */
  public SecureReference buildSecureReference(NodeId location, Key key) {
    throw new IllegalArgumentException("Email data is only stored as content-hash blocks.");
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
    if (! (o instanceof EmailData))
      return false;

    return Arrays.equals(_data, ((EmailData) o).getData());
  }

}
