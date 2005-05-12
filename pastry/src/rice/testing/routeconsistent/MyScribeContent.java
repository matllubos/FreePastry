/*
 * Created on May 4, 2005
 */
package rice.testing.routeconsistent;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;

/**
 * @author Jeff Hoye
 */
public class MyScribeContent implements ScribeContent {
  NodeHandle from;
  int seq;
  boolean anycast;
  
  /**
   * @param from Who sent the message.
   */
  public MyScribeContent(NodeHandle from, int seq, boolean anycast) {
    this.from = from;
    this.seq = seq;
    this.anycast = anycast;
  }

  public String toString() {
    if (anycast) {
      return "MyScribeContent (anycast) #"+seq+" from "+from;
    }
    return "MyScribeContent #"+seq+" from "+from;    
  }  
}
