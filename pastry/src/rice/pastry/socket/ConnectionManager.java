/*
 * Created on Mar 31, 2004
 *
 */
package rice.pastry.socket;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import rice.p2p.commonapi.Id;
import rice.pastry.NodeHandle;
import rice.pastry.commonapi.PastryEndpointMessage;
import rice.pastry.dist.NodeIsDeadException;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.socket.exception.TooManyMessagesException;
import rice.pastry.socket.messaging.AckMessage;
import rice.pastry.socket.messaging.AddressMessage;
import rice.pastry.socket.messaging.SocketTransportMessage;
import rice.selector.SelectorManager;
import rice.selector.TimerTask;

/**
 * 
 * This class manages a connection to a single remote node.  It can potentially have 3 types of 
 * communication with that node:
 *   Liveness (UDP)
 *   Control/Routing (TCP)
 *   Data (TCP)
 * Control/Routing and Data are specifically managed by a SocketManager.  
 * The Liveness communication is managed via the PingManager
 * 
 * 
 * This class manages 2 SocketManagers for a particular address.  
 * <HR><HR>
 * 1. A control/routing connection which is acked, and has a maximum size message.
 * 2. A data connection which can have messages of any size and is not acked.
 * <HR>
 * In addition, this class manages the liveness for a particular connection.  
 * It uses a PingManager and the AckTimeoutEvent to do this.  
 * 
 * Any message passed to the SocketManager is considered "in-flight" to get 
 * good TCP performance.  This is a necessity to have two queues for control/routing
 * messages to prevent large scale message duplication under constrained situations:
 * 
 * Lets say we have 80 unacked route messages where the next hop is not the target.
 * It is our pipe that is used up because we are sending so much traffic.  The other
 * guy can't get his ack back through, so we consider him SUSPECTED(_FAULTY) and 
 * reroute all 80 in-flight unacked messages to someone else, causing further harm 
 * to our situation.
 * 
 * 
 * Synchronization strategy:
 * We have 3 kinds of threads acting on this:
 *   Selector Thread
 *   User thread calling send
 *   Timer thread calling vairous
 * 
 * After much thought, we decided to do on "EVERYTHING" on the Selector Thread.
 * To achieve this, all entry points call invoke on the selector which defers 
 * the processing of such an event on the selector queue.
 * 
 * The lifecycle of a node is:
 * UNKNOWN->ALIVE after calling first markAlive();
 * ALIVE->SUSPECTED(_FAULTY) after ackTimeout() and NUM_PINGS_SUSPECT_FAULTY which can be 0
 * SUSPECTED->FAULTY after NUM_PING_TRIES;
 * 
 * ack timeout is called RTO Retransmission Time Out because we use the same calculation
 * as is listed in RFC 1122 (re: TCP), which is updated every ackReceived().  Note that we have 
 * exponential falloff on the timeouts, so if you set the NUM_PINGS_SUSPECT_FAULTY = 1, 
 * and backoffPower = 2, it will take 3*RTO to SUSPECT_FAULTY and reroute around the slow node.  
 * 
 * RTO for ackFailed() to be called
 * + RTO * backoffPower^1 
 * = 3*RTO
 * 
 * With the default settings, the min time that you could declare a node faulty is 61 seconds.  The
 * max time is just over 4 hours, but the node would have to have had an RTO of 240 seconds!
 * 
 * @author Jeff Hoye
 */
public class ConnectionManager {

  // various pointers   
  SocketManager controlSocketManager;
  SocketManager dataSocketManager;
  PingManager pingManager;
  SocketCollectionManager scm;

  /**
   * A handle to the remote node.
   */
  public WeakReference snh; // of a SocketNodeHandle
  
  
  /**
   * The remote node's address.
   */
  InetSocketAddress address;

  /**
   * Enable to turn on additional debugging.
   */
  public static boolean LOG_LOW_LEVEL = true;
  
  /**
   * Kept for debugging.
   */
  int timesMarkedDead = 0;
  /**
   * Kept for debugging.
   */
  int timesMarkedSuspected = 0;

  /**
   * Kept for debugging.
   */
  int timesMissedAck = 0;

  /**
   * We need to have some messages pending invoke on the selector thread 
   * to keep things simple and guarantee order.  However, we must determine
   * where we think these messages are going as they enter the invoke queue
   * so we can start throwing away messages if too many are queued.
   * 
   * This object is what we synchronize on to count the number of messages
   * in this queue.  See invokedControlMessages, invokedDataMessages.
   */
  private Object invokeLock = new Object();
  
  /**
   * Messages in the selector invoke queue.
   */
  int invokedControlMessages = 0;
  /**
   * Messages in the selector invoke queue.
   */
  int invokedDataMessages = 0;

  // ****************** Configuration ******************
  /**
   *  the maximum length of the queue
   */
  public static int MAXIMUM_QUEUE_LENGTH = 128;
  
  /**
   * The maximum number of in flight messages (not including acks).
   * Raising this variable increases bandwidth, but can cause 
   * that number of duplicated messages at each hop that fails
   * an ack timeout.  So, setting this number too high can cause
   * massive message replication in a high churn environment where we
   * often mistake a node for suspected failure (causing message rerouting)
   */
  public static final int MAX_PENDING_ACKS = 1;
  
  /**
   * The maximum message size for a routing entry
   */
  public static final int MAX_ROUTE_MESSAGE_SIZE = 6553600; // 65536

  /**
   * The number of pings before we move into SUSPECTED(_FAULTY)
   */
  public static final int NUM_PINGS_SUSPECT_FAULTY = 1; 

  /**
   * The number of pings before we call it dead.
   */
  public static final int NUM_PING_TRIES = 3;

  /**
   * This is how much we backoff if we don't receive a ping.
   * NUM_PING_RETRIES and backoffPower are the greatest
   * contributors to how long it takes to declare a node faulty.  
   */
  public static final double backoffPower = 2.0;

  /**
   * Retransmission Time Out
   */
  int RTO = 3000; 

  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  int RTO_UBOUND = 240000; // 240 seconds
  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  int RTO_LBOUND = 1000;

  /**
   * Average RTT
   */
  double RTT = 0;

  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  double gainH = 0.25;

  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  double gainG = 0.125;

  /**
   * Standard deviation RTT
   */
  double standardD = RTO/4.0;  // RFC1122 recommends choose value to target RTO = 3 seconds
  
  // *************** Control/Acking Fields *********************
  /**
   * The acks we are waiting for
   * Sequence Number(Integer) -> AckTimeoutEvent
   */
  private HashMap pendingAcks;

  /**
   * The current sequence number for the control socket.
   */
  private int currentAckNumber = 0;
  
  /**
   * Internal list of objects waiting to be written over control channel.
   */
  private LinkedList controlQueue;

  /**
   * A possible value for (@see SocketManger#type).  
   * A control/routing socket.
   */
  public static final int TYPE_CONTROL = 1;
  /**
   * A possible value for (@see SocketManger#type).  
   * A data socket.
   */
  public static final int TYPE_DATA = 2;
  

  // *************** Liveness Fields *********************
  /**
   * NodeHandle.LIVENESS_ALIVE, NodeHandle.LIVENESS_SUSPECTED, NodeHandle.LIVENESS_FAULTY, NodeHandle.LIVENESS_UNKNOWN
   */
  int liveness;

