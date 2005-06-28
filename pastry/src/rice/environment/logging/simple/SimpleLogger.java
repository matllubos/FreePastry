/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging.simple;

import java.io.PrintStream;

import rice.environment.logging.AbstractLogManager;
import rice.environment.logging.LogLevelSetter;
import rice.environment.logging.Logger;
import rice.environment.time.TimeSource;

/**
 * This logger writes its name:time:message to the printstream provided, unless the 
 * priority is lower than the minimumPriority.
 * 
 * @author Jeff Hoye
 */
public class SimpleLogger implements Logger, LogLevelSetter {

  /**
   * The name of this logger.
   */
  String loggerName;
  
  /**
   * The stream to print to.
   */
  AbstractLogManager alm;
  
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
  public SimpleLogger(String loggerName, AbstractLogManager alm, int minPriority) {
    this.loggerName = loggerName;
    this.alm = alm;
    this.minPriority = minPriority;
  }

  /**
   * Prints out loggerName:currentTime:message
   */
  public void log(int priority, String message) {
    if (priority >= minPriority) {
      synchronized(alm) {
        alm.getPrintStream().println(alm.getPrefix()+loggerName+":"+alm.getTimeSource().currentTimeMillis()+":"+message);
      }
    }
  }
  
  /**
   * Prints out logger:currentTime:exception.stackTrace();
   */
  public void logException(int priority, String message, Throwable exception) {
    if (priority >= minPriority) {
      synchronized(alm) {
        alm.getPrintStream().print(alm.getPrefix()+loggerName+":"+alm.getTimeSource().currentTimeMillis()+":"+message);
        exception.printStackTrace(alm.getPrintStream());
      }
    }
  }

  public void setMinLogLevel(int logLevel) {
    minPriority = logLevel;
  }

  public int getMinLogLevel() {
    return minPriority;
  }
}
