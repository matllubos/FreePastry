package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.pastry.*;
import ObjectWeb.Security.*;
import ObjectWeb.Persistence.*;

/**
 * This class is used internally by the storage package to
 * store data that is stored as a content-hash.
 */
class ContentHashData implements Serializable, Persistable {

  // the data
  private byte[] data;

  // our credentials
  private Credentials credentials;

  /**
   * Builds a ContentHashData from a byte[], timestamp, and signature
   *
   * @param data The data to store
   */
  public ContentHashData(byte[] data, Credentials credentials) {
    this.data = data;
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

  public Credentials getCredentials() {
    return credentials;
  }

  public void reActivate(PersistenceID id) {
  }
}
