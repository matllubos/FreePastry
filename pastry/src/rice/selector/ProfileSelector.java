/*
 * Created on Jul 27, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.selector;

import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ProfileSelector extends SelectorManager {
  public static boolean useHeartbeat = true;
  int HEART_BEAT_INTERVAL = 60000;
  long lastHeartBeat = 0;

  public static boolean recordStats = false;

  // *********************** debugging statistics ****************
  /**
   * Records how long it takes to receive each type of message.
   */
  public Hashtable stats = new Hashtable();
  
  public void addStat(String s, long time) {
    if (!recordStats) return;
    Stat st = (Stat)stats.get(s);
    if (st == null) {
      st = new Stat(s);
      stats.put(s,st);
    }
    st.addTime(time);
  }

  public void printStats() {
    if (!recordStats) return;
    synchronized(stats) {
      Enumeration e = stats.elements();
      while(e.hasMoreElements()) {
        Stat s = (Stat)e.nextElement(); 
        System.out.println("  "+s);
      }
    }
  }

  /**
   * A statistic as to how long user code is taking to process a paritcular message.
   * 
   * @author Jeff Hoye
   */
  class Stat {
    int num = 0;
    String name = null;
    long totalTime = 0;
    long maxTime = 0;
    
    public Stat(String name) {
      this.name = name;
    }
    
    public void addTime(long t) {
      num++;
      totalTime+=t;
      if (t > maxTime) {
        maxTime = t;  
      }
    }
    
    public String toString() {
      long avgTime = totalTime/num;
      return name+" num:"+num+" total:"+totalTime+" maxTime:"+maxTime+" avg:"+avgTime;
    }
  }



  protected void onLoop() {
    if (!useHeartbeat) return;  
    long curTime = System.currentTimeMillis();
    if ((curTime - lastHeartBeat) > HEART_BEAT_INTERVAL) {
      System.out.println("selector heartbeat "+new Date());
      lastHeartBeat = curTime;          
    }
    printStats();
  }
}
