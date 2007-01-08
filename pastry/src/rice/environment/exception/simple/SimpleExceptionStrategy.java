/*
 * Created on Jan 8, 2007
 */
package rice.environment.exception.simple;

import rice.environment.exception.ExceptionStrategy;
import rice.selector.SelectorManager;

public class SimpleExceptionStrategy implements ExceptionStrategy {

  public void handleException(Object source, Throwable t) {
    if (source instanceof SelectorManager) {
      SelectorManager sm = (SelectorManager)source;
      sm.destroy();
    }
  }

}
