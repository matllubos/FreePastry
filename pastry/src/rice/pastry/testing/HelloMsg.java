/*
 * Created on Dec 22, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.testing;

import java.net.InetSocketAddress;
import java.util.Vector;

import rice.p2p.commonapi.Id;
import rice.pastry.NodeId;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;
import rice.pastry.socket.ConnectionManager;
import rice.pastry.socket.SocketManager;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class HelloMsg extends Message {
  public transient boolean ackReceived = false;
	//public byte[] garbage = new byte[64000];
    public int queueSize = -1;
	public transient int state;
	public NodeId source;
    public Id target;
    public NodeId actualReceiver;
    private int msgid;
    private transient Vector receivers;
    public transient ConnectionManager lastMan;
    public transient SocketManager lastSM;

    public HelloMsg(Address addr, NodeId src, Id tgt, int mid) {
        super(addr);
        source = src;
        target = tgt;
        msgid = mid;
    }

  public String toString() {
    return "Hello #" + msgid;
  }

  public String getInfo() {
    
    String s=toString();
    s += " from " + source + " to " + target +"<"+state+"> {"+queueSize+"}";// + " received by "+actualReceiver+"}";
//    System.out.println("HM.getInfo():1");
    /*
    if (receivers != null) {
      synchronized(receivers) {
        Iterator i = receivers.iterator();
        while (i.hasNext()) 
          s += ","+i.next();      
      }
    }
    */
//    System.out.println("HM.getInfo():2");
    if (lastMan != null) {
      s += ","+lastMan.getStatus();
    }
    if (lastSM != null) {
      s += ","+System.identityHashCode(lastSM);
    }
  //  System.out.println("HM.getInfo():3");
    return s;
    }
    
    public int getId(){
        return msgid;
    }

		/**
		 * @param address
		 */
		public void addReceiver(InetSocketAddress address) {
      if (receivers == null) {
        receivers = new Vector();
      }
      receivers.add(address);
		}

		/**
		 * 
		 */
		public InetSocketAddress getLastAddress() {
      if (receivers == null) {
        return null;
      }
			return (InetSocketAddress)receivers.lastElement();			
		}

}
