/*
 * Created on Jan 31, 2006
 */
package rice.p2p.commonapi.exception;

import rice.p2p.commonapi.NodeHandle;

public class AppSocketException extends Exception {
  Throwable reason;

  public AppSocketException() {}
  
  public AppSocketException(Throwable reason) {
    this.reason = reason; 
  }

  public AppSocketException(String string) {
    super(string);
  }

  public Throwable reason() {
    return reason; 
  }
}
