/*
 * Created on Apr 6, 2005
 */
package rice.environment.time;

/**
 * Virtualized clock for FreePastry.  
 * 
 * Can return the current time, or be blocked on.
 * 
 * Usually acquired by calling environment.getTimeSource().
 * 
 * @author Jeff Hoye
 */
public interface TimeSource {
  /**
   * @return the current time in millis
   */
  public long currentTimeMillis();
  
  /**
   * block for this many millis
   * 
   * @param delay the amount of time to sleep
   * @throws InterruptedException 
   */
  public void sleep(long delay) throws InterruptedException;
}
