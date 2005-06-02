/*
 * Created on Jun 1, 2005
 */
package rice.environment.params.simple;

import java.io.IOException;

/**
 * Unable to load the default parameters file.  This is required for running pastry.
 * 
 * @author Jeff Hoye
 */
public class DefaultParamsNotPresentException extends IOException {
  IOException subexception;
  
  /**
   * @param ioe
   */
  public DefaultParamsNotPresentException(IOException ioe) {
    this.subexception = ioe;
  }
  
}
