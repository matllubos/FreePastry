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
 *
 * @author Alan Mislove
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

  /**
   * This class is a Continuation provided for simplicity which
   * passes any errors up to the parent Continuation which it
   * is constructed with.  Subclasses should implement the
   * receiveResult() method with the appropriate behavior.
   */
  public static abstract class StandardContinuation implements Continuation {

    /**
     * The parent continuation
     */
    protected Continuation parent;

    /**
     * Constructor which takes in the parent continuation
     * for this continuation.
     *
     * @param continuation The parent of this continuation
     */
    public StandardContinuation(Continuation continuation) {
      parent = continuation;
    }

    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply calls the parent continuation's
     * receiveResult() method.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      parent.receiveException(result);
    }
  }

  /**
   * This class is a Continuation provided for simplicity which
   * listens for any errors and ignores any success values.  This
   * Continuation is provided for testing convience only and should *NOT* be
   * used in production environment.
   */
  public static class ListenerContinuation implements Continuation {

    /**
     * The name of this continuation
     */
    protected String name;
    
    /**
     * Constructor which takes in a name
     *
     * @param name A name which uniquely identifies this contiuation for
     *   debugging purposes
     */
    public ListenerContinuation(String name) {
      this.name = name;
    }
    
    /**
     * Called when a previously requested result is now availble. Does
     * absolutely nothing.
     *
     * @param result The result
     */
    public void receiveResult(Object result) {
    }

    /**
     * Called when an execption occured as a result of the
     * previous command.  Simply prints an error message to the screen.
     *
     * @param result The exception which was caused.
     */
    public void receiveException(Exception result) {
      System.out.println("ERROR - Received exception " + result + " during task " + name);
    }
  }
  
}
