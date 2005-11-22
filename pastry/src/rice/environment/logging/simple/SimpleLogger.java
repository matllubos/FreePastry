/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging.simple;

import java.io.PrintStream;
import java.text.*;
import java.util.Date;

import javax.swing.text.DateFormatter;

import rice.environment.logging.*;
import rice.environment.time.TimeSource;

/**
 * This logger writes its name:time:message to the printstream provided, unless the 
 * priority is lower than the minimumPriority.
 * 
 * @author Jeff Hoye
 */
public class SimpleLogger extends HeirarchyLogger {

  /**
   * The name of this logger.
   */
  String loggerName;
  
  /**
   * The stream to print to.
   */
  AbstractLogManager alm;
  
  /**
   * Constructor.
   * 
   * @param loggerName the name of this logger.
   * @param ps the stream to print to.
   * @param time the timesource.
   * @param minPriority the minimum priority to display.
   */
  public SimpleLogger(String loggerName, AbstractLogManager alm, int level, boolean useDefault) {
    this.loggerName = loggerName;
    this.alm = alm;
    this.level = level;
    this.useDefault = useDefault;
  }

  /**
   * Prints out loggerName:currentTime:message
   */
  public void log(String message) {
    synchronized(alm) {
      String dateString = ""+alm.getTimeSource().currentTimeMillis();
      if (alm.dateFormatter != null) {
        try {
          Date date = new Date(alm.getTimeSource().currentTimeMillis());            
          dateString = alm.dateFormatter.valueToString(date);
        } catch (ParseException pe) {
          pe.printStackTrace();
        }
      }

      alm.getPrintStream().println(alm.getPrefix()+":"+loggerName+":"+dateString+":"+message);
    }
  }
  
  /**
   * Prints out logger:currentTime:exception.stackTrace();
   */
  public void logException(String message, Throwable exception) {
    synchronized(alm) {
      String dateString = ""+alm.getTimeSource().currentTimeMillis();
      if (alm.dateFormatter != null) {
        try {
          Date date = new Date(alm.getTimeSource().currentTimeMillis());            
          dateString = alm.dateFormatter.valueToString(date);
        } catch (ParseException pe) {
          pe.printStackTrace();
        }
      }
      
      alm.getPrintStream().print(alm.getPrefix()+":"+loggerName+":"+dateString+":"+message);
      exception.printStackTrace(alm.getPrintStream());
    }
  }
}
