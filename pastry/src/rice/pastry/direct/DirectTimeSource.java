/*
 * Created on Nov 8, 2005
 */
package rice.pastry.direct;

import rice.environment.time.TimeSource;

public class DirectTimeSource implements TimeSource {

  private long time = 0;
  
  public DirectTimeSource(long time) {
    this.time = time; 
  }
  
  public long currentTimeMillis() {
    return time;
  }
  
  public void setTime(long newTime) {
    if (newTime < time) {
      
    }
    time = newTime;
  }

  public void incrementTime(int millis) {
    setTime(time+millis); 
  }
  
}
