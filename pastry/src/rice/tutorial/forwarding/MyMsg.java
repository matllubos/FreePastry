/*
 * Created on Feb 15, 2005
 */
package rice.tutorial.forwarding;

import java.util.ArrayList;

import rice.p2p.commonapi.*;

/**
 * An example message.
 * 
 * @author Jeff Hoye
 */
public class MyMsg implements Message {
  /**
   * Where the Message came from.
   */
  Id from;
  /**
   * Where the Message is going.
   */
  Id to;
  
  /**
   * Constructor.
   */
  public MyMsg(Id from, Id to) {
    this.from = from;
    this.to = to;
  }
  
  public String toString() {
    String path = "";
    for (int i = 0; i < passport.size(); i++) {
      path+=passport.get(i)+",";
    }
    return "MyMsg along path "+path;
  }

  /**
   * Use low priority to prevent interference with overlay maintenance traffic.
   */
  public byte getPriority() {
    return Message.LOW_PRIORITY;
  }

  ArrayList passport = new ArrayList();
  public void addHop(NodeHandle hop) {
    passport.add(hop);
  }
}
