/*
 * Created on Dec 22, 2003
 *
 */
package rice.pastry.socket.testing;

import java.lang.ref.WeakReference;

import rice.p2p.commonapi.Id;
import rice.pastry.NodeHandle;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNode;

/**
 * @author Jeff Hoye
 *
 */
public class HelloMsg extends Message {
  private PseudoSocketNodeHandle source;
  private PseudoSocketNodeHandle intermediateSource;
  private PseudoSocketNodeHandle actualReceiver;
  private transient WeakReference nextHop = null;
  private transient WeakReference localNode;
	
  public Id target;
  private int msgid;
  public boolean messageDirect = false;

  public HelloMsg(Address addr, SocketNodeHandle src, Id tgt, int mid) {
    super(addr);
		localNode = new WeakReference(src.getLocalNode());
    setSource(src);
    setIntermediateSource(src);
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
    s += " lastAt:"+intermediateSource+" nextHop:"+nextHop+" from:" + source + " to:" + target;// +"<"+state+">"; // + " received by "+actualReceiver+"}";
    return s;
  }
    
  public void setLocalNode(SocketPastryNode spn) {
  	this.localNode = new WeakReference(spn);
  }

  public int getId(){
      return msgid;
  }
  
  public void setSource(SocketNodeHandle snh) {
    source = new PseudoSocketNodeHandle(snh);	
  }
  
  public void setActualReceiver(SocketNodeHandle snh) {
  	actualReceiver = new PseudoSocketNodeHandle(snh);
  }
  
  public void setIntermediateSource(SocketNodeHandle snh) {
    intermediateSource = new PseudoSocketNodeHandle(snh);	
  }
  
  public void setNextHop(NodeHandle nh) {
  	nextHop = new WeakReference(nh);
  }

//  public PseudoSocketNodeHandle getActualReceiver() {
//  	return actualReceiver;
//  }
  
  public PseudoSocketNodeHandle getIntermediateSource() {
    return intermediateSource;	
  }
  
  public NodeHandle getNextHop() {
  	return (SocketNodeHandle)nextHop.get();
  }

  public SocketPastryNode getLocalNode() {
		if (localNode != null) {
			return (SocketPastryNode)localNode.get();	
		}
		return null;
  }
}
