package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.pastry.*;
import ObjectWeb.Security.*;
import ObjectWeb.Persistence.*;

/**
 * This class is used internally by the storage package to
 * store data that is signed.
 */
class SignedData implements Serializable, Persistable {

  // the data
  private byte[] data;

  // the time
  private long timestamp;

  // the signature
  private byte[] signature;

  // our credentials
  private Credentials credentials;
  
  /**
   * Builds a signedData from a byte[], timestamp, and signature
   *
   * @param data The data to store
   * @param time The current time
   * @param signature The signature for this data
   */
  public SignedData(byte[] data, long timestamp, byte[] signature, Credentials credentials) {
    this.data = data;
    this.timestamp = timestamp;
    this.signature = signature;
    this.credentials = credentials;
  }

  /**
   * Returns the data
   *
   * @return The data
   */
  public byte[] getData() {
    return data;
  }

  /**
   * Returns the timestamp
   *
   * @return The timestamp
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the signature
   *
   * @return The signature
   */
  public byte[] getSignature() {
    return signature;
  }

  public Credentials getCredentials() {
    return credentials;
  }

  public void reActivate(PersistenceID id) {
  }
}
