/*
 * Created on Dec 22, 2003
 *
 */
package rice.pastry.testing;

import rice.p2p.commonapi.Id;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;

/**
 * @author Jeff Hoye
 *
 */
public class HelloMsg extends Message {
  public Id target;
  private int msgid;
  public boolean messageDirect = false;

  public HelloMsg(Address addr, NodeHandle src, Id tgt, int mid) {
    super(addr);
    target = tgt;
    msgid = mid;
  }

  public String toString() {
    return "Hello #" + msgid;
  }

  public String getInfo() {
    
    String s=toString();
    if (messageDirect) {
      s+=" direct";
    } else {
      s+=" routed";
    }
//    s += " lastAt:"+intermediateSource+" nextHop:"+nextHop+" from:" + source + " to:" + target;// +"<"+state+">"; // + " received by "+actualReceiver+"}";
    return s;
  }
    
  public int getId(){
      return msgid;
  }

}