  /**
   * Non-Null if we are currently pinging the node.
   */
  private DeadChecker deadChecker;

	private int epoch;
	private Id id;

	/**
	 * Constructs a new ConnectionManager.  
   * This does NOT automatically open TCP connections for control or data traffic.  
   * This is done lazilly.
	 */
	public ConnectionManager(SocketCollectionManager scm, SocketNodeHandle snh) {
    this.scm = scm;   
    this.address = snh.getAddress();
    this.epoch = snh.getEpoch();
    this.id = snh.getId();
    this.snh = new WeakReference(snh);
    this.pingManager = scm.getPingManager();   
    addLivenessListener(scm); 
    pendingAcks = new HashMap();
    controlQueue = new LinkedList();    
    liveness = NodeHandle.LIVENESS_UNKNOWN; 
    checkDead();
	}

  //********************** Socket Lifecycle ***********************
  /**
   * Method which opens a socket manager to a given remote node handle, and updates the
   * bookkeeping to keep track of this socket.  
   *
   * @param type TYPE_CONTROL, TYPE_DATA
   */
  private void openSocket(int type) {
    if (type == TYPE_DATA) {
      if (dataSocketManager != null) {
//        Thread.dumpStack();
        return;
      }
    } else {
      if (controlSocketManager != null) {
//        Thread.dumpStack();
        return;
      }
    }
    
    if (getLiveness() >= NodeHandle.LIVENESS_UNREACHABLE) {
      debug("WARNING:"+this+"Attempting to open socket to faulty or unreachable node");
      checkDead();
      return;
    }
    SocketNodeHandle remoteHandle = getNodeHandle();
    if (remoteHandle != null) {
      final SocketManager sm = new SocketManager(address, scm, this, type, remoteHandle);
        if (type == TYPE_DATA) {
          dataSocketManager = sm;
        } else {
          controlSocketManager = sm;
        }
        if (!failedDuringOpen) {
          if (liveness != NodeHandle.LIVENESS_UNKNOWN) {
            sm.tryToCreateConnection(); 
          } else {
            addLivenessListener(new LivenessListener() {
  						public void updateLiveness(NodeHandle nh, int liveness) {
                if (liveness != NodeHandle.LIVENESS_UNKNOWN) {
                  if ((!sm.closed) && (!sm.connecting))
                    sm.tryToCreateConnection();
                  removeLivenessListener(this);
                }
  						}
  					});
          }
        }
     } else {
       System.out.println(this+" remote node handle is null");
     }
      checkDead();
  }


  /**
   * called by SocketCollectionManager when a remote node opens a socket.
   * This has logic to replace the existing socket if both parties opened
   * sockets to each other concurrenlty.  It has a helper replaceExistingSocket().
   * 
   * @param sm The new SocketManager opened by the remote node
   */
  public void acceptSocket(SocketManager sm) {
    if (sm.closed) {
      Thread.dumpStack();
    }
    sm.setConnectionManager(this);
    if (liveness != NodeHandle.LIVENESS_ALIVE)
      checkDead();
    if (sm.getType() == TYPE_CONTROL) {
      if (controlSocketManager == null) {
        controlSocketManager = sm;
      } else {
        // we have to be very careful here
        if (!controlSocketManager.connecting || replaceExistingSocket(controlSocketManager,sm)) {
//          if (!controlSocketManager.connecting) {
//            System.out.println(controlSocketManager+" collided with "+sm+" due to not connected, closed"+controlSocketManager);
//          } else {
//            System.out.println(controlSocketManager+" collided with "+sm+" due to replacement, closed"+controlSocketManager);            
//          }
          SocketManager temp = controlSocketManager;
          controlSocketManager = sm;
          requeueMessagesPendingAck();
          moveMessagesToControlSM();
          temp.close();
        } else {
//          System.out.println(controlSocketManager+" collided with "+sm+" due to replacement, closed:"+sm);            
          sm.closeHalf();
        }
      }
    } else {
      if (dataSocketManager == null) {
        dataSocketManager = sm;
      } else {
        // we have to be very careful here
        if (!dataSocketManager.connecting || replaceExistingSocket(dataSocketManager,sm)) {
          SocketManager temp = dataSocketManager;
          dataSocketManager = sm;
					// move all pending messages out of old dsm
					Iterator i = temp.getPendingMessages();
					while (i.hasNext()) {
						Message o = (Message)i.next();
						if (!(o instanceof AddressMessage)) {
							dataSocketManager.send(o);	
						}	
					}					
          temp.close();
        } else {
          sm.closeHalf();
        }
      }
    }
  }

  /**
   * Called when there are 2 connections of the same type opened to the same node.  
   * It returns wether to close the existing socket and use the new one.
   * The algorithm is symmetric so both sides will calculate the same decision.
   * The one with the lower hashcode for the string version of address/port
   * @param existing My existing SocketManager
   * @param newMgr The new one trying to be opened.
   * @return true if the existing one should be replaced.
   */
  private boolean replaceExistingSocket(SocketManager existing, SocketManager newMgr) {
    debug("ERROR: Request to record socket opening for already-open socket to " + address + " of type:"+existing.getType());
    String local = "" + scm.getAddress().getAddress().getHostAddress() + scm.getAddress().getPort();
    String remote = "" + address.getAddress().getHostAddress() + address.getPort();
  
    debug("RESOLVE: Comparing " + local + " and " + remote);
  
    if (remote.compareTo(local) < 0) {
      if (ConnectionManager.LOG_LOW_LEVEL)
        System.out.println("CM.replaceExistingSocket(): RESOLVE: Cancelling existing data connection " + existing+" replacing with "+newMgr);  
      debug("RESOLVE: Cancelling existing data connection to " + address);  
      return true;
    } else {
      if (ConnectionManager.LOG_LOW_LEVEL)
        System.out.println("CM.replaceExistingSocket(): RESOLVE: Cancelling new data connection " + newMgr+" keeping "+existing);  
      debug("RESOLVE: Cancelling new connection to " + address);
      return false;
    }
  }

  /**
   * This is the ConnectionManager's time to tell the socketmanager that it is not idle.
   * It does this if the manager is this cm's controlSocketManager, and it has no pending
   * acks.
   * 
   * @param manager
   * @return true if the socket can be considered idle
   */
  public boolean isPendingAcks(SocketManager manager) {
    if (manager.getType() == TYPE_CONTROL) {
      if (manager == controlSocketManager) {
        if (pendingAcks.size() > 0) {
          return false;
        }
      }
    }
    return true;
  }
  
