package rice.post;

/**
 * Any exception specific to POST.
 * 
 * @version $Id$
 */
public class PostException extends Exception {
  
  /**
   * Constructor.
   *
   * @param msg The string representing the error.
   */
  public PostException(String msg) {
    super(msg);
  }
}
