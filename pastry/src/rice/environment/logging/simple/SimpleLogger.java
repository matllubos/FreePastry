/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging.simple;

import java.io.PrintStream;

import rice.environment.logging.Logger;
import rice.environment.time.TimeSource;

/**
 * This logger writes its name:time:message to the printstream provided, unless the 
 * priority is lower than the minimumPriority.
 * 
 * @author Jeff Hoye
 */
public class SimpleLogger implements Logger {

  /**
   * The name of this logger.
   */
  String loggerName;
  
  /**
   * The stream to print to.
   */
  SimpleLogManager slm;
  
  /**
   * The timesource.
   */
  TimeSource time;
  
  /**
   * The minimum priority to display.
   */
  int minPriority;
  
  /**
   * Constructor.
   * 
   * @param loggerName the name of this logger.
   * @param ps the stream to print to.
   * @param time the timesource.
   * @param minPriority the minimum priority to display.
   */
  public SimpleLogger(String loggerName, SimpleLogManager slm, TimeSource time, int minPriority) {
    this.loggerName = loggerName;
    this.slm = slm;
    this.time = time;
    this.minPriority = minPriority;
  }

  /**
   * Prints out loggerName:currentTime:message
   */
  public void log(int priority, String message) {
    if (priority >= minPriority) {
      slm.ps.println(slm.prefix+loggerName+":"+time.currentTimeMillis()+":"+message); 
    }
  }
}