  /**
   * Called when a socket is closed to set the socketManger to null.
   * 
   * Note that this can be called when we are competing for available 
   * Sockets (FileHandles) so it in no way affects the liveness of the 
   * remote node.  In some cases there are still pending messages to
   * be sent, it moves all the pending acked messages back into the queue
   * (potential message duplication).  Then it schedules reopening the
   * socket in the future.
   * 
   * @param manager The SocketManager that was closed.
   */
  public void socketClosed(SocketManager manager) {
    //System.out.println(this+".socketClosed("+manager.getType()+")");
    if (manager.getType() == TYPE_CONTROL) {
      if (manager == controlSocketManager) {
        //System.out.println(this+"Socket closed with:"+controlQueue.size()+"+"+pendingAcks.size()+" messages outstanding.");
        controlSocketManager = null;
      
        // reopen socket later if there are messages in queue
        if (scm.pastryNode.isAlive() && (controlQueue.size() > 0 || pendingAcks.size() > 0)) {
          int q = controlQueue.size();
          int pa = pendingAcks.size();
          // move all pending acks to front of queue
          requeueMessagesPendingAck();
          
          // we got our socket taken away from us and 
          // need a new one next time one is available
          //System.out.println("CM.socketClosed("+manager.getType()+"):requesting socket for recently closed one queue:"+q+" pendingAcks:"+pa);
          openSocket(TYPE_CONTROL);
          moveMessagesToControlSM();
        }
      }
    } else { // manager was the dsm
      if (manager == dataSocketManager) {
        dataSocketManager = null;
      }
    }
    scm.getSocketPoolManager().socketClosed(manager);
  }  

  /**
   * A helper for socketClosed().  This will requeue the messages that 
   * we didn't get acks from.  We need to do this when we close a socket 
   * because we don't know that these messages got to their destination.
   * 
   * Note that this can cause message duplication.
   */
  private void requeueMessagesPendingAck() {

    Iterator i = pendingAcks.keySet().iterator();
    while (i.hasNext()) {                    
      AckTimeoutEvent ate = (AckTimeoutEvent)pendingAcks.remove(i.next());
      ate.cancel();
      Message message = ate.msg.msg;
            
      addToQueuePriority(ate.msg.msg);
    }    
  }

  // *************** Message Lifecycle *****************
  /**
   * Sends the message over the apropriate channel.  The type is determined
   * by getMessageType().  
   * <HR><HR>
   * If the type is TYPE_CONTROL then the message will be queued 
   * here.  Messages can be scheduled for writing when the number 
   * of messages in flight drop below MAX_PENDING_ACKS.
   * 
   * In reality, all this method does is to deferr sending the message 
   * until we are on the selector thread.  To maintain message order,
   * it is required that even if this method is called on the selector, 
   * we call invoke just as if it were called on any other thread.
   * 
   * @param message The message to send.
   * @param type SocketCollectionManager.TYPE_CONTROL, SocketCollectionManager.TYPE_DATA
   */
  public void send(final Message message) {    
    if (!scm.pastryNode.isAlive()) return;
    if (errors(message)) {
      return;
    }
//    if (Thread.currentThread() == scm.manager.selectorThread) {
//      Thread.dumpStack();
//    }
    if (LOG_LOW_LEVEL)
      System.out.println("ENQ:@"+System.currentTimeMillis()+":"+this+":"+message);
    //System.out.println("send("+message+"):"+message.getClass().getName());    
    
//    if (message instanceof RouteMessage) {
//      if (((RouteMessage)message).unwrap() instanceof HelloMsg) {
//        Thread.dumpStack();
//      }
//    }
    
    //Thread.dumpStack();
    SelectorManager.getSelectorManager().invoke(new Runnable() {
			public void run() {
        sendNow(message);
			}
		});    
  }  

  private boolean errors(Message message) {
    if (getLiveness() >= NodeHandle.LIVENESS_UNREACHABLE) {
      if (liveness == NodeHandle.LIVENESS_UNREACHABLE) {
        messageNotSent(message, SocketPastryNode.EC_NODE_UNREACHABLE);              
      } else {
        messageNotSent(message, SocketPastryNode.EC_CONNECTION_FAULTY);
      }
      return true;
    }
    int numMessages = 0;    
    if (getMessageType(message) == TYPE_CONTROL) {
      if (getNumberMessagesAllowedToSend(TYPE_CONTROL) < 0) {
        messageNotSent(message, SocketPastryNode.EC_QUEUE_FULL);
        return true;
      } else {
        synchronized(invokeLock) {
          invokedControlMessages++;       
        }
      }
    } else {
      if (getNumberMessagesAllowedToSend(TYPE_DATA) < 0) {
        messageNotSent(message, SocketPastryNode.EC_QUEUE_FULL);
        return true;
      } else {
        synchronized(invokeLock) {
          invokedDataMessages++;       
        }
      }
    }    
    return false;
  }
  
  /**
   * Returns the number of messages that may be queued
   * by type.  This looks for messages in all the locations
   * they may be: controlQueue, pendingAcks, invokedControlMessages
   * or dataSocketManager.getNumberPendingMessages(), invokedDataMessages
   * 
   * @param type Message type.
   * @return remaining queue size.
   */
  public int getNumberMessagesAllowedToSend(int type) {
    int numMessages = 0;
    if (type == TYPE_CONTROL) {
      numMessages+=controlQueue.size();
      numMessages+=pendingAcks.size();
      numMessages+=invokedControlMessages;      
      return MAXIMUM_QUEUE_LENGTH - numMessages;
    } else {
      if (dataSocketManager != null) {
        numMessages += dataSocketManager.getNumberPendingMessages();        
      }
      numMessages+=invokedDataMessages;      
      return SocketChannelWriter.MAXIMUM_QUEUE_LENGTH - numMessages;
    }
  }

  
  
  /**
   * To be called only on the seletor thread, and only from the anonamous
   * inner class created in send().
   * 
   * Sends the message over the apropriate channel.  The type is determined
   * by getMessageType().
   * <HR><HR>
   * If the type is TYPE_CONTROL then the message will be queued 
   * here.  Messages can be scheduled for writing when the number 
   * of messages in flight drop below MAX_PENDING_ACKS.
   * 
   * @param message The message to send.
   * @param type SocketCollectionManager.TYPE_CONTROL, SocketCollectionManager.TYPE_DATA
   */
  private void sendNow(Message message) {    
//    System.out.println(this+".sendNow("+message+"):"+message.getClass().getName());    
    if (!scm.pastryNode.isAlive()) return;

    int type = getMessageType(message);
    if (type == TYPE_CONTROL) {  
      synchronized(invokeLock) {
        invokedControlMessages--;
      }
      // Check to see if we should reroute this
      if (!reroute(message)) {
        enqueue(message);
        moveMessagesToControlSM();
      }
    } else { // type == TYPE_DATA
      synchronized(invokeLock) {
        invokedDataMessages--;
      }
      if (liveness < NodeHandle.LIVENESS_UNREACHABLE) {
        openSocket(TYPE_DATA);        
        dataSocketManager.send(message);        
      } else {
        if (liveness == NodeHandle.LIVENESS_UNREACHABLE) {
          messageNotSent(message, SocketPastryNode.EC_NODE_UNREACHABLE);              
        } else {
          messageNotSent(message, SocketPastryNode.EC_CONNECTION_FAULTY);
        }
      }
    }
  } // sendNow()


