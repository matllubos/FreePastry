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
    
    Continuation command = new MyContinuation();    
    past.lookup(id, command);    
  }
}
