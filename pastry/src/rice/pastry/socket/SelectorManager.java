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

package rice.pastry.socket;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import rice.pastry.Log;
import rice.pastry.dist.NodeIsDeadException;

/**
 * This class is the class which handles the selector, and listens for activity.
 * When activity occurs, it figures out who is interested in what has happened,
 * and hands off to that object.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SelectorManager {

  // the selector used
  private Selector selector;

  // the pastry node
  private SocketPastryNode node;

  // used for testing (simulating killing a node)
  private boolean alive = true;

  // a list of the invocations that need to be done in this thread
  private LinkedList invocations;

  public Thread selectorThread;

  public long lastHeartBeat = 0;
  public int HEART_BEAT_TIME = 60000;
  public int maxInvocations = 0;
  public int totalNumInvocations = 0;
  public int numTimesNotRemoveKeyInRow = 0;
  public int numTimesNotRemoveKey = 0;
  public int totalNumTimesNotRemoveKey = 0;
  public static boolean recordStats = false;
  public static boolean useHeartbeat = false;


  // *********************** debugging statistics ****************
  /**
   * Records how long it takes to receive each type of message.
   */
  public Hashtable stats = new Hashtable();
  
  public void addStat(String s, long time) {
    if (!recordStats) return;
    Stat st = (Stat)stats.get(s);
    if (st == null) {
      st = new Stat(s);
      stats.put(s,st);
    }
    st.addTime(time);
  }

  public void printStats() {
    if (!recordStats) return;
    synchronized(stats) {
      Enumeration e = stats.elements();
      while(e.hasMoreElements()) {
        Stat s = (Stat)e.nextElement(); 
        System.out.println("  "+s);
      }
    }
  }

  /**
   * A statistic as to how long user code is taking to process a paritcular message.
   * 
   * @author Jeff Hoye
   */
  class Stat {
    int num = 0;
    String name = null;
    long totalTime = 0;
    long maxTime = 0;
    
    public Stat(String name) {
      this.name = name;
    }
    
    public void addTime(long t) {
      num++;
      totalTime+=t;
      if (t > maxTime) {
        maxTime = t;  
      }
    }
    
    public String toString() {
      long avgTime = totalTime/num;
      return name+" num:"+num+" total:"+totalTime+" maxTime:"+maxTime+" avg:"+avgTime;
    }
  }

  /**
   * Constructor.
   *
   * @param node The pastry node this SocketManager is serving
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


  SocketCollectionManager scm = null;
  void setSocketCollectionManager(SocketCollectionManager scm) {
    this.scm = scm;
  }

  /**
   * This method starts the socket manager listening for events. It is designed
   * to be started when this thread's start() method is invoked.
   */
  public void run() {
    selectorThread = Thread.currentThread();
    
    try {
      debug("Socket Manager starting...");

      // loop while waiting for activity
      while (alive && (select() >= 0)) {
        if (stall) {
            try {
                Thread.sleep(STALL_TIME);
                stall = false;
            } catch (InterruptedException ie) {}
        }
        if (useHeartbeat) {
        long curTime = System.currentTimeMillis();
          if (curTime - lastHeartBeat > HEART_BEAT_TIME) {
            long diff = (curTime-lastHeartBeat)-HEART_BEAT_TIME;
            lastHeartBeat = curTime;
            curTime/=1000;
            System.out.println("SM.run(): "+new Date()+","+scm.addressString()+": lostTime:"+diff+" total:"+totalNumTimesNotRemoveKey+ " maxInvokes:"+maxInvocations+" totalInvokes:"+totalNumInvocations+" spm:"+scm.socketPoolManager+" waitingToAccept:"+waitingToAccept());
            System.out.println(scm.socketPoolManager.getStatus());
            Iterator i = scm.getConnectionManagers().iterator(); 
            while(i.hasNext()) {
              ConnectionManager cm = (ConnectionManager)i.next();
              System.out.println("  "+cm.getStatus());
            }            
            printStats();
            System.out.println();
          }        
        }

        SelectionKey[] keys = selectedKeys();
        for (int i = 0; i < keys.length; i++) {
//          SelectionKey k = keys[i];
//          if (k.isValid()) {
//            System.out.println("Selected key "+System.identityHashCode(k)+":"+k.isValid()+":"+k.interestOps());
//          } else {
//            System.out.println("Selected key "+System.identityHashCode(k)+":"+k.isValid());
//          }
//          selector.selectedKeys().remove(keys[i]);

          SelectionKeyHandler skh = (SelectionKeyHandler) keys[i].attachment();
          if (skh != null) {

            // read
            if (keys[i].isValid() && keys[i].isReadable()) {
              if (skh.read(keys[i])) {
                selector.selectedKeys().remove(keys[i]);                                
              } 
            }
  
            // write
            if (keys[i].isValid() && keys[i].isWritable()) {
              if (skh.write(keys[i])) {
                selector.selectedKeys().remove(keys[i]);                                
              } 
            }

            // accept
            if (keys[i].isValid() && keys[i].isAcceptable()) {
              //try { Thread.sleep(5000); } catch (InterruptedException ie){}
              if (acceptorKey != null) {
                numTimesNotRemoveKey++;                
                scm.disableAccept(); // gets enabled when we acceptSocket()
              }
              acceptorKey = keys[i]; // gets set back to null in acceptSocket()
              scm.socketPoolManager.requestAccept(); // calls acceptSocket() now or later
            }

            // connect
            if (keys[i].isValid() && keys[i].isConnectable()) {
              if (skh.connect(keys[i])) {
                selector.selectedKeys().remove(keys[i]);                                
              } 
            }
/*
            if (acceptorKey != null) {
              scm.disableAccept();
              acceptRejected++;

              selector.selectedKeys().remove(keys[i]);                                
              removedOneKey = true;
            } 
            */
          } else {
            keys[i].channel().close();
            //System.out.println("Selector cancelling key "+System.identityHashCode(keys[i]));
            keys[i].cancel();
          }
        } // for

        if (alive)
          doInvocations();      
      }

      closeChannels();

    } catch (NodeIsDeadException nide) {
      if (alive) {
        nide.printStackTrace();        
      } 
      try {
        closeChannels();      
      } catch (Throwable e) {
        System.out.println("ERROR (SocketManager.run): " + e);
        e.printStackTrace();
      }
    } catch (Throwable e) {
      System.out.println("ERROR (SocketManager.run): " + e);
      e.printStackTrace();
    }
  }

  private void closeChannels() throws IOException {
    SelectionKey[] keys = keys();

    System.out.println(this+" killed");
    for (int i = 0; i < keys.length; i++) {
      try {
        keys[i].channel().close();
        keys[i].cancel();
      } catch (IOException e) {
        System.out.println("IOException " + e + " occured while trying to close and cancel key.");
      }
    }
    selector.close();
  }
  
  SelectionKey acceptorKey = null;
  
  public void acceptSocket() {
    scm.enableAccept();
    SelectionKey tempKey = acceptorKey;
    acceptorKey = null;

    boolean removeKey = false;
    if (tempKey != null) {
      SelectionKeyHandler skh = (SelectionKeyHandler)tempKey.attachment();
      if (skh != null && tempKey.isValid() && tempKey.isAcceptable()) {
        if (skh.accept(tempKey)) {
          removeKey = true;
        } 
      } else {
        removeKey = true;      
      }
  
      if (removeKey) {
        selector.selectedKeys().remove(tempKey);                                      
      }
    }
  }

  public boolean waitingToAccept() {
    return acceptorKey != null;
  }


  /**
   * To be used for testing purposes only - kills the socket client by shutting
   * down all outgoing sockets and stopping the client thread.
   */
  public void kill() {
    //System.out.println("SelectorManager.kill()");
    // mark socketmanager as dead
    alive = false;
    selector.wakeup();
    if (stall) {
      stall = false;
      selectorThread.interrupt();
    }
  }

  /**
   * Returns the selector used by this SelectorManager. NOTE: All operations
   * using the selector which change the Selector's keySet MUST synchronize on
   * the Selector itself- i.e.: synchronized (selector) {
   * channel.register(selector, ...); }
   *
   * @return The Selector used by this object.
   */
  Selector getSelector() {
    return selector;
  }

  Hashtable invokeTypes = new Hashtable();
  /**
   * This method schedules a runnable task to be done by the selector thread
   * during the next select() call. All operations which modify the selector
   * should be done using this method, as they must be done in the selector
   * thread.
   *
   * @param d The runnable task to invoke
   */
  public void invoke(Runnable d) {
    synchronized(invocationLock) {
      invocations.add(d);
    }
    selector.wakeup();
  }


  Object invocationLock = new Object();

  /**
   * Method which invokes all pending invocations. This method should *only* be
   * called by the selector thread.
   */
  private void doInvocations() {
    LinkedList ll;
    synchronized(invocationLock) {            
      ll = invocations;
      invocations = new LinkedList();
    }
    int size = ll.size();
    totalNumInvocations+=size;
    if (size > maxInvocations) {
      maxInvocations = size;
    }
    while (ll.size() > 0) {
      ((Runnable) ll.removeFirst()).run();
    }

  }

  /**
   * Selects on the selector, and returns the result. Also properly synchronizes
   * around the selector
   *
   * @return DESCRIBE THE RETURN VALUE
   * @exception IOException DESCRIBE THE EXCEPTION
   */
  private int select() throws IOException {
    if (invocations.size() > 0) {
      return selector.selectNow();
    }

    return selector.select();
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
    if (Log.ifp(8)) {
      System.out.println(node.getNodeId() + " (M): " + s);
    }
  }

  // ****************** For testing only ********************
  public boolean isAlive() {
    return alive;
  }

  /**
   * The state of the thread.  (stalled?)
   */
  boolean stall = false;

  public String toString() {
    return "Selector for "+node.getLocalHandle();
  }

  

  /**
   * The time to stall the thread when stalling.
   */
  int STALL_TIME = 15000000;
    /**
     * Stalls the thread for STALL_TIME time.  This is a different way of simulating
     * killing, or temporarily bringing the node down.
     */
    public void stall(int time) {
      STALL_TIME = time;
        stall = true;
    }

		/**
		 * @return true if the current thread == the selectorThread
		 */
		public boolean isSelectorThread() {
      return (Thread.currentThread() == selectorThread);
		}

//	protected void finalize() throws Throwable {
//    System.out.println(this+".finalize()");
//		super.finalize();
//	}

}
