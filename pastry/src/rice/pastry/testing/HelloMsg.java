/*
 * Created on Dec 22, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.testing;

import rice.p2p.commonapi.Id;
import rice.pastry.NodeId;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class HelloMsg extends Message {
    public NodeId source;
    public Id target;
    public NodeId actualReceiver;
    private int msgid;

    public HelloMsg(Address addr, NodeId src, Id tgt, int mid) {
        super(addr);
        source = src;
        target = tgt;
        msgid = mid;
    }

    public String toString() {
    String s="";
    s += "{Hello #" + msgid +
        " from " + source + " to " + target + " received by "+actualReceiver+"}";
    return s;
    }
    
    public int getId(){
        return msgid;
    }

}
