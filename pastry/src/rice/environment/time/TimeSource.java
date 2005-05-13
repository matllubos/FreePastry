/*
 * Created on Apr 6, 2005
 */
package rice.environment.time;

/**
 * Interface to return the current time.  The simplest interface can simply 
 * return System.currentTimeMillis();  
 * 
 * @author Jeff Hoye
 */
public interface TimeSource {
  /**
   * @return the current time in millis
   */
  public long currentTimeMillis();
}
