package rice;

/**
 * This class is a callback class which allows a class to return
 * results from methods at a later time.
 *
 * @version $Id$
 */
public interface ReceiveResultCommand  {

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

  