package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.pastry.*;

/**
 * This class is used internally by the storage package to
 * store data that is signed.
 * 
 * @version $Id$
 */
class SignedData implements Serializable {

  /**
   * The data being stored.
   */
  private byte[] data;

  /**
   * The time that the data was stored.
   */
  private byte[] timestamp;

  /**
   * The signature used to sign the data.
   */
  private byte[] signature;
  
  /**
   * Builds a SignedData for a byte array given a timestamp.
   * The signature is the hash of the timestamp appended to the byte array,
   * signed with the user's private key.
   *
   * @param data The data to store
   * @param time The timestamp
   * @param signature The signature for this data
   * @param credentials Credentials of the data
   */
  public SignedData(byte[] data, byte[] timestamp, byte[] signature) {
    this.data = data;
    this.timestamp = timestamp;
    this.signature = signature;
  }

  /**
   * @return The actual data
   */
  public byte[] getData() {
    return data;
  }

  /**
   * @return The timestamp when the data was stored
   */
  public byte[] getTimestamp() {
    return timestamp;
  }

  /**
   * @return The signature to verify the data and timestamp
   */
  public byte[] getSignature() {
    return signature;
  }
}
