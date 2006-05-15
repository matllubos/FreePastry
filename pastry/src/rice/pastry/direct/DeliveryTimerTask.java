/*
 * Created on Feb 10, 2006
 */
package rice.pastry.direct;

import rice.selector.TimerTask;

public class DeliveryTimerTask extends TimerTask {
  Delivery md;
  
  DeliveryTimerTask(Delivery md, long nextExecutionTime, int period, boolean fixed) {
    this.md = md; 
    this.nextExecutionTime = nextExecutionTime;
    this.period = period;
    this.fixedRate = fixed;
  }
  
  DeliveryTimerTask(Delivery md, long nextExecutionTime, int period) {
    this(md,nextExecutionTime,-1,false);
  }
  
  DeliveryTimerTask(Delivery md, long nextExecutionTime) {
    this(md,nextExecutionTime,-1,false);
  }
  
  public void run() {
    md.deliver();
  }
  
  public String toString() {
    return "DeliveryTT for " + md;
  }
  

}
