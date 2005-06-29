
package rice.p2p.past.messaging;

import rice.*;
import rice.environment.Environment;
import rice.p2p.commonapi.*;
import rice.p2p.past.*;

/**
 * @(#) ContinuationMessage.java
 *
 * This class the abstraction of a message used internally by Past which serves
 * as a continuation (for receiving the results of an operation).
 *
 * @version $Id$
 *
 * @author Alan Mislove
 * @author Ansley Post
 * @author Peter Druschel
 */
public abstract class ContinuationMessage extends PastMessage implements Continuation {

  static final long serialVersionUID = 1321112527034107161L; 
  
  // the response data
  protected Object response;

  // the response exception, if one is thrown
  protected Exception exception;

  /**
    * Constructor which takes a unique integer Id, as well as the
   * data to be stored
   *
   * @param uid The unique id
   * @param source The source handle
   * @param dest The destination address
   */
  protected ContinuationMessage(int uid, NodeHandle source, Id dest) {
    super(uid, source, dest); 
  }

  /**
    * Method which builds a response for this message, using the provided
   * object as a result.
   *
   * @param o The object argument
   */
  public void receiveResult(Object o) {
    setResponse();
    response = o;
  }

  /**
    * Method which builds a response for this message, using the provided
   * exception, which was thrown
   *
   * @param e The exception argument
   */
  public void receiveException(Exception e) {
    setResponse();
    exception = e;
  }

  /**
    * Method by which this message is supposed to return it's response.
   *
   * @param c The continuation to return the reponse to.
   */
  public void returnResponse(Continuation c, Environment env, String instance) {
    if (exception == null)
      c.receiveResult(response);
    else
      c.receiveException(exception);
  }

  /**
   * Returns the response
   *
   * @return The response
   */
  public Object getResponse() {
    return response;
  }
}

