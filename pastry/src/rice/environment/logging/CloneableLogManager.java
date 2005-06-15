/*
 * Created on Jun 15, 2005
 */
package rice.environment.logging;

/**
 * If you implement this interface, then your log manager can be cloned.
 * This is usually used so different nodes can have different log managers.
 * 
 * The simple log manager uses the @param detail arg as a prefix to each
 * line corresponding to that logger.
 * 
 * Another example may be logging to multiple files, but using the "detail"
 * in the filename to indicate which node the log corresponds to.
 * 
 * A PastryNodeFactory should clone the LogManager for each node if the 
 * pastry_factory_multipleNodes parameter is set to true.
 * 
 * 
 * @author Jeff Hoye
 */
public interface CloneableLogManager extends LogManager {
  /**
   * Return a new LogManager with identical parameters except that 
   * there is an indication of detail in each line, or filename if 
   * seperated by files.
   * 
   * @param detail usually will be a nodeid
   * @return a new LogManager 
   */
  LogManager clone(String detail);
}
