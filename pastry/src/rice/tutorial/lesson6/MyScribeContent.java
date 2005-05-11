/*
 * Created on May 4, 2005
 */
package rice.tutorial.lesson6;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;

/**
 * @author Jeff Hoye
 */
public class MyScribeContent implements ScribeContent {
  NodeHandle from;
  int seq;
  
  /**
   * @param from Who sent the message.
   */
  public MyScribeContent(NodeHandle from, int seq) {
    this.from = from;
    this.seq = seq;
  }

  public String toString() {
    return "MyScribeContent #"+seq+" from "+from;
  }  
}
