package rice.post.storage;

import java.io.*;
import java.security.*;
import java.util.*;

import rice.pastry.*;

/**
 * This class is used internally by the storage package to
 * store data using a secure scheme.
 *
 * @version $Id$
 */
class SecureData implements Serializable {

  /**
  * The data stored in this content hash object.
   */
  private byte[] data;

  /**
    * Builds a SecureData from a byte array and the credentials of data
   *
   * @param data The data to store
   * @param credentials Credentials of the data
   */
  public SecureData(byte[] data) {
    this.data = data;
  }

  /**
    * @return The byte array of actual data.
   */
  public byte[] getData() {
    return data;
  }

  public boolean equals(Object o) {
    if (! (o instanceof SecureData))
      return false;

    return Arrays.equals(data, ((SecureData) o).getData());
  }
}
