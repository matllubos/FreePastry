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
public class ProcessingRequest implements Runnable {
  Continuation c;
  Executable r;
  
  TimeSource timeSource;
  SelectorManager selectorManager;
  Logger logger;
  
  public ProcessingRequest(Executable r, Continuation c, LogManager logging, TimeSource timeSource, SelectorManager selectorManager ){
    this.r = r;
    this.c = c;
    
    logger = logging.getLogger(getClass(), null);
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
    if (logger.level <= Logger.FINER) logger.log("COUNT: Starting execution of " + this);
    try {
    long start = timeSource.currentTimeMillis();
      final Object result = r.execute();
      if (logger.level <= Logger.FINEST) logger.log("QT: " + (timeSource.currentTimeMillis() - start) + " " + r.toString());

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
    if (logger.level <= Logger.FINER) logger.log("COUNT: Done execution of " + this);      
  }
}

