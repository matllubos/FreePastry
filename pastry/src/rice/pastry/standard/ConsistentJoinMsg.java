/*
 * Created on Apr 13, 2005
 */
package rice.pastry.standard;

import java.util.HashSet;

import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;

/**
 * @author Jeff Hoye
 */
public class ConsistentJoinMsg extends Message {
  LeafSet ls;
  boolean request;
  HashSet failed;
  
  /**
   * 
   */
  public ConsistentJoinMsg(Address addr, LeafSet ls, HashSet failed, boolean request) {
    super(addr);
    this.ls = ls;
    this.request = request;
    this.failed = failed;
  }
  
  public String toString() {
    return "ConsistenJoinMsg "+ls+" request:"+request; 
  }
}
