package rice.email;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.post.storage.*;

/**
 * Represents the attachment to an email.
 *
 * @author Alan Mislove
 */
public class EmailData implements PostData {

  /**
   * The data representing the stored data
   */
  protected transient byte[] _data;
  
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
  public ContentHashReference buildContentHashReference(Id location, byte[] key) {
    return new EmailDataReference(location, key);
  }

  /**
   * This method dynamically builds an appropriate SignedReference for
   * this type of PostData given a location.  
   *
   * @param location the location of the data
   * @throws IllegalArgumentException Always
   */
  public SignedReference buildSignedReference(Id location) {
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
  public SecureReference buildSecureReference(Id location, byte[] key) {
    throw new IllegalArgumentException("Email data is only stored as content-hash blocks.");
  }  
  
  /**
   * Returns the data of this attachment
   *
   * @return The data stored in this attachment
   */
  public byte[] getData() {
    return _data;
  }

  /**
   * Returns whether or not this EmailData is equal to the object
   *
   * @return The equality of this and o
   */
  public boolean equals(Object o) {
    if (! (o instanceof EmailData))
      return false;

    return Arrays.equals(_data, ((EmailData) o).getData());
  }

  /**
   * Internal method for writing out this data object
   *
   * @param oos The current output stream
   */
  private void writeObject(ObjectOutputStream oos) throws IOException {
    oos.defaultWriteObject();
    
    oos.writeInt(_data.length);
    oos.write(_data);
  }
  
  /**
   * Internal method for reading in this data object
   *
   * @param ois The current input stream
   */
  private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ois.defaultReadObject();
    
    _data = new byte[ois.readInt()];
    ois.readFully(_data, 0, _data.length);
  }
}
