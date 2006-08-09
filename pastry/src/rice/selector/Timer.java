/*
 * Created on Nov 17, 2004
 */
package rice.selector;


/**
 * @author Jeff Hoye
 */
public interface Timer {
  void scheduleAtFixedRate(TimerTask task, long delay, long period);
  void schedule(TimerTask task, long delay);
  void schedule(TimerTask task, long delay, long period);
  void schedule(TimerTask dtt);
}
