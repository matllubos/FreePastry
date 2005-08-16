/*
 * Created on Jun 1, 2005
 */
package rice.environment.params.simple;

import java.io.*;
import java.io.PrintWriter;


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
  public ParamsNotPresentException(String reason, Exception e) {
    super(reason);
    this.subexception = e;
  }
  
  public void printStackTrace(PrintStream arg0) {
    super.printStackTrace(arg0);
    subexception.printStackTrace(arg0);
  }
  
  public void printStackTrace() {
    super.printStackTrace();
    subexception.printStackTrace();
  }
  
  public void printStackTrace(PrintWriter pw) {
    super.printStackTrace(pw);
    subexception.printStackTrace(pw);
  }
}
