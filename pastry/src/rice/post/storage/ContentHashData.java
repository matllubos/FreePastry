package rice.post.storage;

import java.io.*;
import java.security.*;

import rice.pastry.*;
import ObjectWeb.Security.*;
import ObjectWeb.Persistence.*;

/**
 * This class is used internally by the storage package to
 * store data as a content-hash.
 * 
 * @version $Id$
 */
class ContentHashData implements Serializable, Persistable {

  /** 
   * The data stored in this content hash object.
   */
  private byte[] data;

  /**
   * Credentials of the data
   */
  private Credentials credentials;

  /**
   * Builds a ContentHashData from a byte array and the credentials of data
   *
   * @param data The data to store
   * @param credentials Credentials of the data
   */
  public ContentHashData(byte[] data, Credentials credentials) {
    this.data = data;
    this.credentials = credentials;
  }

  /**
   * @return The byte array of actual data.
   */
  public byte[] getData() {
    return data;
  }

  /**
   * @return The credentials of the data
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
