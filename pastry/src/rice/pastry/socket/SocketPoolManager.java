/*
 * Created on Mar 31, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.socket;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import rice.pastry.NodeHandle;
import rice.selector.SelectionKeyHandler;
import rice.selector.TimerTask;

/**
 * This class is in charge of "enforcing" the limitiaton on the number 
 * of sockets specified in SocketCollectionManager.MAX_OPEN_SOCKETS.
 * 
 * A SocketManager may be constructed, but will have to request to open
 * before simply opening the socket.  He will be put into a queue then opened
 * as soon as there are available socket permits for him to open.  This
 * process starts with requestToOpenSocket()
 * 
 * The same is true of incoming sockets through the ServerSocket.  We can't
 * simply open each socket that comes in, we may exceed the MAX_OPEN_SOCKETS,
 * and we better have similar priority to our own sockets that we want to
 * open, otherwise, we will never be able to send our messages.  This process
 * starts with accept(SelectorKey)
 * 
 * see transferIdleToWaiting() for the core logic of selecting the next
 * socket to allow to open.
 * 
 * SocketPoolManager is also the SelectionKeyHandler for the TCP server 
 * socket.
 * 
 * @author Jeff Hoye
 */
public class SocketPoolManager extends SelectionKeyHandler {

  /**
   * ServerSocketChannel for accepting incoming connections
   */
  SelectionKey key;

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

  /**
   * Backpointer.
   */
  SocketCollectionManager scm;

  /**
   * Constructor for the SocketPoolManager
   * @param scm the SocketCollectionManager associated with this SPM
   */
	public SocketPoolManager(SocketCollectionManager scm) {
    this.scm = scm;
	}

  // ******************** SocketManager Lifecycle *****************

  // ******************** Opening a Socket **********************
  /**
   * Calling this method will result in a call to SocketManager.openSocket().
   * This may be called immeadietly or later, if there are already too many
   * open sockets.  In the second case, it adds you to the waiting set/queue
   * if you are not already waiting.
   * 
   * @param sm the SocketManager to call openSocket() on.
   */
  protected void requestToOpenSocket(SocketManager sm) {
    assertNotClosed(sm);
    if (sm.connectionManager.getLiveness() >= NodeHandle.LIVENESS_UNREACHABLE) {
      return;  // This dude is dead, no sense wasting time with him
    }
    if (!waitingSet.contains(sm)) {
      addWaiting(sm);
    }
    transferIdleToWaiting();
  }

  /**
   * Adds the SocketManager to the queue of SocketManagers waiting to 
   * have openSocket() called on them.
   * @param sm the SocketManager requesting to open a socket.
   */
  private void addWaiting(SocketManager sm) {
//    if (printMe(sm))
      //System.out.println("  ** addWaiting("+sm+")");
    waitingSet.add(sm);
    waitingQueue.add(sm);            
  }

  /**
   * This method closes idle sockets and opens waiting sockets if there are 
   * sockets waiting to be opened.  This method is the heart of the logic 
   * for SocketPoolManager
   * 
   * Here is how the recursion works, there are a few routes from here:
   * transferIdleToWaiting() -> closeSocketsIfNecessary() *-> 
   *   SocketManager.close() -> ConnectionManager.socketClosed() -> 
   *   SocketPoolManager.socketClosed() -> transferIdleToWaiting();
   * transferIdleToWaiting() -> openNextWaiting() -> acceptSocket() ->
   *   socketOpened() -> transferIdleToWaiting();
   * transferIdleToWaiting() *-> openNextWaiting() -> SocketManager.openSocket() ->
   *   SocketManager.createConnection() -> SocketPoolManager.socketOpened() ->
   *   transferIdleToWaiting();
   * transferIdleToWaiting() -> closeNextIdle() -> SocketManager.close() ->
   *   ConnectionManager.socketClosed() -> SocketPoolManager.socketClosed() ->
   *   transferIdleToWaiting();
   * If after all these calls, there are still waiting sockets, it will schedule
   * the wakeup task, which wakes up every 100 millis to call transferIdleToWaiting()
   * until there are no waiting SocketManagers.
   */
  private void transferIdleToWaiting() {
    if (!scm.pastryNode.isAlive()) return;
    //System.out.println("transferIdleToWaiting():"+this);
    
    closeSocketsIfNecessary();
    
    while (scm.pastryNode.isAlive() && itemsAreWaiting() && (availablePermits() > 0)) {
      openNextWaiting();
    }

    
    if (itemsAreWaiting()) { // don't need to loop because recurring
      if (!closeNextIdle()) {
        scheduleWakeupTask();        
      }
    }     
  }
  
  /**
   * Returns true if there are items waiting that have not been 
   * given a permit to open.
   * @return
   */
  private boolean itemsAreWaiting() {
    return waitingToAccept() || !waitingSet.isEmpty();
  }

