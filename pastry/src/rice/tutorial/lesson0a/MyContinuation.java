/*
 * Created on Jul 6, 2005
 */
package rice.tutorial.lesson0a;

import rice.Continuation;
import rice.p2p.past.PastContent;

/**
 * Example continuation.
 * 
 * @author Jeff Hoye
 */
class MyContinuation implements Continuation {
  /**
   * Called when the result arrives.
   */
  public void receiveResult(Object result) {
    PastContent pc = (PastContent)result;
    System.out.println("Received a "+pc);
  }

  /**
   * Called if there is an error.
   */
  public void receiveException(Exception result) {
    System.out.println("There was an error: "+result);      
  }
}