/*
 * Created on Mar 31, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimerTask;

import rice.pastry.messaging.Message;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ConnectionManager {
  SocketManager controlSocketManager;
  SocketManager dataSocketManager;
  PingManager pingManager;
  
  SocketCollectionManager scm;
  InetSocketAddress address;
  
  int status;

  long PING_DELAY = 3000;
  int NUM_PING_TRIES = 3;

  long ACK_DELAY = 1500;

  // internal list of objects waiting to be written
  private LinkedList queue;

  /**
   *  the maximum length of the queue
   */
  public static int MAXIMUM_QUEUE_LENGTH = 128;
  
	/**
	 * 
	 */
	public ConnectionManager(SocketCollectionManager scm, InetSocketAddress address) {
    this.scm = scm;   
    this.address = address;
    this.pingManager = scm.getPingManager();
    status = 0; 
    queue = new LinkedList();
	}

  public int getStatus() {
    return status;
  }

  public Object sendLock = new Object();
  
  public void send(Message message, int type) {
//    System.out.println("ENQ2:CM.send("+message+")");
//    synchronized(sendLock) {
    if (type == SocketCollectionManager.TYPE_CONTROL) {  
      if (controlSocketManager == null) {
        debug("No control connection open to " + address + " - opening one");
        openSocket(SocketCollectionManager.TYPE_CONTROL);        
      }

      if (controlSocketManager != null) {
        //debug("Found connection open to " + address + " - sending now");
        
//        if (sm.isProbing()) {
//          reroute(address, message);
//        } else {
          //controlSocketManager.send(message);
          enqueue(message);
          moveMessagesToControlSM();
//        }
      } else {
        debug("ERROR: Could not connection to remote address " + address + " rerouting message " + message);
        scm.reroute(address, message);
      }
    } else { // type == TYPE_DATA
      if (dataSocketManager == null) {
        debug("No data connection open to " + address + " - opening one");
        openSocket(SocketCollectionManager.TYPE_DATA);        
      }

      if (dataSocketManager != null) {
        //debug("Found connection open to " + address + " - sending now");
        
        dataSocketManager.send(message);        
        scm.getSocketPoolManager().socketUpdated(dataSocketManager);
      } else {
        debug("ERROR: Could not connect to remote address " + address + " for data, dropping message " + message);
      }      
    }
//    } // synch
  } // send()



	/**
   * Adds an object to this SocketChannelWriter's queue of pending objects to
   * write. This methos is synchronized and therefore safe for use by multiple
   * threads.
   *
   * @param o The object to be written.
   * @return DESCRIBE THE RETURN VALUE
   */
  public boolean enqueue(Object o) {
    synchronized (queue) {
      if (queue.size() < MAXIMUM_QUEUE_LENGTH) {
        addToQueue(o);
        return true;
      } else {
        System.err.println(System.currentTimeMillis()+":"+scm.pastryNode.getNodeId()+"->"+ address + " (W): Maximum TCP queue length reached - message " + o + " will be dropped.");
        return false;
      }
    }
  }

  /**
   * Adds an entry into the queue, taking message prioritization into account
   *
   * @param o The feature to be added to the ToQueue attribute
   */
  private void addToQueue(Object o) {
    if (o instanceof Message) {
      boolean priority = ((Message) o).hasPriority();

      if ((priority) && (queue.size() > 0)) {
        for (int i = 1; i < queue.size(); i++) {
          Object thisObj = queue.get(i);

          if ((thisObj instanceof Message) && (!((Message) thisObj).hasPriority())) {
            debug("Prioritizing socket message " + o + " over message " + thisObj);

            //System.out.println("ENQ2.5:CM.addToQueue("+o+"):"+i);
            queue.add(i, o);
            return;
          }
        }
      }
    }

    //System.out.println("ENQ2.5:CM.addToQueue("+o+"):last");
    queue.addLast(o);
  }

  private void debug(String s) {
    scm.debug(s);
  }

  /**
   * Method which opens a socket to a given remote node handle, and updates the
   * bookkeeping to keep track of this socket
   *
   * @param address DESCRIBE THE PARAMETER
   */
  protected void openSocket(int type) {
    SocketManager sm = null;
    try {
      sm = new SocketManager(address, scm, this, type);
      checkDead();
      scm.getSocketPoolManager().socketOpened(sm);
    } catch (IOException e) {
      System.out.println("GOT ERROR " + e + " OPENING DATA SOCKET!");
      e.printStackTrace();
      if (sm != null) {
        sm.close();
      }
    }            

    if (type == SocketCollectionManager.TYPE_DATA) {
      dataSocketManager = sm;
    } else {
      controlSocketManager = sm;
    }
  }

  /**
   * @param manager
   * @param type
   */
  public void socketClosed(SocketManager manager, int type) {
    if (type == SocketCollectionManager.TYPE_CONTROL) {
      controlSocketManager = null;
    } else {
      dataSocketManager = null;
    }
    scm.getSocketPoolManager().socketClosed(manager);
  }  


  protected void checkDead() {
    if (address == null) {
      //System.out.println("checkDead called with null address");
      return;
    }
    
    DeadChecker checker = new DeadChecker(address, NUM_PING_TRIES, pingManager);
    scm.scheduleTask(checker, PING_DELAY, PING_DELAY);
    pingManager.forcePing(address, checker);
  }

  public void acceptSocket(SocketManager sm) {
    if (sm.getType() == SocketCollectionManager.TYPE_CONTROL) {
      if (controlSocketManager == null) {
        controlSocketManager = sm;
        sm.setConnectionManager(this);
        scm.getSocketPoolManager().socketOpened(sm);
      } else {
        controlSocketManager = handleCollision(controlSocketManager, sm);
      }
    } else {
      if (dataSocketManager == null) {
        dataSocketManager = sm;
        sm.setConnectionManager(this);
        scm.getSocketPoolManager().socketOpened(sm);
      } else {
        dataSocketManager = handleCollision(dataSocketManager, sm);
      }
    }
  }

  protected SocketManager handleCollision(SocketManager existing, SocketManager newMgr) {
    debug("ERROR: Request to record socket opening for already-open socket to " + address + " of type:"+existing.getType());
    String local = "" + scm.localAddress.getAddress().getHostAddress() + scm.localAddress.getPort();
    String remote = "" + address.getAddress().getHostAddress() + address.getPort();
  
    debug("RESOLVE: Comparing " + local + " and " + remote);
  
    if (remote.compareTo(local) < 0) {
      debug("RESOLVE: Cancelling existing data connection to " + address);  
      scm.getSocketPoolManager().socketClosed(existing);
      scm.getSocketPoolManager().socketOpened(newMgr);
      existing.close();
      newMgr.setConnectionManager(this);
      return newMgr;
    } else {
      debug("RESOLVE: Cancelling new connection to " + address);
      return existing;
    }
  }
  
  
  /**
   * DESCRIBE THE CLASS
   *
   * @version $Id$
   * @author jeffh
   */
  class DeadChecker extends TimerTask implements PingResponseListener {
    int tries = 1;
    // already called once
    int NUM_TRIES;
    InetSocketAddress address;
    PingManager manager;

    /**
     * Constructor for DeadChecker.
     *
     * @param address DESCRIBE THE PARAMETER
     * @param numTries DESCRIBE THE PARAMETER
     * @param mgr DESCRIBE THE PARAMETER
     */
    public DeadChecker(InetSocketAddress address, int numTries, PingManager mgr) {
      //System.out.println("Registered DeadChecker("+address+")");
      this.address = address;
      manager = mgr;
      this.NUM_TRIES = numTries;
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param address DESCRIBE THE PARAMETER
     * @param RTT DESCRIBE THE PARAMETER
     * @param timeHeardFrom DESCRIBE THE PARAMETER
     */
    public void pingResponse(
                             InetSocketAddress address,
                             long RTT,
                             long timeHeardFrom) {
      //System.out.println("Terminated DeadChecker(" + address + ") due to ping.");
      cancel();
    }

    /**
     * Main processing method for the DeadChecker object
     */
    public void run() {
      if (tries < NUM_TRIES) {
        //System.out.println("DeadChecker("+address+") pinging again."+tries);
        tries++;
        manager.forcePing(address, this);
      } else {
        //System.out.println("DeadChecker("+address+") marking node dead.");
        manager.doneProbing(address);
        scm.markDead(address);
        cancel();
      }
    }
  }

    
  
	/**
	 * @param i
	 */
	public void ackReceived(int i, SocketManager.AckTimeoutEvent ate) {
    moveMessagesToControlSM();
    long elapsedTime = -1;
    if (ate!=null) {
      elapsedTime = ate.elapsedTime();
    }
//    System.out.println("CM.ackReceived("+i+","+elapsedTime+")");
	}

  /**
   * 
   */
  private void moveMessagesToControlSM() {
    synchronized(queue) {
      while (!queue.isEmpty() && controlSocketManager.canWrite()) {
        Object o = queue.removeFirst();
//        System.out.println("ENQ3:CM.moveMessagesToControlSM("+o+")");

        controlSocketManager.send(o);
      }
      /*
      Iterator i = queue.iterator();
      while(i.hasNext()) {
        System.out.println("  CM.moveMessagesToControlSM("+i.next()+")"); 
      }*/
    }
        
  }

	/**
	 * @return
	 */
	public int size() {
		return queue.size();
	}

}

  