  /**
   * Returns the number of sockets that are permitted to be opened.
   * This is the MAX_OPEN_SOCKETS-the queue and permittedSet
   * @return the number of sockets we are permitting to open.
   */
  private int availablePermits() {
    return MAX_OPEN_SOCKETS-(queue.size()+permittedSet.size());
  }


  /**
   * Opens the next waiting SocketManager.
   */
  private void openNextWaiting() {
    if (waitingToAccept()) {
      acceptSocket();
    } else {
      SocketManager toOpen = removeNextWaiting(); 
      permittedSet.add(toOpen); // add to the permitted set
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
   * Called when a node failed to connect for some reason.  He is 
   * relenquishing his permit to connect.  So someone else may.
   * @param manager
   */
  public void relenquishPermit(SocketManager manager) {
    permittedSet.remove(manager);
    transferIdleToWaiting();
  }

  /**
   * Returns the next SocketManager waiting to have openSocket() called on it.
   * @return a SocketManger to cal openSocket() on.
   */
  private SocketManager removeNextWaiting() {
    SocketManager sm = (SocketManager)waitingQueue.removeFirst();
    waitingSet.remove(sm);
    //System.out.println("  ** removeWaiting("+sm+")");
    return sm;
  }

  /**
   * Method which is designed to be called by node handles when they have
   * opened a socket to their remote node. 
   *
   * @param manager The new manager.
   */
  protected void socketOpened(SocketManager manager) {
    if (queue.contains(manager)) {
      System.out.println("SPM opened twice : "+manager);      
      Thread.dumpStack();
    }    

    assertNotClosed(manager);
    permittedSet.remove(manager); // remove from the permitted set
    queue.addLast(manager);
    manager.markActive();
    transferIdleToWaiting();
  }

  /**
   * Throws an exception if the SocketManger is closed.
   * @param sm the assumedly open SocketManager
   */
  private void assertNotClosed(SocketManager sm) {
    if (sm.closed) {
      throw new RuntimeException("Socket is closed");
    }
  }

  /**
   * Method which is designed to be called whenever a node has network activity.
   * The method keeps the most active nodes on the back of the queue, so they 
   * are the least likely to be selected to be closed.
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
  
  // ********************** Accepting a Socket ************************
  /**
   * This is non-null when it is waiting to be accepted on.
   */
  SelectionKey acceptorKey = null;
  
  /**
   * Specified by the SelectionKeyHandler interface. Is called when the 
   * ServerSocket has a connectoin to accept.  It is called with a key
   * that represents an incoming connection.  Calling this method notifies
   * the SocketPoolManager that the key would like to be accepted.  It will
   * result in a call to doAccept() when there are permits available to
   * have additional sockets.  This could be while the method is called,
   * or much later.
   * 
   * This method first disables the accept flag on the key, so it 
   * doesn't get repeatedly called while it is waiting to be accepted.
   *
   * @param key The key which is acceptable.
   */
  public void accept(SelectionKey key) {
    if (acceptorKey != null) {
//      numTimesNotRemoveKey++;                
      disableAccept(); // gets enabled when we acceptSocket()
    }
    acceptorKey = key; // gets set back to null in acceptSocket()
    requestAccept(); // calls acceptSocket() now or later
  }

  /**
   * Disables the accept interestOp().  We do this while we 
   * are waiting to accept, so it doesn't keep calling us.
   */
  public void disableAccept() {
    key.interestOps(key.interestOps() & ~SelectionKey.OP_ACCEPT);
  }
  
  /**
   * Will call selectorManager.acceptSocket when it can.
   * @return
   */
  public void requestAccept() {
    if (availablePermits() > 0) {
      acceptSocket();
      return;
    } 
    if (!closeNextIdle()) { // will recursively accept by calling transferIdleToWaiting()
      scheduleWakeupTask();        
    }
  }

  /**
   * There is a remote socket trying to connect.
   * @return
   */
  public boolean waitingToAccept() {
    return acceptorKey != null;
  }

  /**
   * To prevent accepting sockets preventing the ability
   * to open sockets, we throttle accepting by hanging 
   * on to the key that wishes to be accepted until we have
   * a permit available to open.  This is called by
   * openNextWaiting()
   * 
   * This method also makes sure that the key would like
   * to be accepted before accepting.  However
   * the state of the key is set back to acceptable
   * either way.
   */
  private void acceptSocket() {
    if (!scm.pastryNode.isAlive()) return;
    enableAccept();
    SelectionKey tempKey = acceptorKey;
    acceptorKey = null;

    boolean removeKey = false;
    if (tempKey != null) {
      SelectionKeyHandler skh = (SelectionKeyHandler)tempKey.attachment();
      if (skh != null && tempKey.isValid() && tempKey.isAcceptable()) {
        doAccept(tempKey);
      }    
    }
  }

  /**
   * Enables the accept interestOp().  We disable the key this while we 
   * are waiting to accept, so it doesn't keep calling us.  We 
   * reenable it once we can accept the socket.
   */
  public void enableAccept() {
    key.interestOps(key.interestOps() | SelectionKey.OP_ACCEPT);
  }
  
  /**
   * Accepts the socket.
   * @param key
   */
  public void doAccept(SelectionKey key) {
    try {
      socketOpened(new SocketManager(key, scm));
    } catch (IOException e) {
      if (ConnectionManager.LOG_LOW_LEVEL)
        System.out.println("ERROR (accepting connection): " + e + " at "+scm.addressString());
    }    
  }


  // *********************** Closing a Socket *************************
  /**
   * Method which is designed to be called *ONCE THE SOCKET HAS BEEN CLOSED*.
   * This method simply updates the bookeeping, but does not actually close the
   * socket.
   *
   * @param manager the SocketManager that closed
   */
  protected void socketClosed(SocketManager manager) {
    permittedSet.remove(manager);
    queue.remove(manager);
    if (queue.contains(manager)) {
      throw new RuntimeException("socket was in the queue twice "+manager);
    }
    waitingQueue.remove(manager);
    waitingSet.remove(manager);
    transferIdleToWaiting();
  }
  
  // ***** Handling more sockets than available permits (idles sockets) *****
  // note that most of this begins with transferIdleToWaiting()
  /**
   * calls SocketManager.close() until we have less than
   * MAX_OPEN_SOCKETS open.  Hopefully we never exceed the 
   * maximum number of sockets, but this is here to catch 
   * such a bug if it occurs.
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

  
  /**
   * Returns the number of idle connections.
   * @return the number of idle connections.
   */
  public int countIdles() {
    return getIdles(queue.size()).size();
  }
  
  /**
   * Returns up to numIdles idle SocketManagers
   * @param numIdles the maximum number of idle SocketManagers you want
   * @return a collection of idle SocketManagers
   */
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
  
  /**
   * Closes the first idle SocketManager it finds.
   * @return true if it closed an idle SocketManager
   */
  public boolean closeNextIdle() {
    Iterator i = queue.iterator();
    while(i.hasNext()) {
      SocketManager sm = (SocketManager)i.next();
      if (sm.isIdle()) {
        sm.close();//ThisHalf();
        return true;
      }
    }
    return false;    
  }

  /**
   * Schedules the WakeupTimerTask for 100 millis from 
   * now.  This is agressively looking for idle sockets
   * to release so new ones can open.  
   */
  private void scheduleWakeupTask() {
    if (myTimer != null) {
      return;      
    }    
    myTimer = new WakeupTimerTask();    
    scm.scheduleTask(myTimer,100);
  }


  WakeupTimerTask myTimer;
  /**
   * The WakeupTimerTask is engaged when you don't have
   * enough sockets available through MAX_OPEN_SOCKETS, 
   * and thus have 1 or more sockets waiting.
   * 
   * It keeps waking up and trying to find idle connections
   * to close, and activate waiting sockets.
   *
   * It calls transferIdleToWaiting(), which will
   * reschedule the wakeup task if there are still 
   * waiting tasks when it completes, by calling
   * scheduleWakeupTask().
   */
  class WakeupTimerTask extends TimerTask {
		public void run() {
      myTimer = null;
      transferIdleToWaiting();
    }
  }


  // ******************** Debugging methods *****************

  /**
   * Logs trase.
   * @param s trase
   */  
  private void debug(String s) {
    scm.debug(s);
  }

  /**
   * Yee ol' toString()
   */
  public String toString() {
    String s = "M:"+MAX_OPEN_SOCKETS+" p:"+availablePermits()+" q:"+queue.size()+" w:"+waitingSet.size()+" w2:"+waitingQueue.size()+" myTimer:"+myTimer;
    return s;
  }

  /**
   * A debugging method.
   * @return
   */
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
   * Prints out all of the idle SocketManagers.
   */
  private void printIdleSockets() {
    Iterator i = getIdles(queue.size()).iterator();
    while(i.hasNext()) {
      System.out.println("  idle:"+i.next());
    }
  }

  /**
   * Prints out all of the non-idle SocketManagers.
   */
  public void printBusySockets() {
    Iterator i = queue.iterator();
    while(i.hasNext()) {
      SocketManager sm = (SocketManager)i.next();
      if (!sm.isIdle()) {
        System.out.println("  busy:"+sm+"@"+System.identityHashCode(sm));
      }
    }
  }

  /**
   * Prints out all of the SocketManagers who are waiting to have 
   * openSocket() called on them.
   */
  private void printWaitingSockets() {
    Iterator i = waitingQueue.iterator();
    while(i.hasNext()) {
      System.out.println("  waiting:"+i.next());
    }
  }
}
