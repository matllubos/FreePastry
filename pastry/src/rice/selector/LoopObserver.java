/*
 * Created on May 6, 2005
 */
package rice.selector;

/**
 * @author Jeff Hoye
 */
public interface LoopObserver {

  /**
   * If you want to hear about loops that took longer than 5 seconds, return 5000.
   * 
   * @return the minimum time (in millis) you are interested in hearing about.
   */
  int delayInterest();

  /**
   * @param loopTime the time it took to do a full loop in millis.
   */
  void loopTime(int loopTime);

}
