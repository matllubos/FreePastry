/*
 * Created on Apr 6, 2005
 */
package rice.environment.logging;

/**
 * Factory interface to generate loggers.
 * 
 * @author Jeff Hoye
 */
public interface LogManager {
  /**
   * Returns the Logger matching the paramerters, constructs a new one if an 
   * appropriate one hasn't yet been constructed.
   * 
   * @param clazz the Class associated with this logger.
   * @param instance the instancename associated with this logger.
   * @return the logger.
   */
  public Logger getLogger(Class clazz, String instance);
}