  /**
   * This method determines which socket the message should be sent over.  
   * Currently it sends any message with "priority" or a RouteMessage
   * over the control channel.
   * 
   * @param m the message
   * @return TYPE_CONTROL or TYPE_DATA
   */
  int getMessageType(Message m) {
    
//    if (m instanceof RouteMessage) {
//      if (((RouteMessage)m).unwrap() instanceof HelloMsg) {
//        return TYPE_DATA;        
//      }
//    }

    if (m.hasPriority() || 
         ((m instanceof RouteMessage) && ((RouteMessage)m).getOptions().multipleHopsAllowed())) {         
      return TYPE_CONTROL;
    }
//    System.out.println("Sending "+m+" over data pipe.");
    return TYPE_DATA;
  }

  
// *********************** Control Traffic Handlers **********************

  int lastQueueSize = 0;
	/**
   * Enqueues the object for sending over the control/routing channel by priority.
   * 
   * @param o The object to be queued.
   * @return true if we will attempt to send the message.  False if we're dead, or the queue is full.
   */
  private boolean enqueue(Message o) {
    if (controlQueue.size() < MAXIMUM_QUEUE_LENGTH) {
      if (LOG_LOW_LEVEL) {
        if (controlQueue.size() > lastQueueSize+10) {
          lastQueueSize = controlQueue.size();
          System.out.println(this+" queue size = "+lastQueueSize);
        }
      }
      addToQueue(o);
      return true;
    } else {      
      System.err.println(System.currentTimeMillis()+":"+scm.pastryNode.getNodeId()+"->"+ address + " (CM): Maximum TCP queue length reached - message " + o + " will be dropped.");
      return false;
    }
  }

  /**
   * Adds an entry into the control/routing queue, taking message 
   * prioritization into account.  By calling addToQueuePriority 
   * if the message has priority.
   *
   * @param o The feature to be added to the ToQueue attribute
   */
  private void addToQueue(Message o) {
    boolean priority = o.hasPriority();
    if (priority) {
      addToQueuePriority(o);
      return;
    }
    controlQueue.addLast(new MsgEntry(o));
  }

  /**
   * Inserts the message into the queue after all of the other
   * control messages, but before all the route messages.  Giving
   * overlay control messages priority, or in the case of killing
   * a socket prematurely, we give priority to the messages pending
   * ack, but they are queued after the messages with priority 
   * (i.e. control messages).  Note that the check to determine
   * if the message had priority occured before calling this method.
   * 
   * @param o the message to queue.
   */
  private void addToQueuePriority(Message o) {
    if (controlQueue.size() > 0) {
      for (int i = 1; i < controlQueue.size(); i++) {
        Object thisObj = ((MsgEntry)controlQueue.get(i)).message;

        if ((thisObj instanceof Message) && (!((Message) thisObj).hasPriority())) {
          debug("Prioritizing socket message " + o + " over message " + thisObj);

          controlQueue.add(i, new MsgEntry(o));
          return;
        }
      }
    }    
    controlQueue.addLast(new MsgEntry(o));
  }

  /**
   * Schedules up to MAX_PENDING_ACKS messages into the controlSocketManager from the queue.
   * This is called when we get an ack, or we schedule a new message.  This is where we wrap
   * a message in a SocketTransportMessage.
   */
  private void moveMessagesToControlSM() {
    openSocket(TYPE_CONTROL);
    while (!controlQueue.isEmpty() && canWrite()) {      
      Object message = ((MsgEntry)controlQueue.removeFirst()).message;

      SocketTransportMessage stm = new SocketTransportMessage((Message)message,currentAckNumber++);
      registerMessageForAck(stm); 
      controlSocketManager.send(stm);
    }
  }

  /**
   * This is the function that decides wether we can schedule more in
   * flight messages.  It currently only looks at the number of messages
   * in flight.  But may eventually take liveness into account.
   * 
   * @return wether we can schedule inflight messages.
   */
  private boolean canWrite() {
    if (pendingAcks.size() > MAX_PENDING_ACKS) {
      debug("WARNING: CM.canWrite() found pendingAcks.size to be greater than MAX_PENDING_ACKS:"+pendingAcks.size()+" > "+MAX_PENDING_ACKS);
    }
    return pendingAcks.size() < MAX_PENDING_ACKS;
  }

  /**
   * Called whenever we schedule a new message over the controlSocketManager.
   * @param msg The SocketTransportMessage we are about to send.
   */
  private void registerMessageForAck(SocketTransportMessage msg) {
      AckTimeoutEvent ate = new AckTimeoutEvent(msg);
      pendingAcks.put(new Integer(ate.ackNum),ate);
      ate.schedule(0);
//      scm.pastryNode.scheduleTask(ate, RTO);
//      System.out.println(this+".registerMsg4Ack("+ate+"):type="+type);
    controlSocketManager.markActive();
  }
  
 
  /**
   * Called when a message couldn't be sent becasue the message was too large.
   *
   * @param stp SocketTransportMessage that couldn't be sent.
   * @param len the length of the serialized message.
   */  
  void messageTooLarge(SocketTransportMessage stm, int len) {
    int i = stm.seqNumber;
    RouteMessage rm = (RouteMessage)stm.msg;
    Message m = rm.unwrap();
    messageNotSent(m,SocketPastryNode.EC_MSG_TOO_LARGE);
//    System.err.println(this+"WARNING: Message "+o+" dropped because it was too big.  Size = "+len+" max size ="+ConnectionManager.MAX_ROUTE_MESSAGE_SIZE);

    // pull the ate out of the queue
    AckTimeoutEvent ate = (AckTimeoutEvent)pendingAcks.remove(new Integer(i));    
    if (ate == null) {
      debug("ERROR: ConnectionManager.registerMessageForAck(): messageNotSent, but message was not in pendingAcks "+ stm+":"+len);
      return;
    }
    ate.cancel();
  }
  
  /**
   * Method to notify connection manager that a message was not sent.
   * @param m The unsent message.
   * @param errorCode the reason.
   */
  void messageNotSent(Message m, int errorCode) {
    scm.pastryNode.messageNotSent(m,errorCode);    
  }

  /**
   * Called by the SM when a message is read.
   * This will receive messages of 3 types:
   *   SocketTransportMessages - which it delivers payload and calls sendAck
   *   AckMessages - which it calls ackReceived
   *   Other - which should only be delivered from the 
   * data channel.
   * @param message The message that was just received.
   */
  public void receiveMessage(Message o) {
//    markAlive();
    if (o instanceof SocketTransportMessage) {
      SocketTransportMessage stm = (SocketTransportMessage)o;
      sendAck(stm);
      try {
        internalReceiveMsg(stm.msg);
      } catch (NodeIsDeadException nide) {
        nide.printStackTrace();
        if (scm.pastryNode.isAlive()) {
          throw nide;
        }
      }
      return;
    } else if (o instanceof AckMessage) {
      ackReceived((AckMessage)o);
      return;
    }    
    internalReceiveMsg(o);
  }

  public void internalReceiveMsg(Message o) {
    long beginTime = System.currentTimeMillis();
    scm.pastryNode.receiveMessage(o);
    long endTime = System.currentTimeMillis();   
    Object o2 = o; 
    if (o2 instanceof RouteMessage) {
      o2 = ((RouteMessage)o2).unwrap();
    }
    if (o2 instanceof PastryEndpointMessage) {
      o2 = ((PastryEndpointMessage)o2).getMessage();
    }
//    scm.manager.addStat(o2.getClass().getName(),endTime-beginTime);    
  }
	
