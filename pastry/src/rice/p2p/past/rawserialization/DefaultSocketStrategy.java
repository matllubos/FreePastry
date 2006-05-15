/*
 * Created on Apr 21, 2006
 */
package rice.p2p.past.rawserialization;

import rice.p2p.past.PastContent;

/**
 * A SocketStrategy that always returns the same answer.  
 * 
 * @author Jeff Hoye
 */
public class DefaultSocketStrategy implements SocketStrategy {

  boolean answer = false;
  public DefaultSocketStrategy(boolean answer) {
    this.answer = answer; 
  }
  
  public boolean sendAlongSocket(int sendType, PastContent content) {
    return answer;
  }
}
