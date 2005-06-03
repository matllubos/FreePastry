/*
 * Created on Jul 27, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.selector.testing;

import java.io.IOException;

import rice.environment.Environment;
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
  public static boolean logAll = true;
  public static boolean logIssues = true;
  public static Environment environment;
  
	public static void main(String[] args) throws IOException {
    environment = new Environment();
    
    System.out.println("hello world <selector test>");
    SelectorManager sman = new SelectorManager(false, environment.getTimeSource());
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
    final long t1Start = environment.getTimeSource().currentTimeMillis();
    final int t1Delay = 3000;
    timer.schedule(new TimerTask() {
      long lastTime = t1Start;
      public void run() {
        long curTime = environment.getTimeSource().currentTimeMillis();
        long delay = curTime-lastTime;
        lastTime = curTime;
        if ((logAll == true) || (delay-t1Delay) > 100)
          System.out.println("Scheduled many times for delay "+t1Delay+" actual delay "+delay);
      }
    },t1Delay, t1Delay);
  }  

  public static void scheduleStuff(Timer timer, SelectorManager sman) {
    final long t1Start = environment.getTimeSource().currentTimeMillis();
    final int t1Delay = 5000;
    timer.schedule(new TimerTask() {
      public void run() {
        long curTime = environment.getTimeSource().currentTimeMillis();
        curTime-=t1Start;
        if ((logAll) ||(logIssues && (curTime-t1Delay) > 100))
          System.out.println("Scheduled once for delay "+t1Delay+" actual delay "+curTime);
      }
    },t1Delay);
    //if (true) return;
    final long i1Start = environment.getTimeSource().currentTimeMillis();
    sman.invoke(new Runnable() {
      public void run() {
        long curTime = environment.getTimeSource().currentTimeMillis();
        curTime-=i1Start;
        if ((logAll) || (logIssues && (curTime > 100)))
          System.out.println("invoked after "+curTime+" millis.");
      }
    });    
  }
}
