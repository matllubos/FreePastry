/*
 * Created on Jun 28, 2005
 *
 */
package rice.environment.logging;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Hashtable;

import javax.swing.text.DateFormatter;

import rice.environment.logging.simple.SimpleLogger;
import rice.environment.params.ParameterChangeListener;
import rice.environment.params.Parameters;
import rice.environment.time.TimeSource;

/**
 * @author jstewart
 *
 */
public abstract class AbstractLogManager implements LogManager {
  /**
   * Hashtable of loggers stored by full.class.name[instance]
   */
  protected Hashtable loggers;
  protected Parameters params;
  
  protected Logger defaultLogger;
  
  protected TimeSource time;
  protected PrintStream ps;
  protected String prefix;
  protected String dateFormat;

  public DateFormatter dateFormatter;
  
  protected AbstractLogManager(PrintStream stream, TimeSource timeSource, Parameters params, String prefix, String df) {
    this.ps = stream;
    this.time = timeSource;
    this.params = params;
    this.prefix = prefix;
    this.dateFormat = df;
    if (this.dateFormat == null) {
      this.dateFormat = params.getString("logging_date_format");
    }
    if (this.dateFormat != null && !this.dateFormat.equals("")) {      
      dateFormatter = new DateFormatter(new SimpleDateFormat(this.dateFormat));
//      System.out.println("DateFormat "+this.dateFormat);
    }

    this.loggers = new Hashtable();
    this.defaultLogger = constructLogger("",parseVal("loglevel"));

    params.addChangeListener(new ParameterChangeListener() {
	    public void parameterChange(String paramName, String newVal) {
	      if (paramName.equals("loglevel")) {
	        ((LogLevelSetter)defaultLogger).setMinLogLevel(parseVal(paramName));
	      } else if (paramName.endsWith("_loglevel")) {
	        if (loggers.contains(paramName)) {
	          ((LogLevelSetter)loggers.get(paramName)).setMinLogLevel(parseVal(paramName));
	        }
	      }
	    }
    });
  }

  protected int parseVal(String key) {
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
      Logger logger = constructLogger(clazz, parseVal(searchString));     
      loggers.put(clazz, logger);
      return logger;
    }
    return null;
  }
  
  protected abstract Logger constructLogger(String clazz, int level);

  public TimeSource getTimeSource() {
    return time;
  }

  public PrintStream getPrintStream() {
    return ps;
  }
  
  public String getPrefix() {
    return prefix;
  }
}
