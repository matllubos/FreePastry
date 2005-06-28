/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging;

/**
 * The Logger is a simplified interface of the java.util.logging.Logger.  It is 
 * envisioned that one could implement this interface using java.util.logging, but
 * that many times this interface is overkill. 
 * 
 * @author Jeff Hoye
 */
public interface Logger {

  // These are suggested base level priorities.
  
  /**
   * SEVERE is a message level indicating a serious failure.
   */
  public static final int SEVERE = 1000; 
  
  /**
   * WARNING is a message level indicating a potential problem.
   */
  public static final int WARNING = 900;
  
  /**
   * INFO is a message level for informational messages.
   * Things that only happen once per node creation.
   */
  public static final int INFO = 800;
  
  /**
   * CONFIG is a message level for static configuration messages.
   */
  public static final int CONFIG = 700;
  
  /**
   * FINE is a message level providing tracing information.
   * Things that get logged once per specific message.
   */
  public static final int FINE = 500;
  
  /**
   * FINER indicates a fairly detailed tracing message. 
   * Things that get logged once per general message.
   */
  public static final int FINER = 400;
  
  /**
   * FINEST indicates a highly detailed tracing message. 
   * Things that happen more than once per general message.
   */
  public static final int FINEST = 300;

  /**
   * ALL indicates that all messages should be logged.
   */
  public static final int ALL = Integer.MIN_VALUE;
  
  /**
   * OFF is a special level that can be used to turn off logging. 
   */
  public static final int OFF = Integer.MAX_VALUE;

  /**
   * Prints the message if the priority is equal to or higher than the minimum priority.
   * @param priority the priority of this log message
   * @param message the message to print
   */
  public void log(int priority, String message);

  /**
   * Prints the stack trace of the exception.  If you only want to print the 
   * exception's string, use the log() method.
   * 
   * This is necessary because Exception doesn't have a convienient way of printing the stack trace as a string.
   * 
   * @param priority the priority of this log message
   * @param exception the exception to print
   */
  public void logException(int priority, String message, Throwable exception);
}
