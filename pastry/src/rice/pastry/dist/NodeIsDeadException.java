package rice.pastry.dist;

/**
 * Thrown if you try to send a message on a node that has been
 * killed.
 * 
 * @author Jeff Hoye 
 */
public class NodeIsDeadException extends RuntimeException {

  /**
   * The original reason we found the node to be dead.
   */
  public Exception originalException;

  /**
   * Constructor for NodeIsDeadException.
   *
   * @param e The original exception when we noticed
   * we had been killed.
   */
  public NodeIsDeadException(Exception e) {
    originalException = e;
  }

}
