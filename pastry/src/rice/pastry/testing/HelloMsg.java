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
  private transient boolean ackReceived = false;
//	public transient int state;
  public NodeHandle source;
  public NodeHandle intermediateSource;
    public Id target;
    public NodeHandle actualReceiver;
    private int msgid;
//    private transient Vector receivers;
//    private transient ConnectionManager lastMan;
//    public transient SocketManager lastSM;
    public transient NodeHandle nextHop = null;
    public boolean messageDirect = false;

    public HelloMsg(Address addr, NodeHandle src, Id tgt, int mid) {
        super(addr);
        source = src;
        intermediateSource = src;
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
//    if (lastMan != null) {
//      s += ","+lastMan.getStatus();
//    }
//    if (lastSM != null) {
//      s += ", lastSM:"+lastSM;
////      if (lastSM.closedTrace != null)
////        lastSM.closedTrace.printStackTrace();
//    }
  //  System.out.println("HM.getInfo():3");
    return s;
    }
    
    public int getId(){
        return msgid;
    }

		/**
		 * @param address
		 */
//		public void addReceiver(InetSocketAddress address) {
//      if (receivers == null) {
//        receivers = new Vector();
//      }
//      receivers.add(address);
//		}

		/**
		 * 
		 */
//		public InetSocketAddress getLastAddress() {
//      if (receivers == null) {
//        return null;
//      }
//			return (InetSocketAddress)receivers.lastElement();			
//		}

}
