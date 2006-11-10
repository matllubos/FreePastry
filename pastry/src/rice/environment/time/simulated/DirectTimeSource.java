/*
 * Created on Nov 8, 2005
 */
package rice.environment.time.simulated;

import rice.environment.logging.*;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;
import rice.selector.*;

public class DirectTimeSource implements TimeSource {

  private long time = 0;
  private Logger logger = null;
  private String instance;
  private SelectorManager selectorManager;
  
  public DirectTimeSource(long time) {
    this(time, null);
  }

  public DirectTimeSource(long time, String instance) {
    if (time < 0) {
      time = System.currentTimeMillis();
    } else {
      this.time = time; 
    }
    this.instance = instance;
  }
  
  public DirectTimeSource(Parameters p) {
    this(p.getLong("direct_simulator_start_time")); 
  }

  public void setLogManager(LogManager manager) {
    logger = manager.getLogger(DirectTimeSource.class, instance);
  }
  
  public void setSelectorManager(SelectorManager sm) {
    selectorManager = sm;
  }
  
  public long currentTimeMillis() {
    return time;
  }
  
  /**
   * Should be synchronized on the selectorManager
   * @param newTime
   */
  public void setTime(long newTime) {
    if (newTime < time) {
      throw new RuntimeException("Attempted to set time from "+time+" to "+newTime+".");
    }
    if (logger.level <= Logger.FINER) logger.log("DirectTimeSource.setTime("+time+"=>"+newTime+")");
    time = newTime;
  }

  /**
   * Should be synchronized on the selectorManager
   */
  public void incrementTime(int millis) {
    setTime(time+millis); 
  }

  private class BlockingTimerTask extends TimerTask {
    boolean done = false;
    
    public void run() {
      synchronized(selectorManager) {
        done = true;
        selectorManager.notifyAll();
        // selector already yields enough
//        Thread.yield();
      }
    }
    
  }
  
  public void sleep(long delay) throws InterruptedException {
    synchronized(selectorManager) { // to prevent an out of order acquisition
      // we only lock on the selector
      BlockingTimerTask btt = new BlockingTimerTask();
      if (logger.level <= Logger.FINE) logger.log("DirectTimeSource.sleep("+delay+")");
      
      selectorManager.getTimer().schedule(btt,delay);
      
      while(!btt.done) {
        selectorManager.wait(); 
      }
    }
  }
  
}
