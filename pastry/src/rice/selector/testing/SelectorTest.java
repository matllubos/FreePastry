/*
 * Created on Jul 27, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.selector.testing;

import rice.selector.SelectorManager;
import rice.selector.Timer;
import rice.selector.TimerTask;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class SelectorTest {
    

	public static void main(String[] args) {
    SelectorManager sman = SelectorManager.getSelectorManager();
    Timer timer = sman.getTimer();
    scheduleRepeated(timer,sman);
    for(int i = 0; i < 10; i++) {
      scheduleStuff(timer,sman);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ie) {
      }
    }
	}

  public static void scheduleRepeated(Timer timer, SelectorManager sman) {
    final long t1Start = System.currentTimeMillis();
    final int t1Delay = 3000;
    timer.schedule(new TimerTask() {
      long lastTime = t1Start;
      public void run() {
        long curTime = System.currentTimeMillis();
        long delay = curTime-lastTime;
        lastTime = curTime;
        if ((delay-t1Delay) > 100)
          System.out.println("Scheduled many times for delay "+t1Delay+" actual delay "+delay);
      }
    },t1Delay, t1Delay);
  }  

  public static void scheduleStuff(Timer timer, SelectorManager sman) {
    final long t1Start = System.currentTimeMillis();
    final int t1Delay = 5000;
    timer.schedule(new TimerTask() {
      public void run() {
        long curTime = System.currentTimeMillis();
        curTime-=t1Start;
        if ((curTime-t1Delay) > 100)
          System.out.println("Scheduled once for delay "+t1Delay+" actual delay "+curTime);
      }
    },t1Delay);
    //if (true) return;
    final long i1Start = System.currentTimeMillis();
    sman.invoke(new Runnable() {
      public void run() {
        long curTime = System.currentTimeMillis();
        curTime-=i1Start;
        if (curTime > 100)
          System.out.println("invoked after "+curTime+" millis.");
      }
    });    
  }
}
