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

package rice.pastry.testing;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import rice.pastry.Log;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.PastrySeed;
import rice.pastry.dist.DistPastryNode;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNode;
import rice.pastry.socket.SocketPoolManager;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.pastry.wire.Wire;

/**
 * A hello world example for pastry. This is the distributed driver.
 *
 * For example, run with default arguments on two machines, both with args
 * -bootstrap firstmachine. Read the messages and follow the protocols.
 *
 * Either separately or with the above, try -nodes 3 and -nodes 20.
 * Try -msgs 100. Try the two machine configuration, kill it, restart, and
 * watch it join as a different node. Do that a few times, watch LeafSet
 * entries accumulate, then in 30 seconds, watch the maintenance take over.
 *
 * @version $Id$
 *
 * @author Sitaram Iyer
 * @author Peter Druschel
 */

public class DistHelloWorldMultiThread {
  private PastryNodeFactory factory;
  private Vector pastryNodes;
  private Vector helloClients;
  public Random rng;

  private static int port = 5009;
  private static String bshost = null;
  private static int bsport = 5009;
  private static int numnodes = 25;
  private static int nummsgs = 50;
  public static int protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
  private static boolean toFile = false;
  private static boolean useStall = true;
  private static boolean useKill = true;
  private static boolean noKilling = false;
  private static boolean oneThreadPerMessage = false;
  

  /**
   * Constructor
   */
  public DistHelloWorldMultiThread() {
    factory =
      DistPastryNodeFactory.getFactory(
        new RandomNodeIdFactory(),
        protocol,
        port);
    pastryNodes = new Vector();
    helloClients = new Vector();
    rng = new Random(PastrySeed.getSeed());
  }

  /**
   * Gets a handle to a bootstrap node. First we try localhost, to see
   * whether a previous virtual node has already bound itself there.
   * Then we try nattempts times on bshost:bsport. Then we fail.
   *
   * @param firstNode true of the first virtual node is being bootstrapped on this host
   * @return handle to bootstrap node, or null.
   */
  protected NodeHandle getBootstrap(boolean firstNode) {
    InetSocketAddress addr = null;
    if (firstNode && bshost != null)
      addr = new InetSocketAddress(bshost, bsport);
    else {
      try {
        addr =
          new InetSocketAddress(
            InetAddress.getLocalHost().getHostName(),
            bsport);
      } catch (UnknownHostException e) {
        System.out.println(e);
      }
    }

    NodeHandle bshandle = ((DistPastryNodeFactory) factory).getNodeHandle(addr);
    return bshandle;
  }

