package rice;

/**
 * This class is a callback class which allows a class to return
 * results from methods at a later time.  The method should return
 * a CommandIdentifier which will be returned via the
 * receiveResult command.
 *
 * @version $Id$
 */
public interface ReceiveResultCommand  {

  /**
   * Called when a previously requested result is now availble.  The
   * identifier is the previously returned identifier for the
   * request.
   *
   * @param identifier The identifier returned when the request was made.
   * @param result The result of the command.
   */
  public void receiveResult(ResultIdentifier identifier, Object result);

}

  