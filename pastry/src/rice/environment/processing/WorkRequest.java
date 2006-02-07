/*
 * Created on Aug 16, 2005
 */
package rice.environment.processing;

import rice.Continuation;
import rice.selector.SelectorManager;

/**
 * Extend this class and implement doWork() if you need to do blocking disk IO.
 * 
 * This is primarily used by Persistence.
 * 
 * @author Jeff Hoye
 */
public abstract class WorkRequest implements Runnable {
  private Continuation c;
  private SelectorManager selectorManager;
  
  public WorkRequest(Continuation c, SelectorManager sm){
    this.c = c;
    this.selectorManager = sm;
  }
  
  public WorkRequest(){
    /* do nothing */
  }
  
  public void returnResult(Object o) {
    c.receiveResult(o); 
  }
  
  public void returnError(Exception e) {
    c.receiveException(e); 
  }
  
  public void run() {
    try {
     // long start = environment.getTimeSource().currentTimeMillis();
      final Object result = doWork();
     // System.outt.println("PT: " + (environment.getTimeSource().currentTimeMillis() - start) + " " + toString());
      selectorManager.invoke(new Runnable() {
        public void run() {
          returnResult(result);
        }
        
        public String toString() {
          return "invc result of " + c;
        }
      });
    } catch (final Exception e) {
      selectorManager.invoke(new Runnable() {
        public void run() {
          returnError(e);
        }
        
        public String toString() {
          return "invc error of " + c;
        }
      });
    }
  }
  
  public abstract Object doWork() throws Exception;
}
