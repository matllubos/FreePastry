/**************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002, Rice University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither  the name  of Rice  University (RICE) nor  the names  of its
contributors may be  used to endorse or promote  products derived from
this software without specific prior written permission.

This software is provided by RICE and the contributors on an "as is"
basis, without any representations or warranties of any kind, express
or implied including, but not limited to, representations or
warranties of non-infringement, merchantability or fitness for a
particular purpose. In no event shall RICE or contributors be liable
for any direct, indirect, incidental, special, exemplary, or
consequential damages (including, but not limited to, procurement of
substitute goods or services; loss of use, data, or profits; or
business interruption) however caused and on any theory of liability,
whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even
if advised of the possibility of such damage.

********************************************************************************/

package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.channels.*;

import rice.pastry.*;

/**
 * This class is the class which handles the selector, and listens for
 * activity.  When activity occurs, it figures out who is interested in
 * what has happened, and hands off to that object.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class SelectorManager {
  
  // the selector used
  private Selector selector;
  
  // the pastry node
  private SocketPastryNode node;
  
  // used for testing (simulating killing a node)
  private boolean alive = true;
  
  // the amount of time to wait during a selection (ms)
  public int SELECT_WAIT_TIME = 1000;
  
  // a list of the invocations that need to be done in this thread
  private LinkedList invocations;
  
  /**
    * Constructor.
   *
   * @param node The pastry node this SocketManager is serving
   * @param port The port number this Datagram Manager should run on
   */
  public SelectorManager(SocketPastryNode node) {
    this.node = node;
    this.invocations = new LinkedList();
    
    // attempt to create selector
    try {
      selector = Selector.open();
    } catch (IOException e) {
      System.out.println("ERROR (SelectorManager): Error creating selector " + e);
    }
  }
  
  /**
   * Returns the selector used by this SelectorManager.
   *
   * NOTE: All operations using the selector which change the Selector's
   * keySet MUST synchronize on the Selector itself- i.e.:
   *    synchronized (selector) {
   *      channel.register(selector, ...);
   *    }
   *
   * @return The Selector used by this object.
   */
  Selector getSelector() {
    return selector;
  }
  
  /**
   * This method schedules a runnable task to be done by the selector thread
   * during the next select() call.  All operations which modify the selector
   * should be done using this method, as they must be done in the selector 
   * thread.
   *
   * @param d The runnable task to invoke
   */
  public synchronized void invoke(Runnable d) {
    invocations.add(d);
    selector.wakeup();
  }
  
  /**
   * Method which invokes all pending invocations.  This method should *only* be
   * called by the selector thread.
   */
  private synchronized void doInvocations() {
    while (invocations.size() > 0) {
      ((Runnable) invocations.removeFirst()).run();
    }
  }
  
  /**
   * Selects on the selector, and returns the result.  Also properly synchronizes
   * around the selector
   *
   * @param the result from selector.select
   */
  private int select() throws IOException {
    synchronized (selector) {
      return selector.select(SELECT_WAIT_TIME);
    }
  }
  
  /**
   * Selects all of the keys on the selector and returns the result
   * as an array of keys.
   *
   * @return The array of keys
   */
  private SelectionKey[] keys() throws IOException {
    synchronized (selector) {
      return (SelectionKey[]) selector.keys().toArray(new SelectionKey[0]);
    }
  }
  
  /**
   * Selects all of the currenlty selected keys on the selector and returns the result
   * as an array of keys.
   *
   * @return The array of keys
   */
  private SelectionKey[] selectedKeys() throws IOException {
    synchronized (selector) {
      return (SelectionKey[]) selector.selectedKeys().toArray(new SelectionKey[0]);
    }
  }
  
  /**
   * This method starts the socket manager listening for events.
   * It is designed to be started when this thread's start() method is invoked.
   */
  public void run() {
    try {
      debug("Socket Manager starting...");
      
      // loop while waiting for activity
      while (alive && (select() >= 0)) {
        
        doInvocations();
        
        SelectionKey[] keys = selectedKeys();
          
        for (int i=0; i<keys.length; i++) {
          selector.selectedKeys().remove(keys[i]);
          
          SelectionKeyHandler skh = (SelectionKeyHandler) keys[i].attachment();
          
          if (skh != null) {
            // accept
            if (keys[i].isValid() && keys[i].isAcceptable()) {
              skh.accept(keys[i]);
            }
            
            // connect
            if (keys[i].isValid() && keys[i].isConnectable()) {
              skh.connect(keys[i]);
            }
            
            // read
            if (keys[i].isValid() && keys[i].isReadable()) {
              skh.read(keys[i]);
            }
            
            // write
            if (keys[i].isValid() && keys[i].isWritable()) {
              skh.write(keys[i]);
            }
          } else {
            keys[i].channel().close();
            keys[i].cancel();
          }
        }
      }
    
      SelectionKey[] keys = keys();
        
      for (int i=0; i<keys.length; i++) {
        keys[i].channel().close();
        keys[i].cancel();
      }
      
      selector.close();
    } catch (Throwable e) {
      System.out.println("ERROR (SocketManager.run): " + e);
      e.printStackTrace();
    }
  }
  
  /**
   * To be used for testing purposes only - kills the socket client
   * by shutting down all outgoing sockets and stopping the client
   * thread.
   */
  public void kill() {
    // mark socketmanager as dead
    alive = false;
  }
  
  private void debug(String s) {
    if (Log.ifp(8))
      System.out.println(node.getNodeId() + " (M): " + s);
  }
}