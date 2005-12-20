/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging.simple;

import java.io.PrintStream;
import java.util.Hashtable;

import rice.environment.logging.*;
import rice.environment.logging.LogManager;
import rice.environment.params.ParameterChangeListener;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;
import rice.environment.time.simple.SimpleTimeSource;

/**
 * This class creates loggers that log to a specified PrintStream System.out by default.
 * 
 * @author Jeff Hoye
 */
public class SimpleLogManager extends AbstractLogManager implements CloneableLogManager {

  /**
   * Constructor.
   * 
   * @param stream the stream to write to
   * @param timeSource the timesource to get times from
   * @param minPriority the minimum priority to print
   */
  public SimpleLogManager(PrintStream stream, TimeSource timeSource, Parameters params) {
    this(stream, timeSource, params, "", null);  
  }
  
  public SimpleLogManager(PrintStream stream, TimeSource timeSource, Parameters params, String prefix, String dateFormat) {
    super(stream, timeSource, params, prefix, dateFormat);
  }
  
  public PrintStream getPrintStream() {
    return ps;
  }
  
  public Parameters getParameters() {
    return params;
  }
  
  public TimeSource getTimeSource() {
    return time;
  }
  
  
  /**
   * Convienience constructor.
   * 
   * Defauts to System.out as the stream, and SimpleTimeSource as the timesource.
   * 
   * @param minPriority the minimum priority to print.
   */  
  public SimpleLogManager(Parameters params) {
    this(null, new SimpleTimeSource(), params);
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to SimpleTimeSource as the timesource.
   * 
   * @param stream the stream to write to
   * @param minPriority the minimum priority to print
   */
  public SimpleLogManager(PrintStream stream, Parameters params) {
    this(stream, new SimpleTimeSource(), params);
  }
  
  /**
   * Convienience constructor.
   * 
   * Defauts to System.out as the stream.
   * 
   * @param timeSource the timesource to get times from
   * @param minPriority the minimum priority to print
   */
  public SimpleLogManager(TimeSource timeSource, Parameters params) {
    this(null, timeSource, params);
  }
  
  protected Logger constructLogger(String clazz, int level, boolean useDefault) {
    return new SimpleLogger(clazz,this,level, useDefault);
  }

  /* (non-Javadoc)
   * @see rice.environment.logging.CloneableLogManager#clone(java.lang.String)
   */
  public LogManager clone(String detail) {
    return new SimpleLogManager(ps, time, params, detail, dateFormat);
  }
  
}
