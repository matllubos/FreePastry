/*
 * Created on Mar 31, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.TimerTask;

import rice.pastry.NodeHandle;

/**
 * This class is in charge of "enforcing" the limitiaton on the number 
 * of sockets specified in SocketCollectionManager.MAX_OPEN_SOCKETS.
 * 
 * @author Jeff Hoye
 */
public class SocketPoolManager {

  /**
   * the number of sockets where we start closing other sockets
   */
  public static int MAX_OPEN_SOCKETS = 200;

  /**
   * Sockets waiting to get opened.  We use a set and queue to get O(1) check.
   */
  HashSet waitingSet = new HashSet();
  /**
   * Sockets waiting to get opened.  We use a set and queue to get O(1) check.
   */
  LinkedList waitingQueue = new LinkedList(); 
  
  /**
   * Set of SocketManagers we gave permission to open.
   * These sockets are in the state where they have connected
   * to the remote server socket, but have not yet been accepted.
   */
  HashSet permittedSet = new HashSet();
	
  /**
   * the linked list of open sockets
   */
  private LinkedList queue = new LinkedList(); // last is most recently used

  SocketCollectionManager scm;
  SelectorManager selectorManager;

  Random r = new Random();

	public SocketPoolManager(SocketCollectionManager scm, SelectorManager selectorMgr) {
    this.scm = scm;
    this.selectorManager = selectorMgr;
	}

  // ******************** SocketManager Lifecycle *****************
  /**
   * Method which is designed to be called by node handles when they wish to
   * open a socket to their remote node. This method will determine if another
   * node handle needs to disconnect.  If this is the case, we only want to 
   * close sockets on the Selector Thread, so we register for invocation, and 
   * will disconnect ejected node handles until we hit our limit.
   *
   * @param manager The new manager.
   */
  protected void socketOpened(SocketManager manager) {
    
    //System.out.println("SPM.socketOpened("+manager+")");
//    if (manager.address == null) {
      //Thread.dumpStack();
//    }
    if (queue.contains(manager)) {
      System.out.println("SPM opened twice : "+manager);      
      Thread.dumpStack();
    }    
    manager.openedTrace = new RuntimeException("opened here");

    assertNotClosed(manager);
    permittedSet.remove(manager);
    queue.addLast(manager);
    manager.markActive();
    transferIdleToWaiting();
  }

  /**
   * Will call selectorManager.acceptSocket when it can.
   * @return
   */
  public void requestAccept() {
    if (availablePermits() > 0) {
      selectorManager.acceptSocket();
    }
    if (!closeNextIdle()) { // will recursively accept
      scheduleWakeupTask();        
    }
    //System.out.println("SPM.canAccept() returning false.");
    //if (true) return true;
    
//    return false;
  }

  public Collection getIdles(int numIdles) {
    LinkedList idles = new LinkedList();
    Iterator i = queue.iterator();
    while(i.hasNext()) {
      SocketManager sm = (SocketManager)i.next();
      if (sm.isIdle()) {
        idles.add(sm);
        if (idles.size() >= numIdles) {
          return idles;
        }
      }
    }
    return idles;
  }
  
  public int countIdles() {
    return getIdles(queue.size()).size();
  }
  
  public boolean closeNextIdle() {
    Iterator i = queue.iterator();
    while(i.hasNext()) {
      SocketManager sm = (SocketManager)i.next();
      if (sm.isIdle()) {
        sm.close();
        return true;
      }
    }
    return false;    
  }

  /**
   * Method which is designed to be called whenever a node has network activity.
   * This is used to determine which nodes should be disconnected, should it be
   * necessary (implementation of a LRU stack).
   *
   * @param sm The socket manager that got updated
   */
  protected void socketUpdated(SocketManager sm) {
    assertNotClosed(sm);
    if (!queue.contains(sm)) {
      Thread.dumpStack();
    }
    queue.remove(sm);
    queue.addLast(sm);
  }
  
  /**
   * This method returns true if you can open the socket now
   * if you can't, it adds you to waiting.
   * @param sm
   * @return
   */
  protected void requestToOpenSocket(SocketManager sm) {
    assertNotClosed(sm);
    if (sm.connectionManager.getLiveness() >= NodeHandle.LIVENESS_FAULTY) {
      return;  // This dude is dead
    }
    if (!waitingSet.contains(sm)) {
      addWaiting(sm);
    }
    transferIdleToWaiting();
  }

  private void assertNotClosed(SocketManager sm) {
    if (sm.closed) {
      throw new RuntimeException("Socket is closed");
    }
  }

  public void addPermitted(SocketManager sm) {
    if (permittedSet.contains(sm)) {
      throw new RuntimeException("SocketManager already given permit");
    }
    permittedSet.add(sm);    
  }

  private int availablePermits() {
    return MAX_OPEN_SOCKETS-(queue.size()+permittedSet.size());
  }

  private void addWaiting(SocketManager sm) {
//    if (printMe(sm))
      //System.out.println("  ** addWaiting("+sm+")");
    waitingSet.add(sm);
    waitingQueue.add(sm);            
  }

  private void printIdleSockets() {
    Iterator i = getIdles(queue.size()).iterator();
    while(i.hasNext()) {
      System.out.println("  idle:"+i.next());
    }
  }

  public void printBusySockets() {
    Iterator i = queue.iterator();
    while(i.hasNext()) {
      SocketManager sm = (SocketManager)i.next();
      if (!sm.isIdle()) {
        System.out.println("  busy:"+sm+"@"+System.identityHashCode(sm));
      }
    }
  }

