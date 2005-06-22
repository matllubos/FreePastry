/*
 * Created on Jun 1, 2005
 */
package rice.environment.params.simple;


/**
 * Unable to load the default parameters file.  This is required for running pastry.
 * 
 * @author Jeff Hoye
 */
public class ParamsNotPresentException extends RuntimeException {
  Exception subexception;
  
  /**
   * @param ioe
   */
  public ParamsNotPresentException(Exception e) {
    this.subexception = e;
  }
  
}
