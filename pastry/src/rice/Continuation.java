package rice;

/**
 * Asynchronously receives the result to a given method call, using
 * the command pattern.
 * 
 * Implementations of this class contain the remainder of a computation
 * which included an asynchronous method call.  When the result to the
 * call becomes available, the receiveResult method on this command
 * is called.
 *
 * @version $Id$
 */
public interface Continuation {

  /**
   * Called when a previously requested result is now availble.
   *
   * @param result The result of the command.
   */
  public void receiveResult(Object result);

  /**
   * Called when an execption occured as a result of the
   * previous command.
   *
   * @param result The exception which was caused.
   */
  public void receiveException(Exception result);

}
