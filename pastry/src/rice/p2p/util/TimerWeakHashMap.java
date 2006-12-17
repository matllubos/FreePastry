/*
 * Created on Jun 9, 2006
 */
package rice.p2p.util;

import java.util.WeakHashMap;

import rice.selector.*;

/**
 * Weak hash map that holds hard link to keys for a minimum time.
 * 
 * @author Jeff Hoye
 */
public class TimerWeakHashMap extends WeakHashMap {

  int defaultDelay;
  Timer timer;
  
  public TimerWeakHashMap(Timer t, int delay) {
    this.defaultDelay = delay;
    timer = t;
  }
  
  public static class HardLinkTimerTask extends TimerTask {
    Object hardLink;
    public HardLinkTimerTask(Object hardLink) {
      this.hardLink = hardLink;
    }
    public void run() {
      // do nothing, just expire, taking the hard link away with you
    }
  }

  
  public Object put(Object key, Object val) {
    refresh(key);
    return super.put(key, val);
  }

  public void refresh(Object key) {
    refresh(key, defaultDelay);
  }
  
  public void refresh(Object key, int delay) {      
    timer.schedule(
        new HardLinkTimerTask(key), delay);
  }
  
  
}
