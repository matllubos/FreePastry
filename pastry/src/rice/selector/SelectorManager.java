/*************************************************************************

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

package rice.selector;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.*;

/**
 * This class is the class which handles the selector, and listens for activity.
 * When activity occurs, it figures out who is interested in what has happened,
 * and hands off to that object.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SelectorManager extends Thread {

  public static int TIMEOUT = 500;

  /**
   * The static selector which is used by all applications
   */
  private static SelectorManager manager;

  // the underlying selector used
  private Selector selector;

  // a list of the invocations that need to be done in this thread
  protected LinkedList invocations;

  // the list of handlers which want to change their key
  private HashSet modifyKeys;

  private Timer timer;

  private TimerThread timerThread;
  
  boolean alive = true;

  /**
   * Constructor, which is private since there is only one selector per JVM.
   */
  protected SelectorManager() {
    super("Main Selector Thread");
    this.invocations = new LinkedList();
    this.modifyKeys = new HashSet();

    // attempt to create selector
    try {
      selector = Selector.open();
    } catch (IOException e) {
      System.out.println("SEVERE ERROR (SelectorManager): Error creating selector " + e);
    }
    timer = new Timer(selector);
    timerThread = timer.thread;
    start();
  }

  /**
   * Returns the SelectorManager applications should use.
   *
   * @return The SelectorManager which applications should use
   */
  public static SelectorManager getSelectorManager() {
    if (manager != null)
      return manager;

    synchronized (SelectorManager.class) {
      if (manager != null)
        return manager;

      String s = System.getProperty("PROFILE_SELECTOR_MANAGER");
      if ((s != null) && (s != "")) {
        System.out.println("Using Profile Selector");
        manager = new ProfileSelector();        
      } else {
        manager = new SelectorManager();
      }
      return manager;
    }
  }

  /**
   * Registers a new channel with the selector, and attaches the given SelectionKeyHandler
   * as the handler for the newly created key.  Operations which the hanlder is interested
   * in will be called as available.
   *
   * @param channel The channel to regster with the selector
   * @param handler The handler to use for the callbacks
   * @param ops The initial interest operations
   * @return The SelectionKey which uniquely identifies this channel
   */
  public SelectionKey register(SelectableChannel channel, SelectionKeyHandler handler, int ops) throws IOException {
    return channel.register(selector, ops, handler);
  }

  /**
   * This method schedules a runnable task to be done by the selector thread
   * during the next select() call. All operations which modify the selector
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
   * Adds a selectionkey handler into the list of handlers which wish to change
   * their keys. Thus, modifyKeys() will be called on the next selection
   * operation
   *
   * @param key The key which is to be chanegd
   */
  public synchronized void modifyKey(SelectionKey key) {
    modifyKeys.add(key);
    selector.wakeup();
  }
  
  /**
   * This method is to be implemented by a subclass to do some task each loop.
   */
  protected void onLoop() {
  }

  /**
   * This method starts the socket manager listening for events. It is designed
   * to be started when this thread's start() method is invoked.
   */
  public void run() {
    try {
      debug("SelectorManager starting...");

      // loop while waiting for activity
      while (alive) {
//        doSelect
//        select();
        timerThread.mainLoopHelper(this);
        onLoop();
        doInvocations();

        SelectionKey[] keys = selectedKeys();

        for (int i = 0; i < keys.length; i++) {
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

      for (int i = 0; i < keys.length; i++) {
        try {
          keys[i].channel().close();
          keys[i].cancel();
        } catch (IOException e) {
          System.out.println("IOException " + e + " occured while trying to close and cancel key.");
        }
      }

      selector.close();
    } catch (Throwable t) {
      System.out.println("ERROR (SelectorManager.run): " + t);
      t.printStackTrace(System.out);
      System.exit(-1);
    }
  }

  /**
   * Method which invokes all pending invocations. This method should *only* be
   * called by the selector thread.
   */
  private void doInvocations() {
    Runnable run = getInvocation();
    while (run != null) {
      try {
        run.run();
      } catch (Exception e) {
        System.err.println("Invoking runnable caused exception " + e + " - continuing");
        e.printStackTrace();
      }
      
      run = getInvocation();
    }

    SelectionKey key = getModifyKey();
    while (key != null) {
      if (key.isValid() && (key.attachment() != null))
        ((SelectionKeyHandler) key.attachment()).modifyKey(key);

      key = getModifyKey();
    }
  }

  /**
   * Method which synchroniously returns the first element off of the
   * invocations list.
   *
   * @return An item from the invocations list
   */
  private synchronized Runnable getInvocation() {
    if (invocations.size() > 0)
      return (Runnable) invocations.removeFirst();
    else
      return null;
  }

  /**
   * Method which synchroniously returns on element off
   * of the modifyKeys list
   *
   * @return An item from the invocations list
   */
  private synchronized SelectionKey getModifyKey() {
    if (modifyKeys.size() > 0) {
      Object result = modifyKeys.iterator().next();
      modifyKeys.remove(result);
      return (SelectionKey) result;
    } else {
      return null;
    }
  }

  /**
   * Selects on the selector, and returns the result. Also properly synchronizes
   * around the selector
   *
   * @return DESCRIBE THE RETURN VALUE
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  int select(int time) throws IOException {
    if (time > TIMEOUT)
      time = TIMEOUT;
    try {
      if ((time <= 0) || (invocations.size() > 0) || (modifyKeys.size() > 0)) {
        return selector.selectNow();
      }

      synchronized (selector) {
        return selector.select(time);
      }
    } catch (IOException e) {
      if (e.getMessage().indexOf("Interrupted system call") >= 0) {
        System.out.println("Got interrupted system call, continuing anyway...");
        return 1;
      } else {
        throw e;
      }
    }
  }

  /**
   * Selects all of the keys on the selector and returns the result as an array
   * of keys.
   *
   * @return The array of keys
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private SelectionKey[] keys() throws IOException {
    return (SelectionKey[]) selector.keys().toArray(new SelectionKey[0]);
  }

  /**
   * Selects all of the currenlty selected keys on the selector and returns the
   * result as an array of keys.
   *
   * @return The array of keys
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private SelectionKey[] selectedKeys() throws IOException {
    return (SelectionKey[]) selector.selectedKeys().toArray(new SelectionKey[0]);
  }

  /**
   * DESCRIBE THE METHOD
   *
   * @param s DESCRIBE THE PARAMETER
   */
  private void debug(String s) {
    if (rice.pastry.Log.ifp(8)) {
      System.out.println("(SelectorManager): " + s);
    }
  }

	public Timer getTimer() {
		return timer;
	}

	/**
	 * @return
	 */
	public static boolean isSelectorThread() {
    return Thread.currentThread() == manager;
	}
}
