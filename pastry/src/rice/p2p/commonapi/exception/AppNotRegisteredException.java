/*
 * Created on Jan 31, 2006
 */
package rice.p2p.commonapi.exception;

import rice.p2p.commonapi.NodeHandle;

/**
 * The application has not been registered on that node.
 * 
 * @author Jeff Hoye
 */
public class AppNotRegisteredException extends AppSocketException {
  public AppNotRegisteredException() {
    super();
  }
}
