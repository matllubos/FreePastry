/*
 * Created on Aug 9, 2005
 */
package rice.environment.processing.simple;

import rice.*;
import rice.environment.logging.*;
import rice.environment.time.TimeSource;
import rice.pastry.dist.DistPastryNode;
import rice.selector.SelectorManager;

/**
 * @author Jeff Hoye
 */
public class ProcessingRequest {
  Continuation c;
  Executable r;
  
  LogManager logManager;
  TimeSource timeSource;
  SelectorManager selectorManager;
  
  public ProcessingRequest(Executable r, Continuation c, LogManager logging, TimeSource timeSource, SelectorManager selectorManager ){
    this.r = r;
    this.c = c;
    
    this.logManager = logging;
    this.timeSource = timeSource;
    this.selectorManager = selectorManager;
  }
  
  public void returnResult(Object o) {
    c.receiveResult(o); 
  }
  
  public void returnError(Exception e) {
    c.receiveException(e); 
  }
  
  public void run() {
    logManager.getLogger(DistPastryNode.class, null).log(Logger.FINER,
      "COUNT: Starting execution of " + this);
    try {
    long start = timeSource.currentTimeMillis();
      final Object result = r.execute();
      logManager.getLogger(getClass(), null).log(Logger.FINEST,"QT: " + (timeSource.currentTimeMillis() - start) + " " + r.toString());

      selectorManager.invoke(new Runnable() {
        public void run() {
          returnResult(result);
        }
        public String toString(){
          return "return ProcessingRequest for " + r + " to " + c;
        }
      });
    } catch (final Exception e) {
      selectorManager.invoke(new Runnable() {
        public void run() {
          returnError(e);
        }
        public String toString(){
          return "return ProcessingRequest for " + r + " to " + c;
        }
      });
    }
    logManager.getLogger(DistPastryNode.class, null).log(Logger.FINER,
      "COUNT: Done execution of " + this);      
  }
}

