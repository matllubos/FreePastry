
package rice;

/**
 * Asynchronously executes a processing function, and returns the result.  
 * Just like Runnable, but has a return value;
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public interface Executable {

  /**
   * Executes the potentially expensive task and returns the result.
   *
   * @param result The result of the command.
   */
  public Object execute();

}