	/**
	 *  of SocketTransportMessage
	 * The need for this is that the lastAckSent may 
	 * contain a reference to the SocketNodeHandle 
	 * that is kept to keep us from being 
	 * garbage collected.
 	 */
  WeakReference lastAckSent = null;

  /**
   * Called by controlSocketManager whenever a SocketTransportMessage is received.
   * This sends an ack.  It doesn't enque the ack locally, it schedules it into the 
   * controlSocketManger immeadiately.  We don't consider Acks "in-flight" messages.
   * 
   * @param smsg the SocketTransportMessage that was received.
   */
  private void sendAck(SocketTransportMessage smsg) {
//    System.out.println(this+".sendAck("+smsg+")");
    if (controlSocketManager != null) {
      controlSocketManager.send(new AckMessage(smsg.seqNumber,smsg));      
      lastAckSent = new WeakReference(smsg);
    } else {
      System.out.println("ERROR:ack lost for "+smsg);   
    }
  }

  /**
   * Called by controlSocketManager when an Ack is read.  This will schedule more messages to be 
   * sent if possible.
   * @param am the AckMessage that came off the wire.
   */
  private void ackReceived(AckMessage am) {
    int i = am.seqNumber;
    //System.out.println(this+" SM.ackReceived("+i+") on type :"+type);
//    markAlive(); //setLiveness(NodeHandle.LIVENESS_ALIVE);
    AckTimeoutEvent ate = (AckTimeoutEvent)pendingAcks.remove(new Integer(i));
    
    if (ate != null) {
//        System.out.println(this+" SM.ackReceived("+i+") on type :"+type+"elapsed Time:"+ate.elapsedTime());
      ate.cancel();              
      updateRTO(System.currentTimeMillis() - ate.startTime);
    } else {
      debug(this+" SM.ackReceived("+i+"):Ack received late");
    }
    moveMessagesToControlSM();
  }


  /**
   * Called by AckTimeoutEvent when a message took to long to respond.
   * @param ate The timeout event.
   */
  private void ackNotReceived(AckTimeoutEvent ate, int powerOffset) {
    //System.out.println(this+" SM.ackNotReceived("+ate+")");    
    AckTimeoutEvent nate = (AckTimeoutEvent)pendingAcks.get(new Integer(ate.ackNum));
    if (nate == null) {
      // already received ack, or we got the socket close out from under us
      return;
    }
    timesMissedAck++;
    if (NUM_PINGS_SUSPECT_FAULTY == 0) {
      markSuspected();
    }
    checkDead(powerOffset);
  }

  // ********************* Liveness Helpers ******************
  /**
   * Adds a new round trip time datapoint to our RTT estimate, and 
   * updates RTO and standardD accordingly.
   * 
   * @param m new RTT
   */
  private void updateRTO(long m) {
    // rfc 1122
    double err = m-RTT;
    double absErr = err;
    if (absErr < 0) {
      absErr *= -1;
    }
    RTT = RTT+gainG*err;
    standardD = standardD + gainH*(absErr-standardD);
    RTO = (int)(RTT+(4.0*standardD));
    if (RTO > RTO_UBOUND) {
      RTO = RTO_UBOUND;
    }
    if (RTO < RTO_LBOUND) {
      RTO = RTO_LBOUND;
    }
//      System.out.println("CM.updateRTO() RTO = "+RTO+" standardD = "+standardD+" suspected in "+getTimeToSuspected(RTO)+" faulty in "+getTimeToFaulty(RTO));
  }

  /**
   * Helper function to calculate exponential falloff based on RTO and number of tries.
   * 
   * @param i the exponent
   * @return the amount of time you should wait to do any more liveness updates or pings
   */  
  private int getRTOPower(int i) {
    return (int)(RTO*Math.pow(backoffPower,i));
  }

  /**
   * This gets the time to wait given an RTO and backoff state.  
   * 
   * It multiplies RTO * backoffPower ^ power.
   * A backoff state is the power of the backoffPower that we
   * are multiplying by.
   * 
   * @param exampleRTO the example RTO
   * @param power the backoff state 
   * @return the millis to wait until the next step
   */
  public static int getRTOPower(int exampleRTO, int power) {
    return (int)(exampleRTO*Math.pow(backoffPower,power));    
  }

  /**
   * Calculates the total time we will take to declare a node suspected given an RTO.
   * @param exampleRTO the RTO considering the other settings
   * @return millis of how long it will take to declare suspected
   */
  public static int getTimeToSuspected(int exampleRTO) {
    int declareSuspTime = exampleRTO;
    for (int i = 2; i <= NUM_PINGS_SUSPECT_FAULTY+1; i++) {
      declareSuspTime += getRTOPower(exampleRTO, i);
    }        
    return declareSuspTime;
  }
  
  /**
   * Calculates the total time we will take to declare a node faulty given an RTO.
   * @param exampleRTO the RTO considering the other settings
   * @return millis of how long it will take to declare faulty
   */
  public static int getTimeToFaulty(int exampleRTO) {
    int declareDeadTime = exampleRTO;
    for (int i = 2; i <= NUM_PING_TRIES+1; i++) {
      declareDeadTime += getRTOPower(exampleRTO, i);
    }    
    return declareDeadTime;
  }
  

  /**
   * Gets the liveness status of this node.
   * 
   * @return NodeHandle.LIVENESS_ALIVE, NodeHandle.LIVENESS_SUSPECTED, NodeHandle.LIVENESS_FAULTY, NodeHandle.LIVENESS_UNKNOWN
   */
  public int getLiveness() {
    //System.out.println(this+".getLiveness()"+liveness);
    return liveness;
  }

  /**
   * Sets the liveness status of this node.
   * 
   * @param newStatus NodeHandle.LIVENESS_ALIVE, NodeHandle.LIVENESS_SUSPECTED, NodeHandle.LIVENESS_FAULTY, NodeHandle.LIVENESS_UNKNOWN
   */
  private void setLiveness(int newLiveness) {
    if ((liveness >= NodeHandle.LIVENESS_SUSPECTED) &&
        (newLiveness == NodeHandle.LIVENESS_SUSPECTED)) {
          System.out.println("Attempting to downgrade faultyness without setting to alive command rejected");    
          Thread.dumpStack();
      return;  
    } 
    liveness = newLiveness;    
    updateLivenessListeners();  
  }
  
  private boolean failedDuringOpen = false;

  protected void failedDuringOpen() {
    failedDuringOpen = true;
    checkDead();
  }

  /**
   * Starts pinging the remote node if we're not already pinging them.
   * @return true if it is going to send another ping
   */
  public boolean checkDead() {
    return checkDead(1);
  }
  
  public boolean checkDead(int powerOffset) {
    //assertSelectorThread();
    if (!scm.pastryNode.isAlive()) return false;

    if (powerOffset < 1) {
      throw new RuntimeException("Invalid Power Offset "+powerOffset);
    }
    
    if (address == null) {
      //System.out.println("checkDead called with null address");
      return false;
    }    
    
    if (deadChecker == null) { 
      //System.out.println(this+" checking dead");
      deadChecker = new DeadChecker(address, pingManager, powerOffset);
      deadChecker.start();
      return true;
    } else {
      deadChecker.updatePowerOffset(powerOffset);    
      return false;
    }
  }
  
