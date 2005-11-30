/*
 * Created on Jun 28, 2005
 *
 */
package rice.environment.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;

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
  
  protected TimeSource time;
  protected PrintStream ps;
  protected String prefix;
  protected String dateFormat;
  
  /**
   * the "default" log level
   */
  int globalLogLevel;

  /**
   * If we only want package level granularity.
   */
  protected boolean packageOnly = true;
  
  protected boolean enabled;
  private PrintStream nullPrintStream;

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

    this.enabled = params.getBoolean("logging_enable");
    if (params.contains("logging_packageOnly")) {
      this.packageOnly = params.getBoolean("logging_packageOnly");
    }
    this.nullPrintStream = new PrintStream(new NullOutputStream());

    this.loggers = new Hashtable();
    this.globalLogLevel = parseVal("loglevel");

    params.addChangeListener(new ParameterChangeListener() {
	    public void parameterChange(String paramName, String newVal) {
	      if (paramName.equals("logging_enable")) {
	        enabled = Boolean.getBoolean(newVal);
	      } else if (paramName.equals("loglevel")) {            
            synchronized(this) {
              // iterate over all loggers, if they are default loggers,
              // set the level
              globalLogLevel = parseVal(paramName);
              Iterator i = loggers.values().iterator();
              while(i.hasNext()) {
                HeirarchyLogger hl = (HeirarchyLogger)i.next();
                if (hl.useDefault) {
                  hl.level = globalLogLevel; 
                }
              }
            } // synchronized
	      } else if (paramName.endsWith("_loglevel")) {
            if ((newVal == null) || (newVal.equals(""))) {
              // parameter "removed" 
              // a) set the logger to use defaultlevel, 
              // b) set the level 
              if (loggers.contains(paramName)) { // perhaps we haven't even created such a logger yet
                HeirarchyLogger hl = (HeirarchyLogger)loggers.get(paramName);
                hl.useDefault = true;
                hl.level = globalLogLevel;
              }
            } else {
              // a) set the logger to not use the defaultlevel, 
              // b) set the level 
              if (loggers.contains(paramName)) { // perhaps we haven't even created such a logger yet
                HeirarchyLogger hl = (HeirarchyLogger)loggers.get(paramName);
                hl.useDefault = false;
                hl.level = parseVal(paramName);
              }
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
    // first we want to get the logger name
    String loggerName;
    String className = clazz.getName();
    String[] parts = null;
    if (packageOnly) {
      // builds loggername = just the package
      parts = className.split("\\.");
      loggerName = parts[0];
      // the "-1" cuts off the class part of the full package name
      for (int curPart = 1; curPart < parts.length-1; curPart++) {
        loggerName+="."+parts[curPart];   
      }            
    } else {
      // loggerName is the className
      loggerName = className;
    }
    
    if (instance != null) {
      loggerName = loggerName+"$"+instance;
    }
    
    // see if this logger exists
    if (loggers.contains(loggerName)) {
      return (Logger)loggers.get(loggerName);
    }
    
    // OPTIMIZATION: parts is only built if needed, and it may have been needed earlier, or it may not have been
    if (parts == null) parts = className.split("\\.");
    
    // at this point we know we are going to have to build a logger.  We need to
    // figure out what level to initiate it with.
    
    
    
    int level = globalLogLevel;
    boolean useDefault = true;    
    
    String baseStr;
    
    // Ex: if clazz.getName() == rice.pastry.socket.PingManager, try:
    // 1) rice.pastry.socket.PingManager
    // 2) rice.pastry.socket
    // 3) rice.pastry
    // 4) rice
    
    int lastPart = parts.length;
    
    // this strips off the ClassName from the package
    if (packageOnly) lastPart--;
    
    // numParts is how much of the prefix we want to use, start with the full name    
    for (int numParts = lastPart; numParts >= 0; numParts--) {     
      
      // build baseStr which is the prefix of the clazz up to numParts
      baseStr = parts[0];
      for (int curPart = 1; curPart < numParts; curPart++) {
        baseStr+="."+parts[curPart];   
      }
      
      
      // try to find one matching a specific instance
      if (instance != null) {            
        String searchString = baseStr+":"+instance+"_loglevel";
        // see if this logger should exist
        if (params.contains(searchString)) {
          level = parseVal(searchString);
          useDefault = false;
          break;
        }
      }
      
      String searchString = baseStr+"_loglevel";
      if (params.contains(searchString)) {
        level = parseVal(searchString);
        useDefault = false;
        break;
      }
    }
    
    
    // at this point, we didn't find anything that matched, so now return a logger
    // that has the established level
    Logger logger = constructLogger(loggerName, level, useDefault);     
    loggers.put(clazz, logger);
    return logger;
  }

  protected abstract Logger constructLogger(String clazz, int level, boolean useDefault);

  public TimeSource getTimeSource() {
    return time;
  }

  public PrintStream getPrintStream() {
    if (enabled) {
      return ps;
    } else {
      return nullPrintStream;
    }
  }
  
  public String getPrefix() {
    return prefix;
  }
  
  private static class NullOutputStream extends OutputStream {
    public void write(int arg0) throws IOException {
      // do nothing
    }
    public void write(byte[] buf) throws IOException {
      // do nothing
    }
    public void write(byte[] buf, int a, int b) throws IOException {
      // do nothing
    }
  }
}
