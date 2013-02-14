package rice.email;

import java.security.*;

import rice.pastry.*;
import rice.post.storage.*;

/**
 * Represents the attachment to an email.
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
  public ContentHashReference buildContentHashReference(NodeId location, Key key){
    return null;
  }

  /**
   * This method dynamically builds an appropriate SignedReference for
   * this type of PostData given a key.  
   *
   * @param location the location of the data
   */
  public SignedReference buildSignedReference(NodeId location){
    return null;
  }
  
  /**
   * Returns the data of this attachment
   *
   * @param The data stored in this attachment
   */
  public byte[] getData() {
    return _data;
  }

}