  /**
   * Called after failing both an ack and a ping.  The synchronization here
   * is trickey.  We don't want to be holding our lock when rerouting because 
   * it may call into another CM and this has potential for a deadlock.
   *
   */
  protected void markSuspected() {
    markSuspectedTime = System.currentTimeMillis();
    timesMarkedSuspected++;
//    System.out.println(this+"markSuspected()");
    setLiveness(NodeHandle.LIVENESS_SUSPECTED);

    rerouteApropriateMessagesFromQueueAndPendingAcks(); // will only reroute route messages, and not delete anything else
  }
  
  long markSuspectedTime = 0;
  
  /**
   * Called when a node is to be declared faulty.
   *
   */
  protected void markDead() {
    long susTime = System.currentTimeMillis() - markSuspectedTime;
    if (LOG_LOW_LEVEL)
      System.out.println(this+"markDead() after being suspected for "+susTime);
    timesMarkedDead++;
    if (SocketPastryNodeFactory.churn && isInLeafSet()) {
//      if (!scm.pastryNode.isAlive())
//        Thread.dumpStack();
      if (scm.pastryNode.isAlive()) {
        System.out.println(this+" marking UNREACHABLE");
      }
      setLiveness(NodeHandle.LIVENESS_UNREACHABLE);
    } else {
      if (SocketPastryNodeFactory.churn)
        System.out.println(this+" marking FAULTY");
      setLiveness(NodeHandle.LIVENESS_FAULTY);
      scm.markDead((SocketNodeHandle)snh.get());
    }
    if (deadChecker != null) {
      deadChecker.cancel();
      deadChecker.tries = NUM_PING_TRIES;
    }
    rerouteApropriateMessagesFromQueueAndPendingAcks(); // will reroute all route messages and throw out everything else
    if (controlSocketManager != null) {
      controlSocketManager.close();
    }
    if (dataSocketManager != null) {
      dataSocketManager.close();
    }
  }


  public void upgradeUnreachableToFaulty() {
    if (liveness == NodeHandle.LIVENESS_UNREACHABLE) {    
      System.out.println(this+".upgratdeUnreachableToFaulty()");
      setLiveness(NodeHandle.LIVENESS_FAULTY);    
    }
  }
    
  protected boolean isInLeafSet() {
    NodeHandle nh = getNodeHandle();
    if (nh == null) return false;
    return scm.isInLeafSet(nh);
  }
  
  /**
   * called by DeadChecker.pingResponse(), message received.
   * Notifies us that we are still hearing from the remote node.
   *
   * @param powerOffset this is the power of the time we need to wait
   */
  protected void markAlive(int powerOffset) {    
    failedDuringOpen = false;
    initializeSocketsIfNeeded();
    if (liveness > NodeHandle.LIVENESS_SUSPECTED) {
      System.out.println(this+"markAlive()");
    }

    if (deadChecker != null) {
      deadChecker.cancel();
      deadChecker = null;
    }
    if (getLiveness() == NodeHandle.LIVENESS_ALIVE) {
      refreshPendingAcks(powerOffset);
      return;        
    } 

    // true if we are switching from faulty to alive
    boolean needToNotifySCM = false;
    if (liveness == NodeHandle.LIVENESS_FAULTY) {
      needToNotifySCM = true;
    }
    setLiveness(NodeHandle.LIVENESS_ALIVE);    
    if (needToNotifySCM) {
      scm.markAlive((SocketNodeHandle)snh.get());
    }
  }
  
  private void initializeSocketsIfNeeded() {
    if ((controlSocketManager != null) && // I have a socket manager
        (!controlSocketManager.sentAddress) && // I haven't initialized it
        (controlSocketManager.ctor == 2)) { // I initiated the socket
          controlSocketManager.tryToCreateConnection();
    }
    if ((dataSocketManager != null) && // I have a socket manager
        (!dataSocketManager.sentAddress) && // I haven't initialized it
        (dataSocketManager.ctor == 2)) { // I initiated the socket
          dataSocketManager.tryToCreateConnection();
    }
  }
  
  private void refreshPendingAcks(int powerOffset) {
    Iterator i = pendingAcks.values().iterator();
    while(i.hasNext()) {
      AckTimeoutEvent ate = (AckTimeoutEvent)i.next();
      ate.schedule(powerOffset);
    }
  }

  /**
   * Reroutes all apropriate messages.  In the queue and the pending
   * acks structure. 
   * 
   * What is apropriate?
   * 1) It is a route message.  
   * 2) If we are "suspected" and the message is not going to the 
   * target.
   * 3) If we are "faulty"
   * 
   * Calls reroute() for all messages that are apropriate to reroute 
   * based on our current liveness.  It deletes them from their structure if 
   * reroute() returns true;
   */  
  private void rerouteApropriateMessagesFromQueueAndPendingAcks() {
    Iterator i = pendingAcks.keySet().iterator();
    ArrayList list = new ArrayList();
    while (i.hasNext()) {
      Object key = i.next();
      AckTimeoutEvent ate = (AckTimeoutEvent)pendingAcks.get(key);
      if (reroute(ate.msg)) {
        list.add(key);
      }
    }
    i = list.iterator();
    while(i.hasNext()) {
      pendingAcks.remove(i.next());        
    }
    i = controlQueue.iterator();
    while (i.hasNext()) {
      if (reroute(((MsgEntry)i.next()).message)) {
        i.remove();
      }
    }
  }
  
  /**
   * A helper function for rerouteApropriateMessagesFromQueueAndPendingAcks().
   * 
   * returns true if message should be removed from the CM (because
   * it is going to be rerouted, or it is not "re-routable."
   * false means that the message needs to remain in queue until 
   * later
   * true means the message can be deleted from the queue
   * 
   * This method uses rerouteExtractedMessage to do the labor.  It extracts the 
   * message from a SocketTransportMessage if that is the kind of message we 
   * were passed.
   * 
   * @param o the message scheduled for reroute
   * @return true if the message was rerouted and can be deleted from the queue/pendingack
   */
  private boolean reroute(Message o) {  
    try {  
      if (o != null) {
        //System.out.println("Should reroute "+o+" but dropping.");
        if (o instanceof SocketTransportMessage) {
          return rerouteExtractedMessage(((SocketTransportMessage)o).msg);
        } else {        
          return rerouteExtractedMessage(o);
        }
      } else {
        return true;
      }
    } catch (TooManyMessagesException tmme) {
      debug("WARNING dropping "+o+" because could not reroute:"+tmme);
      return true; // throwing message away
    }
  }
  
