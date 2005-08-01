/*
 * Created on Jul 6, 2005
 */
package rice.tutorial.lesson0a;

import rice.Continuation;
import rice.p2p.commonapi.Id;
import rice.p2p.past.*;

/**
 * @author Jeff Hoye
 */
public class TestContinuation {

  public static void main(String[] args) {
    Past past = null; // generated elsewhere
    Id id = null; // generated elsewhere
    
    // create the continuation
    Continuation command = new MyContinuation();    
    
    // make the call with the continuation
    past.lookup(id, command);    
  }
}
