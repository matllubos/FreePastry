/**
 * "FreePastry" Peer-to-Peer Application Development Substrate Copyright 2002,
 * Rice University. All rights reserved. Redistribution and use in source and
 * binary forms, with or without modification, are permitted provided that the
 * following conditions are met: - Redistributions of source code must retain
 * the above copyright notice, this list of conditions and the following
 * disclaimer. - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. -
 * Neither the name of Rice University (RICE) nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. This software is provided by RICE and the
 * contributors on an "as is" basis, without any representations or warranties
 * of any kind, express or implied including, but not limited to,
 * representations or warranties of non-infringement, merchantability or fitness
 * for a particular purpose. In no event shall RICE or contributors be liable
 * for any direct, indirect, incidental, special, exemplary, or consequential
 * damages (including, but not limited to, procurement of substitute goods or
 * services; loss of use, data, or profits; or business interruption) however
 * caused and on any theory of liability, whether in contract, strict liability,
 * or tort (including negligence or otherwise) arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 */

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
 * @version $Id$
 * @author Alan Mislove
 */
public class SelectorManager {

  // the amount of time to wait during a selection (ms)
  /**
   * DESCRIBE THE FIELD
   */
  public int SELECT_WAIT_TIME = 100;
  Object selectorLock = new Object();
  Iterator selectorIterator;

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
   * @param node The pastry node this SocketManager is serving
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
   * DESCRIBE THE METHOD
   *
   * @param skh DESCRIBE THE PARAMETER
   */
  public void registerForWakeup(NeedsWakeUp skh) {
    synchronized (needToWakeUp) {
      needToWakeUp.add(skh);
    }
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param skh DESCRIBE THE PARAMETER
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
              key.cancel();
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
      }
      // while(alive)

      // shutdown code
      synchronized (selectorLock) {
        keys = selector.keys().toArray();

        for (int i = 0; i < keys.length; i++) {
          key = (SelectionKey) keys[i];
          key.channel().close();
          key.cancel();
        }

        selector.close();
      }
    } catch (Throwable e) {
      System.out.println("ERROR (run): " + e);
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
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(pastryNode.getNodeId() + " (M): " + s);
    }
  }
}
