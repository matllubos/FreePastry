package rice.post.storage;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.pastry.*;

/**
 * This class is used internally by the storage package to
 * store data as a content-hash.
 * 
 * @version $Id$
 */
class ContentHashData implements Serializable {

  /** 
   * The data stored in this content hash object.
   */
  private byte[] data;

  public transient Object id;

  /**
   * Builds a ContentHashData from a byte array and the credentials of data
   *
   * @param data The data to store
   * @param credentials Credentials of the data
   */
  public ContentHashData(byte[] data) {
    this.data = data;
  }

  /**
   * @return The byte array of actual data.
   */
  public byte[] getData() {
    return data;
  }

  public boolean equals(Object o) {
    if (! (o instanceof ContentHashData))
      return false;

    return Arrays.equals(data, ((ContentHashData) o).getData());
  }

  public String toString() {
    return "ContentHashData[" + id + "]";
  }
}
