package rice.post.storage;

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
   * @param msg The string representing the error.
   */
  public StorageException(String msg) {
    super(msg);
  }
}
