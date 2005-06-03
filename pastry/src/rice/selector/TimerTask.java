/*
 * Created on Nov 17, 2004
 */
package rice.selector;

import rice.environment.time.TimeSource;
import rice.p2p.commonapi.CancellableTask;

/**
 * @author Jeff Hoye
 */
public abstract class TimerTask implements Comparable, CancellableTask {
  long nextExecutionTime;
  private boolean cancelled = false;

  /**
   * If period is positive, task will be rescheduled.
   */
  int period = -1;    
    
  
  public abstract void run();

  /**
   * Returns true if should re-insert.
   * @return
   */
  boolean execute(TimeSource ts) {
    if (cancelled) return false;
    run();
    // often cancelled in the execution
    if (cancelled) return false;
    if (period > 0) {
      nextExecutionTime = ts.currentTimeMillis()+period;
      return true;
    } else {
      return false;
    }
  }
  
  public boolean cancel() {
    if (cancelled) {
      return false;
    }
    cancelled = true;
    return true;
  }
  
  public long scheduledExecutionTime() {
    return nextExecutionTime; 
  }

  public int compareTo(Object arg0) {
    TimerTask tt = (TimerTask)arg0;
    if (tt == this) return 0;
//    return (int)(tt.nextExecutionTime-nextExecutionTime);
    int diff = (int)(nextExecutionTime-tt.nextExecutionTime);
    if (diff == 0) {
      return System.identityHashCode(this) < System.identityHashCode(tt) ? 1 : -1;
    }
    return diff;
  }

  public boolean isCancelled() {
    return cancelled;
  }
   
}
