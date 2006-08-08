/*
 * Created on Apr 6, 2005
 */
package rice.environment.time.simple;

import rice.environment.time.TimeSource;


/**
 * Uses System.currentTimeMillis() to generate time.
 * 
 * @author Jeff Hoye
 */
public class SimpleTimeSource implements TimeSource {
  
  /**
   * Returns the System.currentTimeMillis();
   */
  public long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public void sleep(long delay) throws InterruptedException {
    Thread.sleep(delay);
  }    
}
