/*
 * Created on Apr 6, 2005
 */
package rice.environment;

import rice.environment.time.TimeSource;


/**
 * @author Jeff Hoye
 */
public class Environment {

  public TimeSource time;
  
  public Environment(TimeSource time) {
    this.time = time; 
  }
  
}
