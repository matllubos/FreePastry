package rice.post.security.ca;

/**
 * DESCRIBE THE CLASS
 *
 * @version $Id$
 * @author amislove
 */
public class password {

  static {
    System.loadLibrary("password");
  }

  /**
   * Gets the Password attribute of the password class
   *
   * @return The Password value
   */
  public native static String getPassword();
}
