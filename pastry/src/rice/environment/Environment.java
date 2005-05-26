/*
 * Created on Apr 6, 2005
 */
package rice.environment;

import rice.environment.logging.LogManager;
import rice.environment.time.TimeSource;


/**
 * @author Jeff Hoye
 */
public class Environment {

  public TimeSource time;
  public LogManager lm;
  
  public Environment(TimeSource time, LogManager lm) {
    this.time = time; 
  }
  
  
}
