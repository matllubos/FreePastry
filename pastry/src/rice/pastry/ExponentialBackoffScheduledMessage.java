/*
 * Created on May 6, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry;

import java.util.Timer;
import java.util.TimerTask;

import rice.pastry.messaging.Message;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ExponentialBackoffScheduledMessage extends ScheduledMessage {
  boolean cancelled = false;
  EBTimerTask myTask;
  Timer timer;
  long initialPeriod;
  double expBase;
  int numTimes = 0;
  long lastTime = 0;

  /**
	 * @param node
	 * @param msg
	 * @param initialPeriod
	 * @param expBase
	 */
  public ExponentialBackoffScheduledMessage(PastryNode node, Message msg, Timer timer, long delay, long initialPeriod, double expBase) {
    super(node,msg);
    this.timer = timer;
    this.initialPeriod = initialPeriod;
    this.expBase = expBase;
    schedule(delay);
  }

  public ExponentialBackoffScheduledMessage(PastryNode node, Message msg, Timer timer, long initialDelay, double expBase) {
    super(node,msg);
    this.timer = timer;
    this.initialPeriod = initialDelay;
    this.expBase = expBase;
    schedule(initialDelay);
    numTimes=1;
  }

  
  private void schedule(long time) {
    //System.out.println("EB.schedule()");
    myTask = new EBTimerTask();
    timer.schedule(myTask,time);          
  }
  
  public boolean cancel() {
    //System.out.println("EB.cancel()");

    if (myTask!=null) {
      myTask.cancel();
      myTask = null;
    }
    boolean temp = cancelled;
    cancelled = true;
    return temp;
  }
  
  public void run() {
    //System.out.println("EB.run()");
    if (!cancelled) {
      if (myTask!=null) {
        lastTime = myTask.scheduledExecutionTime();
      }
      super.run();
      long time = (long)(initialPeriod * Math.pow(expBase,numTimes));
      schedule(time);
      numTimes++;
    }
  }
  
  public long scheduledExecutionTime() {
    return lastTime;        
  }
  
  class EBTimerTask extends TimerTask {
    public void run() {
      ExponentialBackoffScheduledMessage.this.run();
    }
  }
}