  /** 
   * Helper method for reroute.  See that method's 
   * documentation.
   * 
   * @param o the message scheduled for reroute
   * @return true if the message can be deleted from the queue/pendingack
   */
  private boolean rerouteExtractedMessage(Message o) {
    if (o instanceof RouteMessage) {
      RouteMessage rm = (RouteMessage)o;
      if (liveness >= NodeHandle.LIVENESS_SUSPECTED) {
        if (rm.getOptions().rerouteIfSuspected()) {
          scm.reroute(rm);
          return true;
        } // else we are suspected, but not supposed to rerouteIfSuspected()        
      }
      switch (liveness) {
        case NodeHandle.LIVENESS_SUSPECTED:
          return false;
        case NodeHandle.LIVENESS_UNREACHABLE:
          messageNotSent(rm.unwrap(),SocketPastryNode.EC_NODE_UNREACHABLE);
          return true;
        case NodeHandle.LIVENESS_FAULTY:
          scm.reroute(rm);
          return true;        
        default: // alive
          return false; // not supposed to reroute if alive
      
      }            
    } else {
      switch (liveness) {
        case NodeHandle.LIVENESS_SUSPECTED:
          return false; // don't throw out anything except route messages
        case NodeHandle.LIVENESS_UNREACHABLE:
          messageNotSent(o,SocketPastryNode.EC_NODE_UNREACHABLE);
          return true; // throw out junk in the queue
        case NodeHandle.LIVENESS_FAULTY:
          messageNotSent(o,SocketPastryNode.EC_CONNECTION_FAULTY);
          return true; // throw out junk in the queue
        default:
          return false; // not supposed to reroute if alive
      }
    }
  }

  /**
   * The number of control/route messages that have not been sent.
   * @return The number of control/route messages that have not been sent.
   */
  public int queueSize() {
    return controlQueue.size();
  }
  
  /**
   * The number of in-flight messages that have not been 
   * acknowledged.
   * 
   * @return number of in-flight messages that have not been acknowledged
   */
  public int pendingAcksSize() {
    return pendingAcks.size();
  }


  /**
   * Can be called to make sure we are on the selector thread
   */
  public void assertSelectorThread() {
    if (Thread.currentThread() != SelectorManager.getSelectorManager()) {
      Thread.dumpStack();
    }
  }

  /**
   * yee ol' toString() method.
   */
  public String toString() {    
    return "CM<"+scm.addressString()+">=><"+address+">:"+id+"@"+epoch;
  }

  /**
   * Logging for debug output.
   * @param s the debug output string to log
   */
  private void debug(String s) {
    scm.debug(s);
  }


// ******************* inner classes ********************  
  /**
   * This class is used to find out timing information for the queue.  It
   * doesn't have a specific use other than experimentation.  It will
   * likely be removed in the future.  But all messages in queue are
   * wrapped in a MsgEntry.
   * 
   */
  class MsgEntry {
    public Message message;
    public long timeQueued;
    public MsgEntry(Message m) {
     this.message = m;
     timeQueued = System.currentTimeMillis(); 
    }    
  }

  /**
   * This is a TimerTask that determines if a remote node has become FAULTY
   * It will carry the node from the state LIVENESS_ALIVE to SUSPECTED(_FAULTY)
   * after NUM_TRIES_SUSPECTED_FAULTY to LIVENESS_FAULTY at 
   * NUM_TRIES_DECLARE_FAULTY.
   *
   */
  class DeadChecker implements PingResponseListener {
    int tries = 0;
    InetSocketAddress address;
    DeadCheckerTimer myTimer;
    boolean cancelled = false;
    long nextTime = 0;
    int powerOffset;
    long lastTimeScheduled = 0;
    boolean alreadyRaised = false;
        
    /**
     * Constructor for DeadChecker.
     *
     * @param address my address
     * @param numTries number of tries before declaring dead
     * @param mgr the ping manager to use to ping the remote node
     */
    public DeadChecker(InetSocketAddress address, PingManager mgr, int powerOffset) {
      //System.out.println("Registered DeadChecker("+address+")");
      this.address = address;
      this.powerOffset = powerOffset;
      if (!scm.pastryNode.isAlive()) {
        Thread.dumpStack();
      }
    }

    public void start() {
      if (tries > 0) {
        throw new RuntimeException("start() called more than onece");
      }
      tries = 1;
      schedule(powerOffset);
      SocketNodeHandle snh = getNodeHandle();
			if (snh != null) {
	      pingManager.forcePing(snh, this, true);
			} else {
			  // we're about to be collected
//			  System.out.println("Connection manager has no node handle1.");	
			}
    }
    
    SocketNodeHandle myHandle = null;
    void preventCollection(boolean b) {
    	if (b) {
    	  myHandle = getNodeHandle();	
    	} else {
    		myHandle = null;
    	}
    }

		/**
     * called by pingManager when the ping was received
     *
     * @param address the address we got the ping response from 
     * @param RTT the round trip time (proximity in millis)
     * @param timeHeardFrom the last time we heard from the guy 
     * (not useful here because we are calling forcePing) 
     */
    public void pingResponse(
                             SocketNodeHandle snh,
                             long RTT,
                             long timeHeardFrom) {
      //System.out.println("Terminated DeadChecker(" + address + ") due to ping.");     
      markAlive(powerOffset); // will terminate this deadchecker      
    }

    class DeadCheckerTimer extends TimerTask {
      /**
       * Main processing method for the DeadChecker object gets called by the
       * timer.
       */
      public void run() {
        // don't need to call invoke because timer/selector are the same
//        SelectorManager.getSelectorManager().invoke(new Runnable() {
//          public void run() {
            runOnSelector();
//          }
//        });            
      }
    }

    

    private void schedule(int powerOffset) {
      if (myTimer != null) {
        throw new RuntimeException("alreadyScheduled");
      }
      lastTimeScheduled = System.currentTimeMillis();
      int delay = getRTOPower(powerOffset);
      this.powerOffset=powerOffset;
      myTimer = new DeadCheckerTimer();
      scm.scheduleTask(myTimer,delay);      
      nextTime = delay+System.currentTimeMillis();
    }

    /**
     * Technically, we should make the code wait longer after runOnSelector() 
     * before calling another ping, but we can add that later if necessary.
     * The consequence of not doing this is getting an extra ping even though
     * we know the connection is starting to bog down.
     * 
     * @param powerOffset
     */
    public void updatePowerOffset(int powerOffset) {
      if (powerOffset > this.powerOffset) {
        this.powerOffset = powerOffset; // the alreadyAdded is because it we increment powerOffset if we have a failure,
        // but we may skip a pO, so we need to keep track if we already raised pO        
        alreadyRaised = true;
      }
    }

    public void cancel() {
      cancelled = true;
      if (myTimer != null) {
        myTimer.cancel();
        myTimer = null;
      }
    }

    private void runOnSelector() {
      myTimer = null;
      if (cancelled) return;
      if (tries < NUM_PING_TRIES) {
        if (tries == NUM_PINGS_SUSPECT_FAULTY) {          
          markSuspected();
        }
        tries++;
        if (alreadyRaised) {
          alreadyRaised = false;
        } else {
          powerOffset++;
          if (powerOffset > 6) 
            powerOffset = 6;
        }
        
        SocketNodeHandle snh = getNodeHandle();
        if (snh != null) {
	        pingManager.forcePing(snh, DeadChecker.this, true);
	        schedule(powerOffset);
        } else {
          // we're about to be collected
//  			  System.out.println("Connection manager has no node handle2.");	
        }
      } else {
        markDead();
      }    
    }
  }
    
