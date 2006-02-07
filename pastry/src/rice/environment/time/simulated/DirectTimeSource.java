/*
 * Created on Nov 8, 2005
 */
package rice.environment.time.simulated;

import rice.environment.logging.*;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;

public class DirectTimeSource implements TimeSource {

  private long time = 0;
  private Logger logger = null;
  private String instance;
  
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
  
  public long currentTimeMillis() {
    return time;
  }
  
  public void setTime(long newTime) {
    if (newTime < time) {
      throw new RuntimeException("Attempted to set time from "+time+" to "+newTime+".");
    }
    if (logger.level <= Logger.FINE) logger.log("DirectTimeSource.setTime("+time+"=>"+newTime+")");
    time = newTime;
  }

  public void incrementTime(int millis) {
    setTime(time+millis); 
  }
  
}
