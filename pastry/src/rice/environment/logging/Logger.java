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
   */
  public static final int INFO = 800;
  
  /**
   * CONFIG is a message level for static configuration messages.
   */
  public static final int CONFIG = 700;
  
  /**
   * FINE is a message level providing tracing information.
   */
  public static final int FINE = 500;
  
  /**
   * FINER indicates a fairly detailed tracing message. 
   */
  public static final int FINER = 400;
  
  /**
   * FINEST indicates a highly detailed tracing message. 
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
}
