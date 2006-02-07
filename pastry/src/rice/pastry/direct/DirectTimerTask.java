/*
 * Created on Nov 8, 2005
 */
package rice.pastry.direct;

import rice.pastry.ScheduledMessage;
import rice.selector.TimerTask;

public class DirectTimerTask extends ScheduledMessage {

  MessageDelivery md;
  
  DirectTimerTask(MessageDelivery md, long nextExecutionTime, int period, boolean fixed) {
    super(md.node,md.msg);
    this.md = md; 
    this.nextExecutionTime = nextExecutionTime;
    this.period = period;
    this.fixedRate = fixed;
  }
  
  DirectTimerTask(MessageDelivery md, long nextExecutionTime, int period) {
    this(md,nextExecutionTime,-1,false);
  }
  
  DirectTimerTask(MessageDelivery md, long nextExecutionTime) {
    this(md,nextExecutionTime,-1,false);
  }
  
  public void run() {
    md.deliver();
  }
  
  public String toString() {
    return "DirectTT for " + msg + " to "+md.node;
  }
  
}