  /**
   * process command line args,
   */
  private static void doIinitstuff(String args[]) {

    // process command line arguments

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-help")) {
        System.out.println(
          "Usage: DistHelloWorld [-msgs m] [-nodes n] [-port p] [-bootstrap bshost[:bsport]]");
        System.out.println(
          "                     [-protocol (rmi|wire)] [-verbose|-silent|-verbosity v] [-help]");
        System.out.println("");
        System.out.println(
          "  Ports p and bsport refer to RMI registry  or Socket port numbers (default = 5009).");
        System.out.println(
          "  Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.");
        System.out.println(
          "  Default verbosity is 5, -verbose is 10, and -silent is -1 (error msgs only).");
        System.exit(1);
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-msgs") && i + 1 < args.length)
        nummsgs = Integer.parseInt(args[i + 1]);
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-port") && i + 1 < args.length) {
        int p = Integer.parseInt(args[i + 1]);
        if (p > 0)
          port = p;
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-bootstrap") && i + 1 < args.length) {
        String str = args[i + 1];
        int index = str.indexOf(':');
        if (index == -1) {
          bshost = str;
          bsport = port;
        } else {
          bshost = str.substring(0, index);
          bsport = Integer.parseInt(str.substring(index + 1));
          if (bsport <= 0)
            bsport = port;
        }
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-nodes") && i + 1 < args.length) {
        int n = Integer.parseInt(args[i + 1]);
        if (n > 0)
          numnodes = n;
        break;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-protocol") && i + 1 < args.length) {
        String s = args[i + 1];

        if (s.equalsIgnoreCase("wire"))
          protocol = DistPastryNodeFactory.PROTOCOL_WIRE;
        else if (s.equalsIgnoreCase("rmi"))
          protocol = DistPastryNodeFactory.PROTOCOL_RMI;
        else
          System.out.println("ERROR: Unsupported protocol: " + s);

        break;
      }
    }

  }

  /**
   * Create a Pastry node and add it to pastryNodes. Also create a client
   * application for this node, so that when this node comes up ( when 
   * pn.isReady() is true) , this application's notifyReady() method
   * is called, and it can do any interesting stuff it wants.
   */
  public PastryNode makePastryNode(boolean firstNode) {
    NodeHandle bootstrap = getBootstrap(firstNode);
    PastryNode pn = factory.newNode(bootstrap); // internally initiateJoins
    pastryNodes.addElement(pn);
    if (protocol == DistPastryNodeFactory.PROTOCOL_SOCKET) {
      SocketNodeHandle snh = (SocketNodeHandle)pn.getLocalHandle();
      System.out.println(snh.getAddress());
    }
    HelloWorldAppMultiThread app = new HelloWorldAppMultiThread(pn, this);
    helloClients.addElement(app);
    //if (Log.ifp(5)) System.out.println("created " + pastryNodes.size()+ "th pastry node " + pn);
    return pn;
  }

  static void waitUntilReady(PastryNode pn) {
    synchronized (pn) {
      while (!pn.isReady()) {
        try {
          pn.wait();
        } catch (InterruptedException e) {
          System.out.println(e);
        }
      }
    }
  }

  public static void makeDeadlock() {
    try { Thread.sleep(15000); } catch (InterruptedException ie) {}
    final Object l1 = new Object();
    final Object l2 = new Object(); 
    new Thread(new Runnable() {
      public void run() {
        synchronized(l1) {
          System.out.println("got l1");
          try { Thread.sleep(5000); } catch (InterruptedException ie) {}
          System.out.println("getting l2");
          synchronized(l2) {
            System.out.println("ERROR: got l2");
          }
        }        
      }
    }, "DL1").start();

    new Thread(new Runnable() {
      public void run() {
        synchronized(l2) {
          System.out.println("got l2");
          try { Thread.sleep(5000); } catch (InterruptedException ie) {}
          System.out.println("getting l1");
          synchronized(l1) {
            System.out.println("ERROR: got l1");
          }
        }        
      }
    }, "DL2").start();
  }

  /**
   * Usage: DistHelloWorld [-msgs m] [-nodes n] [-port p] [-bootstrap bshost[:bsport]]
   *                      [-verbose|-silent|-verbosity v] [-help].
   *
   * Ports p and bsport refer to RMI registry/ Socket port numbers (default = 5009).
   * Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.
   * Default verbosity is 5, -verbose is 10, and -silent is -1 (error msgs only).
   */
  public static void main(String args[]) {
    if (toFile) {
      try {
        PrintStream ps =
//        new PrintStream(new FileOutputStream("/home/jeffh/moo.txt"));
        new PrintStream(new FileOutputStream("c:/moo.txt"));
        System.setOut(ps);
        System.setErr(ps);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    //makeDeadlock();
    
    Log.init(args);
    if (protocol == DistPastryNodeFactory.PROTOCOL_WIRE) {      
      Wire.initialize();
    }
    doIinitstuff(args);
     
    ArrayList stats = new ArrayList();
    Stat curStat = null;
    DistHelloWorldMultiThread driver = null;
    int numSocks = 6;
    for (int times = 0; times < 20; times++) {
    
    //for (numSocks = 20; numSocks > 5; numSocks-=2) {

      try {
      SocketPoolManager.MAX_OPEN_SOCKETS = numSocks;
      curStat = new Stat(numSocks, numnodes);
      stats.add(curStat);
    
    driver = new DistHelloWorldMultiThread();

    // create first node
    PastryNode pn = driver.makePastryNode(true);

    // We wait till the first PastryNode on this host is ready so that the 
    // rest of the nodes find a bootstrap node on the local host
    waitUntilReady(pn);

    try {
      for (int i = 1; i < numnodes; i++) {
        System.out.println(
          "* Creating node " + i + "               **********");
        pn = driver.makePastryNode(false);
        Thread.currentThread().sleep(500);
        waitUntilReady(pn);
      }
    } catch (Exception e) {
      //System.out.println(e);
      e.printStackTrace();
    }

    while (!driver.allNodesCreated()) {
      try {
        Thread.sleep(1000);
      } catch (Exception e) {
        //System.out.println(e);
        e.printStackTrace();
      }
    }

    System.out.println(numnodes + " nodes constructed");

    try {
      Thread.currentThread().sleep(2000);
    } catch (Exception e) {
      //System.out.println(e);
      e.printStackTrace();
    }

    // wait for leafsets to stabalize
    // determine number of elements expected in ls
    int expectedLSsize = (driver.pastryNodes.size()-1)*2; // numNodes-self * 2 because both sides will have the same elements
    LeafSet ls = ((PastryNode) driver.pastryNodes.elementAt(0)).getLeafSet();    
    if (expectedLSsize > ls.maxSize()) {
      expectedLSsize = ls.maxSize();
    }
    boolean lsIsComplete = false;
    while (!lsIsComplete) {
      lsIsComplete = true;
      for (int i = 0; i < numnodes; i++) {      
        ls = ((PastryNode) driver.pastryNodes.elementAt(i)).getLeafSet();
        System.out.println(ls + " : "+ls.size());
        if (ls.size() < expectedLSsize) {
          System.out.println(ls.size()+"<"+expectedLSsize);
          lsIsComplete = false;
        }
      }
      if (!lsIsComplete) {
        try {
          System.out.println();
          Thread.sleep(5000);
        } catch (InterruptedException ie) {
          lsIsComplete = true;          
        }
      }
    }
    
    curStat.lsComplete();
    System.out.println("leafset complete:"+curStat);

    for (int i = 0; i < nummsgs; i++) {
      driver.sendRandomMessage();
    }

    //Now sit back and enjoy the fun, add some churn..

    int NUM_ITERS = 10;
    int kill_counter = 0;
    int iters = 0;
    boolean running = true;
    long finishedTime = 0;
    while (running) {
      if (iters < NUM_ITERS) {
        for (int i = 0; i < 50; i++) {
          driver.sendRandomMessage();
        }
      }
      
      
      try {
        long time = System.currentTimeMillis();
        time/=1000;
        System.out.println("At time (seconds)"+time);
        System.out.println("Messages Sent so far :" + driver.sentMessages());
        System.out.println(
          "Messages Delivered so far :" + driver.recievedMessages());
        ArrayList list = driver.getMissingMessages();
        System.out.println("Missing Messages so far :" + list.size());
        
        if (iters >= NUM_ITERS) {
          long curTime = System.currentTimeMillis();
          if (finishedTime == 0) {
            finishedTime = curTime;
          }
          if (curTime - finishedTime > 6000000) {
            running = false;
          }
          int numMissing = 0;
          Iterator i = list.iterator();
          while (i.hasNext()) {
            HelloMsg m = (HelloMsg)i.next();
            if (!driver.killedNodes.contains(m.source)) { // if the source is not dead
              boolean onlyPrintLiveFailures = useKill || useStall;
              if (onlyPrintLiveFailures) {
                // check to see if last address is a dead node
                InetSocketAddress lastAddr = m.getLastAddress();
                if (lastAddr == null) {
                  numMissing++;
                  System.out.println("  Missing msg: "+m.getInfo());                                                                                        
                } else {
                  Iterator it = driver.pastryNodes.iterator();
                  boolean print = false;
                  boolean missing = false;
                  while (it.hasNext()) {
                    PastryNode pn1 = (PastryNode)it.next();
                    SocketNodeHandle snh = (SocketNodeHandle)pn1.getLocalHandle();
                    if (snh.getAddress().equals(lastAddr) || m.ackReceived == false) {
                      missing = true;
                    }
                  }
                  if (missing) {
                    numMissing++;
                    //System.out.println("     Pre Missing");
                    System.out.println("  Missing msg: "+m.getInfo());                                                                    
                  }      
                }
              } else {
                numMissing++;
                System.out.println("  Missing msg: "+m.getInfo());                                                                                    
              }
            }
          } // while
          if (numMissing == 0) {
            throw new DoneException();
          }
        }
        if (kill_counter < numnodes*2/3) {
          int blargh = driver.rng.nextInt(2);
          if (blargh == 0) {
            if (useStall) {
              driver.stallPastryNode();
            } else {
              driver.killPastryNode();              
            }
          } else {
            if (useKill) {
              driver.killPastryNode();
            } else {
              driver.stallPastryNode();
            }
          }
          kill_counter++;
          System.out.println("KillCounter:" + kill_counter);
        } else {
          iters++;
        }
        
        Thread.currentThread().sleep(5000);

      } catch (DoneException de) {
        throw de;                
      } catch (Exception e) {
        e.printStackTrace();
        //                System.out.println(e);
      }
    }
    } catch (DoneException de) {
      boolean temp = noKilling;
      noKilling = false;
      while (!driver.pastryNodes.isEmpty()) {
        driver.killPastryNode();
      }
      noKilling = temp;
      curStat.finished();
      System.out.println("done "+curStat);
      port += numnodes+1;
      bsport = port;
      try {
        Thread.sleep(5000);
      } catch (InterruptedException ie) {}
    }

    } // for 
    Iterator eeee = stats.iterator();
    while(eeee.hasNext()) {
      System.out.println(eeee.next());
    }
  }

  private void sendRandomMessage() {
    int n = helloClients.size();
    int client = rng.nextInt(n);
    HelloWorldAppMultiThread tempApp =
      (HelloWorldAppMultiThread) helloClients.get(client);

    // get next alive client
    int origClient = client;
    while (!tempApp.getNodeHandle().isAlive()) {
      client++;
      if (client >= n) {
        client = 0;
      }
      if (client == origClient) {
        return; // we looped around, and all of the clients are dead
      }
      tempApp = (HelloWorldAppMultiThread) helloClients.get(client);
    }

    final HelloWorldAppMultiThread app = tempApp;
    final int msgId = getNextMsgId();
    if (oneThreadPerMessage) {
      Thread t = new Thread("Hello Client # " + app.getNodeId() + "," + msgId) {
        public void run() {
          try {
  //          Thread.currentThread().sleep(rng.nextInt(5000));
            app.sendRndMsg(rng, msgId);
          } catch (Exception e) {
            //System.out.println("Error Creating a new client thread for "+app.getNodeId()+" " + e);
  //          if (!(e instanceof NodeIsDeadException)) {
              e.printStackTrace();
  //          }
          }
        }
      };
  
      t.start();
    } else {
        try {
//          Thread.currentThread().sleep(rng.nextInt(5000));
          app.sendRndMsg(rng, msgId);
        } catch (Exception e) {
          //System.out.println("Error Creating a new client thread for "+app.getNodeId()+" " + e);
//          if (!(e instanceof NodeIsDeadException)) {
            e.printStackTrace();
//          }
        }
    }

  }

    

  //Global BookKeeping
  //Precious Global variables - need to have synchronized access to them

  private HashMap SentMessageLog = new HashMap();
  private HashMap RecievedMessageLog = new HashMap();
  private int id = 0;
  private int ready_nodes = 0;

  public synchronized void MessageSent(HelloMsg helloMsg) { // int messageId, NodeId node){
    SentMessageLog.put(new Integer(helloMsg.getId()), helloMsg);
  }

  public synchronized void MessageRecieved(HelloMsg helloMsg) {
            //Thread.dumpStack();
    //System.out.println("DHW.messageReceived("+helloMsg+")");
    RecievedMessageLog.put(new Integer(helloMsg.getId()), helloMsg);
  }

  private synchronized int sentMessages() {
    return SentMessageLog.size();
  }

  private synchronized int recievedMessages() {
    return RecievedMessageLog.size();
  }

  private synchronized ArrayList getMissingMessages() {
    ArrayList list = new ArrayList();
    Set s = SentMessageLog.keySet();
    Iterator i = s.iterator();
    while (i.hasNext()) {
      Integer messageId = (Integer) i.next();
      HelloMsg from = (HelloMsg) SentMessageLog.get(messageId);
      HelloMsg to = (HelloMsg) RecievedMessageLog.get(messageId);
      if (to == null) {
        list.add(from);
      }
    }
    return list;
  }

  public synchronized boolean allNodesCreated() {
    if (ready_nodes == numnodes)
      return true;
    return false;
  }

  public synchronized void node_created() {
    //System.out.println("Node Ready");
    ready_nodes++;
  }

  private synchronized int getNextMsgId() {
    return ++id;
  }

  /**
  * Create a Pastry node and add it to pastryNodes. Also create a client
  * application for this node, so that when this node comes up ( when 
  * pn.isReady() is true) , this application's notifyReady() method
  * is called, and it can do any interesting stuff it wants.
  */
  public void killPastryNode() {
    if (noKilling) return;  
    int killNum = rng.nextInt(pastryNodes.size());
    DistPastryNode pn =
      (DistPastryNode) pastryNodes.remove(killNum);
    killedNodes.addElement(pn.getId());
    HelloWorldAppMultiThread app = (HelloWorldAppMultiThread)helloClients.remove(killNum);      
    System.out.println("***********************   killing pastry node:" + pn + ","+app);
    pn.kill();
  }
  
  public void stallPastryNode() {
      if (noKilling) return;
      int killNum = rng.nextInt(pastryNodes.size());
      DistPastryNode pn =
        (DistPastryNode) pastryNodes.remove(killNum);
      killedNodes.addElement(pn.getId());
      HelloWorldAppMultiThread app = (HelloWorldAppMultiThread)helloClients.remove(killNum);      
      System.out.println("***********************   stalling pastry node:" + pn + ","+app);
      if (pn instanceof SocketPastryNode) {
          ((SocketPastryNode)pn).stall();   
      } else {
          pn.kill();   
      }
  }
  
  Vector killedNodes = new Vector();

}

class Stat {
  public int numNodes;
  public int numSocks; 
  public long lsCompleteTime;
  public long startTime;
  public long endTime;
  
  public Stat(int numS, int numN) {
    numSocks = numS;
    numNodes = numN;
    startTime = System.currentTimeMillis();
  }
  
  public void lsComplete() {
    lsCompleteTime = System.currentTimeMillis();
  }
  
  public void finished() {
    endTime = System.currentTimeMillis();
  }
  
  public String toString() {
    long t = endTime-startTime;
    long t2 = lsCompleteTime-startTime;
    return numNodes+":"+numSocks +":"+t2+","+t;
  }
}

class DoneException extends Exception {
  
}
