package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.pastry.*;
import ObjectWeb.Security.*;
import ObjectWeb.Persistence.*;

/**
 * This class is used internally by the storage package to
 * store data that is signed.
 * 
 * @version $Id$
 */
class SignedData implements Serializable, Persistable {

  /**
   * The data being stored.
   */
  private byte[] data;

  /**
   * The time that the data was stored.
   */
  private long timestamp;

  /**
   * The signature used to sign the data.
   */
  private byte[] signature;

  /**
   * Credentials of the data.
   */
  private Credentials credentials;
  
  /**
   * Builds a SignedData for a byte array given a timestamp.
   * The signature is the hash of the timestamp appended to the byte array,
   * signed with the user's private key.
   *
   * @param data The data to store
   * @param time The current time
   * @param signature The signature for this data
   * @param credentials Credentials of the data
   */
  public SignedData(byte[] data, long timestamp, byte[] signature, Credentials credentials) {
    this.data = data;
    this.timestamp = timestamp;
    this.signature = signature;
    this.credentials = credentials;
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
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * @return The signature to verify the data and timestamp
   */
  public byte[] getSignature() {
    return signature;
  }

  /**
   * @return Credentials of the data
   */
  public Credentials getCredentials() {
    return credentials;
  }

  /**
   * Called when this object is recovered from persistent store, allowing
   * it to recover any objects it may know about.
   */
  public void reActivate(PersistenceID id) {
  }
}
