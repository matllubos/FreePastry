/*
 * Created on Jun 6, 2005
 */
package rice.environment.logging;

/**
 * @author Jeff Hoye
 */
public class InvalidLogLevelException extends RuntimeException {

  /**
   * @param arg0
   */
  public InvalidLogLevelException(String key, String val) {
    super(val+" is not an apropriate value for "+key+". Must be an integer or ALL,OFF,SEVERE,WARNING,INFO,CONFIG,FINE,FINER,FINEST");
  }
}
