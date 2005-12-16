package rice.post.storage;

import rice.p2p.commonapi.Id;
import rice.post.*;

/**
 * An exception that occurred when storing or retrieving data in POST.
 * 
 * @version $Id$
 */
public class StorageException extends PostException {
  
  /**
   * Constructor.
   *
   * @param location The location of the object related to the error
   * @param msg The string representing the error.
   */
  public StorageException(Id location, String msg) {
    super(location.toStringFull() + ": " + msg);
  }
  
  /**
   * Constructor.
   *
   * @param msg The string representing the error.
   */
  public StorageException(String msg) {
    super(msg);
  }
}