  /**
   * A wrapper for a SocketTransportMessage and a TimerTask that waits for an ack.
   * Constructed in registerMessageForAck.  Removed on ackReceived.  Note: don't
   * remove the message on AckFailed.  We don't want to send more messages, and we may
   * need to reroute this one.  Wait until SUSPECTED(_FAULTY) to remove the message.
   * 
   * @author Jeff Hoye
   */
  class AckTimeoutEvent {
    /**
     * The ack number we are waiting for.
     */
    public int ackNum;

    /**
     * The message we sent.
     */
    SocketTransportMessage msg;

    /**
     * When we sent it.
     */
    public long startTime;

    /**
     * The TimerTask to call runOnSelector.
     */
    public AckTimeoutTimer myTimer;

    /** 
     * Whether the task has been cancelled.
     */
    boolean cancelled = false;

    /**
     * When we expect to get the run method called.
     */
    long nextTime = 0;
    
    /**
     * The RTO powerOffset associated with this task.
     */
    int powerOffset = 0;

    /** 
     * We need to reschedule this task multiple times, but a TimerTask 
     * can only be scheduled once.  So we have to use the has-a pattern.
     * @author Jeff Hoye
     */
    class AckTimeoutTimer extends TimerTask {
      /**
       * Reschedules the task onto the selector thread.
       */
      public void run() {
        runOnSelector();
      }
    }


    /**
     * Constructor for ActTimeoutEvent.
     *
     * @param msg the SocketTransportMessage we are waiting for.
     */
    public AckTimeoutEvent(SocketTransportMessage msg) {
      this.ackNum = msg.seqNumber;
      this.msg = msg;
      startTime = System.currentTimeMillis();
    }

    /**
     * Called when AckTimeoutEvent has taken too long for an ack.
     * calls ackNotReceived().
     */
    public void runOnSelector() {            
      if (cancelled) return;
      powerOffset++;
      if (powerOffset > 6) 
        powerOffset = 6;
      myTimer = null;
      ackNotReceived(AckTimeoutEvent.this, powerOffset);
    }
      
    /**
     * Schedules this task based on the RTO and powerOffset.
     * @param powerOffset
     */
    private void schedule(int powerOffset) {
      if (myTimer != null) return;

      int delay = getRTOPower(powerOffset);
      this.powerOffset=powerOffset;
      myTimer = new AckTimeoutTimer();
      scm.scheduleTask(myTimer,delay);      
      nextTime = delay+System.currentTimeMillis();
    }

    /**
     * Cancels this task.
     */
    public void cancel() {
      cancelled = true;
      if (myTimer != null) {
        myTimer.cancel();
        myTimer = null;
      }
    }

    /**
     * Debugging method to determine how long we have been waiting for this ack.
     * @return the time since we scheduled this message for writing.
     */
    public long elapsedTime() {
      return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Returns how long until we expect to hear about a failure.
     * @return
     */
    public long timeToFailure() {
      return nextTime - System.currentTimeMillis();
    }
    
    /**
     * yee ol' toString() method.
     */
    public String toString() {
      return "ATE<"+ackNum+">:"+msg+":elapsed:"+elapsedTime()+" ttf:"+timeToFailure()+ " powerOffset:"+powerOffset;
    }
  }



  // ******************* Trace Functions ***************************8

  /**
   * For debugging.
   * Used to store the last functions called.  (@see #addLastFxn)
   */
//  LinkedList lastFxns = new LinkedList();
  /**
   * Used for debugging.  Records the last funcitons called.
   * @param m String name of the funciton being called.
   */
//  public void addLastFxn(String m) {
//    if (!lastFxns.isEmpty()) {
//      String last = (String)lastFxns.getLast();
//      if (last.equals(m)) {
//        return;
//      }
//    }
//    lastFxns.add(m);
//    if (lastFxns.size() > 5) {
//      lastFxns.removeFirst();
//    }
//  }
  
  /** 
   * For debugging.
   * Outputs the last few funcitons called.
   */
//  public String getLastFxns() {
//    Iterator i = ((Collection)lastFxns.clone()).iterator();
//    String s = "";
//    while(i.hasNext()) {
//      s+=i.next()+",";
//    }
//    return s;
//  }

	public String getStatus() {
    String s1 = null;
    String s2 = null;
    if (controlSocketManager != null) {
      s1 = controlSocketManager.toString();
    }
    
    if (dataSocketManager != null) {
      s2 = dataSocketManager.toString();
    }
    
    
    
    String s3 = "";//+"lastFxn:"+getLastFxns();
  
    s3+= " timesMissedAck:"+timesMissedAck+" timesSusp:"+timesMarkedSuspected+" timesDead:"+timesMarkedDead;
    s3+= " RTT:"+RTT+" RTO:"+RTO+" timeToSusp:"+getTimeToSuspected(RTO)+" timeFaulty:"+getTimeToFaulty(RTO);
    
    s3 += " queue:"+controlQueue.size();
    s3 += " acks:";
    
    
    s3 += pendingAcks.size()+"[";
    if (!pendingAcks.isEmpty()) {
      try {
        s3+=pendingAcks.entrySet().iterator().next();
      } catch (Exception e) {
        
      }
    }
    
    s3 += "]";
    s3 += " deadChecker:";
    DeadChecker dc = deadChecker;
    if (dc != null) {
      s3+=dc.tries+",";
      long time = dc.nextTime-System.currentTimeMillis();
      s3+=time+"canceled:"+dc.cancelled;      
    } else {
      s3+="null";
    }
    
    if (lastAckSent != null) {
      s3+=" lastAckSent:"+lastAckSent.get();      
    } else {
      s3+=" lastAckSent:null";
    }
    
		return this+":"+s3+",  control:"+s1+",  data:"+s2+"  "+scm.socketPoolManager;
	}

  /**
   * closes the socketManagers, called by finalize()
   */
  public void close() {
    if (dataSocketManager!=null) {
      dataSocketManager.close();
    }
    if (controlSocketManager!=null) {
      controlSocketManager.close();
    }
    if (deadChecker != null) {
      deadChecker.cancel();
      deadChecker = null;
    }
  }
  
  /**
   * accessor for the local handle
   * @return SocketNodeHandle of the local node
   */
  public SocketNodeHandle getLocalNodeHandle() {
    return scm.getLocalNodeHandle();
  }

  /**
   * needs to close down remaining connections
   */
	protected void finalize() throws Throwable {
    if (LOG_LOW_LEVEL)
      System.out.println(this+"CM.finalize()");
    close();
		super.finalize();
	}

  LinkedList livenessListeners = new LinkedList();
	public void removeLivenessListener(LivenessListener ll) {
    livenessListeners.remove(ll);		
	}

	/**
	 * @param manager
	 */
	public void addLivenessListener(LivenessListener ll) {
    livenessListeners.add(ll);
	}
  
  private void updateLivenessListeners() {    
    if (livenessListeners.size() == 0) return;
    Iterator i = ((Collection)livenessListeners.clone()).iterator();
    while(i.hasNext()) {
      LivenessListener ll = (LivenessListener)i.next();
      ll.updateLiveness((SocketNodeHandle)snh.get(),liveness);
    }
  }

	/**
	 * 
	 */
	public SocketNodeHandle getNodeHandle() {
    return (SocketNodeHandle)snh.get();
	}
}

  


