package rice.p2p.saar;
                                                                               
/**
 * An exception thrown from within the GNP java classes.
 */
public class GNPException extends RuntimeException {
  /**
   * Constructor for a GNPException.
   * @param message The message to display to the user.
   */
  public GNPException(String message) {
    super(message);
  }
}