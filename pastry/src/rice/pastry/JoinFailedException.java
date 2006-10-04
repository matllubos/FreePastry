/*
 * Created on Sep 27, 2006
 */
package rice.pastry;

public class JoinFailedException extends Exception {

  public JoinFailedException() {
    super();
  }

  public JoinFailedException(String message, Throwable cause) {
    super(message, cause);
  }

  public JoinFailedException(String message) {
    super(message);
  }

  public JoinFailedException(Throwable cause) {
    super(cause);
  }

}
