package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.pastry.*;
import rice.post.security.*;

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
  private PostSignature signature;
  
  /**
   * Builds a SignedData for a byte array given a timestamp.
   * The signature is the hash of the timestamp appended to the byte array,
   * signed with the user's private key.
   *
   * @param data The data to store
   * @param time The timestamp
   * @param credentials Credentials of the data
   */
  public SignedData(byte[] data, byte[] timestamp) {
    this.data = data;
    this.timestamp = timestamp;
    this.signature = null;
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
   * @return The data and timestamp appended
   */
  public byte[] getDataAndTimestamp() {
    byte[] all = new byte[data.length + timestamp.length];
    System.arraycopy(data, 0, all, 0, data.length);
    System.arraycopy(timestamp, 0, all, data.length, timestamp.length);

    return all;
  }
    
  /**
   * @return The signature to verify the data and timestamp
   */
  public PostSignature getSignature() {
    return signature;
  }

  protected void setSignature(PostSignature sig) {
    signature = sig;
  }
}
