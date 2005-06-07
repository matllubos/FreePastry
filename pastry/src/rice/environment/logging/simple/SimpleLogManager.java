/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging.simple;

import java.io.PrintStream;
import java.util.Hashtable;

import rice.environment.logging.*;
import rice.environment.logging.LogManager;
import rice.environment.params.Parameters;
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
  Parameters params;

  Logger defaultLogger;
  
  /**
   * Constructor.
   * 
   * @param stream the stream to write to
   * @param timeSource the timesource to get times from
   * @param minPriority the minimum priority to print
   */
  public SimpleLogManager(PrintStream stream, TimeSource timeSource, Parameters params) {
    this.ps = stream;
    this.time = timeSource;
    this.params = params;
    this.loggers = new Hashtable();
    this.defaultLogger = new SimpleLogger("",this,time,parseVal("loglevel"));
  }
  
  public void setPrintStream(PrintStream stream) {
    this.ps = stream; 
  }
  
  private int parseVal(String key) {
    try {
      return params.getInt(key);
    } catch (NumberFormatException nfe) {
      String val = params.getString(key);

      if (val.equalsIgnoreCase("ALL")) return Logger.ALL;
      if (val.equalsIgnoreCase("OFF")) return Logger.OFF;
      if (val.equalsIgnoreCase("SEVERE")) return Logger.SEVERE; 
      if (val.equalsIgnoreCase("WARNING")) return Logger.WARNING; 
      if (val.equalsIgnoreCase("INFO")) return Logger.INFO; 
      if (val.equalsIgnoreCase("CONFIG")) return Logger.CONFIG; 
      if (val.equalsIgnoreCase("FINE")) return Logger.FINE; 
      if (val.equalsIgnoreCase("FINER")) return Logger.FINER; 
      if (val.equalsIgnoreCase("FINEST")) return Logger.FINEST; 
      throw new InvalidLogLevelException(key,val);
    }
  }
  
  
  /**
   * Convienience constructor.
   * 
   * Defauts to System.out as the stream, and SimpleTimeSource as the timesource.
   * 
   * @param minPriority the minimum priority to print.
   */  
  public SimpleLogManager(Parameters params) {
    this(System.out, new SimpleTimeSource(), params);
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
    this(System.out, timeSource, params);
  }
  
  /**
   * 
   */
  public Logger getLogger(Class clazz, String instance) {
    Logger temp; 
    String baseStr;
    String className = clazz.getName();
    String[] parts = className.split("\\.");
    
    // Ex: if clazz.getName() == rice.pastry.socket.PingManager, try:
    // 1) rice.pastry.socket.PingManager
    // 2) rice.pastry.socket
    // 3) rice.pastry
    // 4) rice
    
    // numParts is how much of the prefix we want to use, start with the full name    
    for (int numParts = parts.length; numParts >= 0; numParts--) {     
      // build baseStr which is the prefix of the clazz up to numParts
      baseStr = parts[0];
      for (int curPart = 1; curPart < numParts; curPart++) {
        baseStr+="."+parts[curPart];   
      }
      
      // try to find one matching a specific instance
      if (instance != null) {            
        temp = getLoggerHelper(baseStr+":"+instance);
        if (temp != null) return temp;
      }
      
      // try to find one without the instance
      temp = getLoggerHelper(baseStr);
      if (temp != null) return temp;
    }
    return defaultLogger;
  }

  /**
   * Searches the loggers HT for the searchString, then searches
   * the params for the search string.
   * 
   * @param clazz
   * @param instance
   * @return
   */
  private Logger getLoggerHelper(String clazz) {
    String searchString = clazz+"_loglevel";
    
    // see if this logger exists
    if (loggers.contains(searchString)) {
      return (Logger)loggers.get(searchString);
    }
    
    // see if this logger should exist
    if (params.contains(searchString)) {
      Logger logger = new SimpleLogger(clazz,this,time,parseVal(searchString));      
      loggers.put(clazz, logger);
      return logger;
    }
    return null;
  }
  
}
