/*
 * Created on Jan 31, 2006
 */
package rice.p2p.commonapi.exception;

import rice.p2p.commonapi.NodeHandle;

/**
 * Raised if there is no acceptor socket available.
 * 
 * @author Jeff Hoye
 */
public class NoReceiverAvailableException extends AppSocketException {
  public NoReceiverAvailableException() {
    super();
  }
}
