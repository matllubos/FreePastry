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

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * This class is the class which handles the selector, and listens for activity.
 * When activity occurs, it figures out who is interested in what has happened,
 * and hands off to that object.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SelectorManager extends Thread implements Timer {

  // the maximal time to sleep on a select operation
  public static int TIMEOUT = 500;

  // The static selector manager which is used by all applications
  private static SelectorManager manager;

  // the underlying selector used
  protected Selector selector;

  // a list of the invocations that need to be done in this thread
  protected LinkedList invocations;

  // the list of handlers which want to change their key
  protected HashSet modifyKeys;

  // the list of keys waiting to be cancelled
  protected HashSet cancelledKeys;

  /**
   * Constructor, which is private since there is only one selector per JVM.
   */
  protected SelectorManager(boolean profile) {
    super("Main Selector Thread");
    this.invocations = new LinkedList();
    this.modifyKeys = new HashSet();
    this.cancelledKeys = new HashSet();
    
    // attempt to create selector
    try {
      selector = Selector.open();
    } catch (IOException e) {
      System.out.println("SEVERE ERROR (SelectorManager): Error creating selector " + e);
    }

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
        manager = new SelectorManager(false);
      }
      return manager;
    }
  }
  
  /**
   * Method which asks the Selector Manager to add the given key to the cancelled 
   * set.  If noone calls register on this key during the rest of this select() operation,
   * the key will be cancelled.  Otherwise, it will be returned as a result of the
   * register operation.
   *
   * @param key The key to cancel
   */
  public void cancel(SelectionKey key) {
    if (key == null)
      throw new NullPointerException();
    
    cancelledKeys.add(key);
  }
  
  /**
   * Utility method which returns the SelectionKey attached to the given channel, if 
   * one exists
   *
   * @param channel The channel to return the key for
   * @return The key
   */
  public SelectionKey getKey(SelectableChannel channel) {
    return channel.keyFor(selector);
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
    if ((channel == null) || (handler == null))
      throw new NullPointerException();
    
    SelectionKey key = channel.register(selector, ops, handler);
    cancelledKeys.remove(key);
    
    return key;
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
    if (d == null)
      throw new NullPointerException();
    
    invocations.add(d);
    selector.wakeup();
  }
  
  public int getNumInvocations() {
    return invocations.size();
  }

  /**
   * Adds a selectionkey handler into the list of handlers which wish to change
   * their keys. Thus, modifyKeys() will be called on the next selection
   * operation
   *
   * @param key The key which is to be chanegd
   */
  public synchronized void modifyKey(SelectionKey key) {
    if (key == null)
      throw new NullPointerException();
    
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
      //System.out.println("SelectorManager starting...");
      debug("SelectorManager starting...");

      // loop while waiting for activity
      while (true) {
        // NOTE: This is so we aren't always holding the selector lock when we get context switched 
        Thread.yield();
//        try { Thread.sleep(100); } catch (Exception ioe) {}
        executeDueTasks();
        onLoop();
        doInvocations();
        doSelections();
        synchronized(selector) {          
          int selectTime = SelectorManager.TIMEOUT;   
          if (timerQueue.size() > 0) {
            TimerTask first = (TimerTask)timerQueue.first(); 
            selectTime = (int)(first.nextExecutionTime - System.currentTimeMillis());
          }
          
          select(selectTime);
          
          if (cancelledKeys.size() > 0) {
            Iterator i = cancelledKeys.iterator();
          
            while (i.hasNext())
              ((SelectionKey) i.next()).cancel();
          
            cancelledKeys.clear();
            
            // now, hack to make sure that all cancelled keys are actually cancelled (dumb)
            selector.selectNow();
          }
        }
      }
    } catch (Throwable t) {
      System.out.println("ERROR (SelectorManager.run): " + t);
      t.printStackTrace(System.out);
      System.exit(-1);
    }
  }

  protected void doSelections() throws IOException {
    SelectionKey[] keys = selectedKeys();

    for (int i = 0; i < keys.length; i++) {
      selector.selectedKeys().remove(keys[i]);

      synchronized(keys[i]) {
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
  }

  /**
   * Method which invokes all pending invocations. This method should *only* be
   * called by the selector thread.
   */
  protected void doInvocations() {
    Iterator i;
    synchronized (this) {
      i = new ArrayList(invocations).iterator();
      invocations.clear();
    }

    while (i.hasNext()) {
      Runnable run = (Runnable)i.next();
      try {
        run.run();
      } catch (Exception e) {
        System.err.println("Invoking runnable caused exception " + e + " - continuing");
        e.printStackTrace();
      }
    }

    synchronized (this) {
      i = new ArrayList(modifyKeys).iterator();
      modifyKeys.clear();
    }
    
    while (i.hasNext()) {
      SelectionKey key = (SelectionKey) i.next();
      if (key.isValid() && (key.attachment() != null))
        ((SelectionKeyHandler) key.attachment()).modifyKey(key);
    }
  }

  /**
   * Method which synchroniously returns the first element off of the
   * invocations list.
   *
   * @return An item from the invocations list
   */
  protected synchronized Runnable getInvocation() {
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
  protected synchronized SelectionKey getModifyKey() {
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
      if ((time <= 0) || (invocations.size() > 0) || (modifyKeys.size() > 0)) 
        return selector.selectNow();
      
      wakeupTime = System.currentTimeMillis() + time;
      return selector.select(time);
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
  protected SelectionKey[] selectedKeys() throws IOException {
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

  public TreeSet timerQueue = new TreeSet();

  /**
   * @return
   */
  public static boolean isSelectorThread() {
    return Thread.currentThread() == manager;
  }
  
  public void schedule(TimerTask task, long delay) {
    task.nextExecutionTime = System.currentTimeMillis() + delay;    
    addTask(task);
  }  

  public void schedule(TimerTask task, Date time) {
    task.nextExecutionTime = time.getTime();
    addTask(task);
  }
  
  public void schedule(TimerTask task, long delay, long period) {
    task.nextExecutionTime = System.currentTimeMillis() + delay;
    task.period = (int)period;
    addTask(task);
  }
  
  public void schedule(TimerTask task, Date firstTime, long period) {
    task.nextExecutionTime = firstTime.getTime();
    task.period = (int)period;
    addTask(task);
  }
  
  public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
    task.nextExecutionTime = System.currentTimeMillis() + delay;
    task.period = (int)period;
    addTask(task);
  }
  
  public void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
    task.nextExecutionTime = firstTime.getTime();
    task.period = (int)period;
    addTask(task);
  }

  long wakeupTime = 0;

  private void addTask(TimerTask task) {
    //System.out.println("AddTask("+task+") "+(task.scheduledExecutionTime() - System.currentTimeMillis()));
    synchronized(selector) {
      boolean ret = timerQueue.add(task);  
      if (!ret) {
        Thread.dumpStack();
      }
    }
    // need to interrupt thread if waiting too long in selector    
    if (wakeupTime >= task.scheduledExecutionTime())
      selector.wakeup();
  }

  protected void executeDueTasks() {
    //System.out.println("SM.executeDueTasks()");
    long now = System.currentTimeMillis();
    ArrayList executeNow = new ArrayList();
    
    // step 1, fetch all due timers
    synchronized(selector) {
      boolean done = false;
      while(!done) {
        if (timerQueue.size() > 0) {
          TimerTask next = (TimerTask)timerQueue.first(); 
          if (next.nextExecutionTime <= now) {
            executeNow.add(next);
            //System.out.println("Removing:"+next);
            timerQueue.remove(next);          
          } else {
            done = true;
          }
        } else {
          done = true; 
        }
      }
    }
    
    
    // step 2, execute them all
    // items to be added back into the queue
    ArrayList addBack = new ArrayList();
    Iterator i = executeNow.iterator();
    while(i.hasNext()) {
      TimerTask next = (TimerTask)i.next(); 
      try {
        //System.out.println("SM.Executing "+next);
        if (next.execute()) {
          addBack.add(next); 
        }
      } catch (Exception e) {
        e.printStackTrace(); 
      }
    }
    
    // step 3, add them back if necessary
    synchronized(selector) {
      i = addBack.iterator();
      while(i.hasNext()) {
        TimerTask tt = (TimerTask)i.next();
        //System.out.println("SM.addBack("+tt+")");
        timerQueue.add(tt);
      }
    }  
  }

  public Timer getTimer() {
    return this;
  } 
}