  private void printWaitingSockets() {
    Iterator i = waitingQueue.iterator();
    while(i.hasNext()) {
      System.out.println("  waiting:"+i.next());
    }
  }

  private SocketManager removeNextWaiting() {
    SocketManager sm = (SocketManager)waitingQueue.removeFirst();
    waitingSet.remove(sm);
    //System.out.println("  ** removeWaiting("+sm+")");
    return sm;
  }


  /**
   * calls SocketManager.close() until we have less than
   * MAX_OPEN_SOCKETS open.
   *
   */  
  private void closeSocketsIfNecessary() {    
    int ap = availablePermits();
    if (ap < 0) {
      ap = -ap;
    } else {
      return;
    }
    Iterator i = getIdles(ap).iterator();
    while (availablePermits() < 0 && i.hasNext()) {
      SocketManager toClose = (SocketManager)i.next();
//      System.out.println("SPM.closeSocketsIfNecessary(): Too many sockets open - closing idle socket " + toClose);
      debug("Too many sockets open - closing idle socket " + toClose);
      toClose.close();
    }
    if (availablePermits() < 0) { // we still haven't closed enough dudes
      System.out.println("SPM.closeSocketsIfNecessary() permits:"+availablePermits() +" waiting: "+waitingSet.size()+":"+waitingQueue.size()+" queue:"+queue.size());       
      Thread.dumpStack();
    }
  }  

  private void transferIdleToWaiting() {
    //System.out.println("transferIdleToWaiting():"+this);
    
    closeSocketsIfNecessary();
    if (printMe(null)) {
      //System.out.println("transferIdleToWaiting():"+this);
      Iterator i = queue.iterator();
      while(i.hasNext()) {
        SocketManager sm = (SocketManager)i.next();
        //System.out.println("  queue:"+sm);
      }
    }
    
    while (itemsAreWaiting() && (availablePermits() > 0)) {
      openNextWaiting();
    }

    
    if (itemsAreWaiting()) { // don't need to loop because recurring
      if (!closeNextIdle()) {
        scheduleWakeupTask();        
      }
    }     
  }

  private boolean itemsAreWaiting() {
    return selectorManager.waitingToAccept() || !waitingSet.isEmpty();
  }

  WakeupTimerTask myTimer;

  private void scheduleWakeupTask() {
    if (myTimer != null) {
      return;      
    }    
    myTimer = new WakeupTimerTask();    
    scm.scheduleTask(myTimer,100);
  }

  class WakeupTimerTask extends TimerTask {
    public void run() {
      selectorManager.invoke(new Runnable() {
				public void run() {
          myTimer = null;
          transferIdleToWaiting();
				}
			});
    }
  }

  private void openNextWaiting() {
    //System.out.println("openNextWaiting() waiting: "+waitingSet.size()+":"+waitingQueue.size()+" idle:"+idleSet.size()+" queue:"+queue.size());
    //printWaitingSockets();
    if (selectorManager.waitingToAccept()) {
      selectorManager.acceptSocket();
    } else {
      SocketManager toOpen = removeNextWaiting();// close idle, open waiting 
      permittedSet.add(toOpen);
      try {
        toOpen.openSocket();
      } catch (IOException ioe) {
        relenquishPermit(toOpen);
        ioe.printStackTrace();
        toOpen.close();
      }    
    }
  }
  
  /**
   * Method which is designed to be called *ONCE THE SOCKET HAS BEEN CLOSED*.
   * This method simply updates the bookeeping, but does not actually close the
   * socket.
   *
   * @param manager the SocketManager that closed
   */
  protected void socketClosed(SocketManager manager) {
    //System.out.println(this+".socketOpened("+manager+")");
    //System.out.println("SPM.socketClosed("+manager+")");
    if (manager.address == null) {
//      Thread.dumpStack();
    } else {
      //System.out.println("SPM.socketClosed("+manager+")");
      //Thread.dumpStack();
    }

    permittedSet.remove(manager);
    queue.remove(manager);
    if (queue.contains(manager)) {
      throw new RuntimeException("socket was in the queue twice "+manager);
    }
    waitingQueue.remove(manager);
    waitingSet.remove(manager);
    transferIdleToWaiting();
  }
  
  /**
   * Logs trase.
   * @param s trase
   */  
  private void debug(String s) {
    scm.debug(s);
  }


  private boolean printMe(SocketManager sm) {
    //if (true) return false;
    if (scm.returnAddress.getPort() == 5009) {
      //System.out.println(this);
      return true;
    }
    return false;
  }

  public String toString() {
    String s = "M:"+MAX_OPEN_SOCKETS+" p:"+availablePermits()+" q:"+queue.size()+" w:"+waitingSet.size()+" w2:"+waitingQueue.size()+" myTimer:"+myTimer;
    return s;
  }

  public String getStatus() {
    String s = "";
    Iterator i = queue.iterator();
    while(i.hasNext()) {
      SocketManager sm = (SocketManager)i.next(); 
      if (sm.connectionManager != null) {
        /*
        boolean con = false;
        if (sm.connected) {
          con = true;
        }
        if (sm.closed) {
          con = false;
        }
        s+="  "+sm.connectionManager.address+":"+con+""+sm.connectionManager.+"\n";
        */
        s+="  "+sm.connectionManager.getStatus();
      } else {
        s+="  null";
      }      
      //s+=sm.whyNotIdle();
      s+="\n";
    }
//    s+=")";
    return s;
  }

  /**
   * Called when a node got a ConnectionFailedException meaning he should
   * retry to connect later because the remote node's backlog is full.
   * @param manager
   */
	public void relenquishPermit(SocketManager manager) {
    permittedSet.remove(manager);
    transferIdleToWaiting();
	}

}
