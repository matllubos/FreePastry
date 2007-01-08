/*
 * Created on Jan 8, 2007
 */
package rice.environment.exception;

import rice.selector.SelectorManager;

public interface ExceptionStrategy {

  void handleException(Object source, Throwable t);

}
