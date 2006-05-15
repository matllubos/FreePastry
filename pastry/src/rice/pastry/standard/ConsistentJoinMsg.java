/*
 * Created on Apr 13, 2005
 */
package rice.pastry.standard;

import java.io.IOException;
import java.util.*;

import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.join.JoinAddress;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;

/**
 * @author Jeff Hoye
 */
public class ConsistentJoinMsg extends PRawMessage {
  private static final long serialVersionUID = -8942404626084999673L;
  
  public static final short TYPE = 2;
  
  LeafSet ls;
  boolean request;
  HashSet failed;
  
  /**
   * 
   */
  public ConsistentJoinMsg(LeafSet ls, HashSet failed, boolean request) {
    super(JoinAddress.getCode());
    this.ls = ls;
    this.request = request;
    this.failed = failed;
  }
  
  public String toString() {
    return "ConsistentJoinMsg "+ls+" request:"+request; 
  }
  
  /***************** Raw Serialization ***************************************/
  public short getType() {
    return TYPE; 
  }
  
  public void serialize(OutputBuffer buf) throws IOException {    
    buf.writeByte((byte)0); // version
    ls.serialize(buf);
    buf.writeBoolean(request);
    buf.writeInt(failed.size());
    Iterator i = failed.iterator();
    while(i.hasNext()) {
      NodeHandle h = (NodeHandle)i.next(); 
      h.serialize(buf);
    }
  }
  
  public ConsistentJoinMsg(InputBuffer buf, NodeHandleFactory nhf, NodeHandle sender) throws IOException {
    super(JoinAddress.getCode());    
    byte version = buf.readByte();
    switch(version) {
      case 0:
        setSender(sender);
        ls = LeafSet.build(buf, nhf);
        request = buf.readBoolean();
        failed = new HashSet();
        int numInSet = buf.readInt();
        for (int i = 0; i < numInSet; i++) {
          failed.add(nhf.readNodeHandle(buf));
        }
        break;
      default:
        throw new IOException("Unknown Version: "+version);
    }      
  }
}
