/*
 * Created on Feb 10, 2006
 */
package rice.pastry.direct;

import rice.selector.TimerTask;

public class DeliveryTimerTask extends TimerTask {
  Delivery md;
  
  DeliveryTimerTask(Delivery md, long nextExecutionTime, int period, boolean fixed, int seq) {
    this.md = md; 
    this.nextExecutionTime = nextExecutionTime;
    this.period = period;
    this.fixedRate = fixed;
    this.seq = seq;
  }
  
  DeliveryTimerTask(Delivery md, long nextExecutionTime, int period, int seq) {
    this(md,nextExecutionTime,-1,false, seq);
  }
  
  DeliveryTimerTask(Delivery md, long nextExecutionTime, int seq) {
    this(md,nextExecutionTime,-1,false, seq);
  }
  
  public void run() {
    md.deliver();
  }
  
  public String toString() {
    return "DeliveryTT for " + md;
  }
}
