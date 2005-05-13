/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging.simple;

import java.io.PrintStream;
import java.util.Hashtable;

import rice.environment.logging.*;
import rice.environment.logging.LogManager;
import rice.environment.time.TimeSource;
import rice.environment.time.simple.SimpleTimeSource;

/**
 * This class creates loggers that log to a specified PrintStream System.out by default.
 * 
 * @author Jeff Hoye
 */
public class SimpleLogManager implements LogManager {

  /**
   * Hashtable of loggers stored by full.class.name[instance]
   */
  Hashtable loggers;
  PrintStream ps;
  TimeSource time;
  int minPriority;

  /**
   * Constructor.
   * 
   * @param stream the stream to write to
   * @param timeSource the timesource to get times from
   * @param minPriority the minimum priority to print
   */
  public SimpleLogManager(PrintStream stream, TimeSource timeSource, int minPriority) {
    this.ps = stream;
    this.time = timeSource;
    this.minPriority = minPriority;
    this.loggers = new Hashtable();
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to System.out as the stream, and SimpleTimeSource as the timesource.
   * 
   * @param minPriority the minimum priority to print.
   */  
  public SimpleLogManager(int minPriority) {
    this(System.out, new SimpleTimeSource(), minPriority);
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to SimpleTimeSource as the timesource.
   * 
   * @param stream the stream to write to
   * @param minPriority the minimum priority to print
   */
  public SimpleLogManager(PrintStream stream, int minPriority) {
    this(stream, new SimpleTimeSource(), minPriority);
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to System.out as the stream.
   * 
   * @param timeSource the timesource to get times from
   * @param minPriority the minimum priority to print
   */
  public SimpleLogManager(TimeSource timeSource, int minPriority) {
    this(System.out, timeSource, minPriority);
  }
  
  /**
   * 
   */
  public Logger getLogger(Class clazz, String instance) {
    if (instance == null) instance = "";
    String loggerName = clazz.getName()+"["+instance+"]";
    Logger logger = (Logger)loggers.get(loggerName);
    if (logger == null) {
      logger = new SimpleLogger(loggerName, ps, time, minPriority);
      loggers.put(loggerName, logger);
    }
    return logger;
  }
  
}
