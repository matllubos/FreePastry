
package rice.pastry.wire;
import java.io.*;
import java.net.*;

import java.nio.channels.*;
import java.util.*;

import rice.pastry.*;

/**
 * This class is the class which handles the selector, and listens for activity.
 * When activity occurs, it figures out who is interested in what has happened,
 * and hands off to that object.
 *
 * @author Alan Mislove, Jeff Hoye
 */
public class SelectorManager {

  /**
   * the amount of time to wait during a selection (ms)
   */
  public int SELECT_WAIT_TIME = 100;
  
  /**
   * used to synchronize getting jobs from the 
   * selector
   */
  Object selectorLock = new Object();

  /**
   * pending jobs
   */
  Iterator selectorIterator;

  /**
   * the objects that need a routine wakeup call.
   */
  ArrayList needToWakeUp = new ArrayList();

  // the selector used
  private Selector selector;

  // the pastry node
  private WirePastryNode pastryNode;

  // used for testing (simulating killing a node)
  private boolean alive = true;

  /**
   * Constructor.
   *
   * @param node The pastry node this SelectorManager is serving
   */
  public SelectorManager(WirePastryNode node) {
    pastryNode = node;

    // attempt to create selector
    try {
      selector = Selector.open();
    } catch (IOException e) {
      System.out.println("ERROR (SocketClient): Error creating selector " + e);
    }
  }

  /**
   * Returns the selector used by this SelectorManager. NOTE: All operations
   * using the selector which change the Selector's keySet MUST synchronize on
   * the Selector itself. (i.e.: synchronized (selector) {
   * channel.register(selector, ...); } ).
   *
   * @return The Selector used by this object.
   */
  public Selector getSelector() {
    return selector;
  }

  /**
   * Gets the Alive attribute of the SelectorManager object
   *
   * @return The Alive value
   */
  public boolean isAlive() {
    return alive;
  }

  /**
   * required to register all objects that need a routine
   * wakeup call on the Selector thread.
   *
   * @param skh The object to call wakeup on.
   */
  public void registerForWakeup(NeedsWakeUp skh) {
    synchronized (needToWakeUp) {
      needToWakeUp.add(skh);
    }
  }

  /**
   * used to unregister an object for wakeup on the 
   * Selector thread.
   *
   * @param skh The object to stop calling wakeup on.
   */
  public void unregisterForWakeup(NeedsWakeUp skh) {
    synchronized (needToWakeUp) {
      needToWakeUp.remove(skh);
    }
  }

  /**
   * This method starts the datagram manager listening for incoming datagrams.
   * It is designed to be started when this thread's start() method is invoked.
   * In this method, the DatagramManager blocks while waiting for activity. It
   * listens on its specified port for incoming datagrams, and processes any
   * write() requests from pastry node handles.
   */
  public void run() {
    try {
      debug("Manager starting...");

      selectorIterator = selector.selectedKeys().iterator();

      // loop while waiting for activity
      Object[] keys = null;
      SelectionKey key = null;
      SelectionKey last = null;
      int lastType = 0;
      int numTimes = 0;
      int MAXTIMES = 5;

      while (alive) {

        // this synchronized block prevents the user from killing the node mid send
        key = getNextSelectedKey();
        if (key != null) {


          synchronized (key) {
            SelectionKeyHandler skh = (SelectionKeyHandler) key.attachment();
            if (skh != null) {
              // accept
              if (key.isValid() && key.isAcceptable()) {
                skh.accept(key);
              }
              // connect
              if (key.isValid() && key.isConnectable()) {
                skh.connect(key);
              }

              // read
              if (key.isValid() && key.isReadable()) {
                //System.out.println("******************* Entering Read");
                skh.read(key);
                //System.out.println("******************* Exiting Read");
              }

              // write
              if (key.isValid() && key.isWritable()) {
                //System.out.println("******************* Entering Write");
                skh.write(key);
                //System.out.println("******************* Exiting Write");
              }
            } else {
              debug("Found key witout attachment!");
              //key.cancel();
            }
          }
          // synch(key)
        }
        // if (key != null)

        synchronized (needToWakeUp) {
          Iterator i = needToWakeUp.iterator();
          while (i.hasNext()) {
            NeedsWakeUp nwu = (NeedsWakeUp) i.next();
            nwu.wakeup();
          }
        }
        // synch (needToWakeUp)
      } // while(alive)

      // shutdown code
      synchronized (selectorLock) {
        keys = selector.keys().toArray();

        for (int i = 0; i < keys.length; i++) {
          key = (SelectionKey) keys[i];
          try {
            key.channel().close();
            key.cancel();
          } catch (IOException ioe) {
            System.err.println("Error Closing socket from "+pastryNode+"to "+key.attachment()+":"+ioe);
            ioe.printStackTrace();
          }
        }

//        System.out.println("Selector:open:"+selector.isOpen()+","+selector);
        if (selector.isOpen()) {
          try {
            selector.close();
          } catch (IOException ioe) {
            System.out.println("SelectorManager:closing:"+ioe);
          }
        }
      }
    } catch (Throwable e) {
      System.out.println("ERROR (run): node:"+pastryNode+":" + e);
      e.printStackTrace();
    }
  }

  /**
   * To be used for testing purposes only - kills the socket client by shutting
   * down all outgoing sockets and stopping the client thread.
   */
  public void kill() {
    // mark socketmanager as dead
    alive = false;
  }

  /**
   * Gets the NextSelectedKey attribute of the SelectorManager object
   *
   * @return The NextSelectedKey value
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  SelectionKey getNextSelectedKey() throws IOException {
    synchronized (selectorLock) {
      if (selectorIterator.hasNext()) {
        SelectionKey next = (SelectionKey) selectorIterator.next();
        selectorIterator.remove();
        return next;
      } else {
        selector.select(SELECT_WAIT_TIME);
        selectorIterator = selector.selectedKeys().iterator();
        return null;
      }
    }
  }


  /**
   * general logging method
   *
   * @param s string to log
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(pastryNode.getNodeId() + " (M): " + s);
    }
  }
}
