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

package rice.pastry.socket.testing;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import rice.pastry.Log;
import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.NodeIdFactory;
import rice.pastry.PastryNode;
import rice.pastry.PastryNodeFactory;
import rice.pastry.PastrySeed;
import rice.pastry.churn.ChurnLeafSetProtocol;
import rice.pastry.dist.DistPastryNode;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.leafset.LeafSetProtocolAddress;
import rice.pastry.socket.ConnectionManager;
import rice.pastry.socket.SocketCollectionManager;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNode;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.socket.SocketPoolManager;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.selector.testing.SelectorTest;

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
  private static PastryNodeFactory factory;
  private Vector pastryNodes;
  private Vector helloClients;
  public static final Random rng = new Random(PastrySeed.getSeed());

  private static int port = 5009;
  private static String bshost = null;
  private static int bsport = 5009;
  private static int numnodes = 8;
  private static int nummsgs = 50;
  public static int protocol = DistPastryNodeFactory.PROTOCOL_SOCKET;
  private static boolean toFile = true;
  private static boolean useKill = true;
  private static boolean useRegen = true;
  private static boolean oneThreadPerMessage = false;
  public static boolean useDirect = true;
  private static boolean limitedSockets = false; 
  public static boolean useNonDirect = true; 
  public static boolean useRandChoices = false;
  public static boolean testSelector = true;
  public static boolean printConnectionManagers = false;
  static int NUM_ITERS = 30;
  public static int numSockets = 10;

  
  public static DistPastryNodeFactory getSameFactory() {
    DistPastryNodeFactory sameNodeFactory = 
      DistPastryNodeFactory.getFactory(
        new NodeIdFactory() {
          public NodeId generateNodeId() {
            return NodeId.buildNodeId();
          }
        },
        protocol,
        5008);    
    return sameNodeFactory;
  }
  

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
  public PastryNode makePastryNode(boolean firstNode, PastryNodeFactory pnf) {
    NodeHandle bootstrap = getBootstrap(firstNode);
    PastryNode pn = pnf.newNode(bootstrap); // internally initiateJoins
    pastryNodes.addElement(pn);
    SocketNodeHandle snh = (SocketNodeHandle)pn.getLocalHandle();
    System.out.println(snh.getAddress());
    HelloWorldAppMultiThread app = new HelloWorldAppMultiThread(pn, this);
    helloClients.addElement(app);
    //if (Log.ifp(5)) System.out.println("created " + pastryNodes.size()+ "th pastry node " + pn);
    return pn;
  }

  static void waitUntilReady(PastryNode pn) {
    synchronized (pn) {
      while (!pn.isReady()) {
        try {
          pn.wait(5000);
          if (!pn.isReady()) {
            System.out.println("waitUntilReady() still waiting for "+pn+" "+pn.getLeafSet());
            if (SocketPastryNodeFactory.churn) {
              ChurnLeafSetProtocol clsp = (ChurnLeafSetProtocol)pn.getMessageDispatch().getDestinationByAddress(new LeafSetProtocolAddress());
              Iterator i = clsp.getProbing().iterator();
              while (i.hasNext()) {
                SocketNodeHandle h = (SocketNodeHandle)i.next();
                ConnectionManager cm = ((SocketPastryNode)pn).sManager.getConnectionManager(h);
                String s = "null";
                if (cm != null) {
                  s = cm.getStatus();              
                }
                System.out.println("  "+h+" "+h.getLiveness()+" "+s);
              }
            }
          }

        } catch (InterruptedException e) {
          System.out.println(e);
        }
      }
    }
  }

  public static void makeDeadlock() {
    sleep(15000);
    final Object l1 = new Object();
    final Object l2 = new Object(); 
    new Thread(new Runnable() {
      public void run() {
        synchronized(l1) {
          System.out.println("got l1");
          sleep(5000);
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
          sleep(5000);
          System.out.println("getting l1");
          synchronized(l1) {
            System.out.println("ERROR: got l1");
          }
        }        
      }
    }, "DL2").start();
  }
  
  public static void logToFileIfNeeded() {
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
  }

  public static void initialize(String args[]) {
    logToFileIfNeeded();
    
    Log.init(args);
    doIinitstuff(args);    
  }

  public static void createNodes(int numnodes, DistHelloWorldMultiThread driver) {
    // create first node
    PastryNode pn = driver.makePastryNode(true, factory);

    // We wait till the first PastryNode on this host is ready so that the 
    // rest of the nodes find a bootstrap node on the local host
    waitUntilReady(pn);

    // create each pastry node
    try {
      for (int i = 1; i < numnodes; i++) {
        System.out.println(
          "* Creating node " + i + "               **********");
        pn = driver.makePastryNode(false, factory);
        sleep(500);
        waitUntilReady(pn);
      }
    } catch (Exception e) {
      //System.out.println(e);
      e.printStackTrace();
    }

    // wait until they are all created
    while (!driver.allNodesCreated()) {
      sleep(1000);
    }

    System.out.println(numnodes + " nodes constructed");

  }

  static DistPastryNode sameNode = null;
  public static void createSameNode(DistHelloWorldMultiThread driver) {
    driver.ready_nodes = numnodes-1;
    double beginTime = System.currentTimeMillis();
    System.out.println("creatingSamenode():1  "+((System.currentTimeMillis()-beginTime)/1000));
    sameNode = (DistPastryNode)driver.makePastryNode(false, getSameFactory());
    System.out.println("creatingSamenode():2  "+((System.currentTimeMillis()-beginTime)/1000));
    System.out.println("sameNode with epoch "+((SocketNodeHandle)sameNode.getLocalHandle()).getEpoch());
    waitUntilReady(sameNode);
    //sleep(5000);
    System.out.println("creatingSamenode():3  "+((System.currentTimeMillis()-beginTime)/1000));

    // wait until they are all created
    while (!driver.allNodesCreated()) {
      System.out.println("creatingSamenode():4  "+((System.currentTimeMillis()-beginTime)/1000));
      //sleep(5000);
    }
    System.out.println("creatingSamenode():5  "+((System.currentTimeMillis()-beginTime)/1000));
    waitForLeafSetsToStabalize(driver);
    System.out.println("creatingSamenode():6  "+((System.currentTimeMillis()-beginTime)/1000));
    if (testSelector) SelectorTest.main(null);
  }
  
  public void killSameNode() {
//    HelloWorldAppMultiThread app = (HelloWorldAppMultiThread)helloClients.remove(killNum); 
    int index = pastryNodes.indexOf(sameNode);
    DistPastryNode pn =
      (DistPastryNode) pastryNodes.remove(index);
    killedNodes.addElement(pn.getLocalHandle());
    HelloWorldAppMultiThread app = (HelloWorldAppMultiThread)helloClients.remove(index);      
    System.out.println("***********************   killing same node:" +sameNode+","+ pn + ","+app);
    sameNode.kill();
    sameNode = null;
  }

  public static void sleep(int time) {
    try {
      Thread.sleep(time);
    } catch (Exception e) {
      //System.out.println(e);
      e.printStackTrace();
    }    
  }

	public static void waitForLeafSetsToStabalize(DistHelloWorldMultiThread driver) {

		// wait for leafsets to stabalize
		// determine number of elements expected in ls
		int expectedLSsize = (driver.pastryNodes.size() - 1) * 2;
		// numNodes-self * 2 because both sides will have the same elements
		LeafSet ls = ((PastryNode) driver.pastryNodes.elementAt(0)).getLeafSet();
		if (expectedLSsize > ls.maxSize()) {
			expectedLSsize = ls.maxSize();
		}
		boolean lsIsComplete = false;
		while (!lsIsComplete) {
			lsIsComplete = true;
			for (int i = 0; i < driver.pastryNodes.size(); i++) {
				ls = ((PastryNode) driver.pastryNodes.elementAt(i)).getLeafSet();
				System.out.println(ls + " : " + ls.size());
				if (ls.size() < expectedLSsize) {
					System.out.println(ls.size() + "<" + expectedLSsize);
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
	}
  
  public static void sendMsgs(int nummsgs, DistHelloWorldMultiThread driver) {
    
    for (int i = 0; i < nummsgs; i++) {
      driver.sendRandomMessage();
    }
  }

  public static boolean printMissingMessages(DistHelloWorldMultiThread driver, int iters) throws DoneException {
//    long time = System.currentTimeMillis();
//    time /= 1000;
    System.out.println("At time "+new Date());
    System.out.println(
      "Messages Sent so far :" + driver.sentMessages());
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
        return false;
      }
      int numMissing = 0;
      int numMissingDirect = 0;
      Iterator i = list.iterator();
      while (i.hasNext()) {
        HelloMsg m = (HelloMsg) i.next();
        if (!driver.killedNodesContains(m.getIntermediateSource())) {
//        if (!driver.killedNodes.contains(m.getIntermediateSource())) {
          // if the source is not dead
          boolean onlyPrintLiveFailures = useKill || useRegen;
          if (onlyPrintLiveFailures) {
            // check to see if last address is a dead node
            //InetSocketAddress lastAddr = m.getLastAddress();
//            Id lastAddr = m.lastNextHop;
//            if (lastAddr == null) {
//              numMissing++;
//              // due to no next hop
//              System.out.println("  Missing msg1: " + m.getInfo());
//            } else {
              Iterator it = driver.killedNodes.iterator();
              boolean print = false;
              boolean missing = true;
              while (it.hasNext()) {
                //PastryNode pn1 = (PastryNode) it.next();
                SocketNodeHandle snh = (SocketNodeHandle)it.next();
//                  (SocketNodeHandle) pn1.getLocalHandle();
                if (m.messageDirect) {
                  if (m.getIntermediateSource().equals(snh)) { // the last known sender is dead
                    missing = false;
                  }
                  if ((m.getNextHop() == null) || (m.getNextHop().equals(snh))) { // the intended receiver is dead
                    missing = false;
                  }
                } else { // we're not using direct, and we got an ack and the lastNexthop is now dead
                  if ((m.getNextHop() == null) || (m.getNextHop().equals(snh))) { // the intended receiver is dead
//                  if (m.getNextHop().equals(snh)) {
                    missing = false;
                  }                  
                }
              }
              if (missing) {
                numMissing++;
                if (m.messageDirect) numMissingDirect++;
                //System.out.println("     Pre Missing");
                
                // this code was designed to print the connections for the node containing this
                if (m.getLocalNode() != null) {                	
                  SocketPastryNode spn = m.getLocalNode();
//                  ConnectionManager cm = getConnectionManager(spn, m.getNextHop());
                  ConnectionManager cm = spn.sManager.getConnectionManager((SocketNodeHandle)m.getNextHop());
                  if (cm != null) {
                    System.out.println("  Missing msg4: " + m.getInfo()+" cm:"+cm.getStatus());                    
                  } else {
                    System.out.println("  Missing msg3: " + m.getInfo()+ "cm: null");
                  }                
                } else {
                  System.out.println("  Missing msg2: " + m.getInfo());
                }
              }
          } else {
            numMissing++;
            if (m.messageDirect) numMissingDirect++;
            System.out.println("  Missing msg: " + m.getInfo());
          }
        }
      } // while
      if (numMissing == 0) {
        throw new DoneException(0);
      }
      System.out.println("  numMissing:"+numMissing+" numMissingDirect:"+numMissingDirect+" lastNumMissingDirectCtr:"+lastNumMissingDirectCtr);
      if (useDirect && limitedSockets && numMissing == numMissingDirect) {
        //System.out.println("DHWMT.printMissingMessages() here");
        // the only missing messages were sent direct
        // if they remain the same for some number of passes, go ahead and continue         
        if (lastNumMissingDirect == numMissingDirect) {
          lastNumMissingDirectCtr++;
          if (lastNumMissingDirectCtr >= 5) {
            System.out.println("Giving up with "+numMissingDirect+" undelevered messages.  This is likely due to a node killing a direct connection as the other was sending"); 
            throw new DoneException(numMissingDirect);
          }
        } else {
          lastNumMissingDirect = numMissingDirect;
          lastNumMissingDirectCtr=0; // reset counter
        }
      }
    }    
    return true;
  }

	public ConnectionManager getConnectionManager(SocketPastryNode spn, PseudoSocketNodeHandle psnh) {
		Iterator i = spn.sManager.getConnectionManagers().iterator();
		while(i.hasNext()) {
			ConnectionManager cm = (ConnectionManager)i.next();
      if (psnh.equals(cm.getNodeHandle())) return cm;
		}
		return null;
	}
  
  /**
   * @param handle
   * @return
   */
  private boolean killedNodesContains(PseudoSocketNodeHandle handle) {
		Iterator i = killedNodes.iterator();
		while (i.hasNext()) {
			SocketNodeHandle snh = (SocketNodeHandle)i.next();
			if (handle.equals(snh)) return true;
		}
    return false;
  }


  static int lastNumMissingDirect = 0;
  static int lastNumMissingDirectCtr = 0;
  
  static int sameNodeNumber = 0;
  public static void manageSameNode(DistHelloWorldMultiThread driver) {
    if (sameNodeNumber % 5 == 0) {
      if (sameNode == null) {
        createSameNode(driver);
      } else {
        driver.killSameNode();
      }
    } 
    sameNodeNumber++;
  }
  
  static long finishedTime = 0;

  /**
   * Usage: DistHelloWorld [-msgs m] [-nodes n] [-port p] [-bootstrap bshost[:bsport]]
   *                      [-verbose|-silent|-verbosity v] [-help].
   *
   * Ports p and bsport refer to RMI registry/ Socket port numbers (default = 5009).
   * Without -bootstrap bshost[:bsport], only localhost:p is used for bootstrap.
   * Default verbosity is 5, -verbose is 10, and -silent is -1 (error msgs only).
   */
	public static void main(String args[]) {
		initialize(args);
    ConnectionManager.MAXIMUM_QUEUE_LENGTH *= 100; // becasue leaf set protocol explodes
    if (printConnectionManagers) {
      SocketCollectionManager.printConnectionManagers = printConnectionManagers;
    }

		ArrayList stats = new ArrayList();
		Stat curStat = null;
		DistHelloWorldMultiThread driver = null;
		int numSocks = numSockets;
    int defautltMaxOpenSockets = SocketPoolManager.MAX_OPEN_SOCKETS;
		for (int times = 0; times < 20; times++) {

			//for (numSocks = 20; numSocks > 5; numSocks-=2) {

			try {
        if (useRandChoices) {
          limitedSockets = rng.nextBoolean();
          useDirect = rng.nextBoolean();
          useNonDirect = rng.nextBoolean();
          useRegen = rng.nextBoolean();
          useKill = rng.nextBoolean();
        }
        if (limitedSockets)
  				SocketPoolManager.MAX_OPEN_SOCKETS = numSocks;
        else 
          SocketPoolManager.MAX_OPEN_SOCKETS = defautltMaxOpenSockets;
          
				curStat = new Stat(SocketPoolManager.MAX_OPEN_SOCKETS, numnodes, useDirect, useNonDirect, useRegen, useKill);
        System.out.println(" running with "+curStat);
				stats.add(curStat);
        
				driver = new DistHelloWorldMultiThread();

				// create first node
//				PastryNode pn = driver.makePastryNode(true);

        createNodes(numnodes,driver);
        sleep(2000);

        waitForLeafSetsToStabalize(driver);
        
        curStat.lsComplete();
        System.out.println("leafset complete:" + curStat);        

        sendMsgs(nummsgs, driver);

				//Now sit back and enjoy the fun, add some churn..

        finishedTime = 0;
				int kill_counter = 0;
				int iters = 0;
        lastNumMissingDirect = 0;
        lastNumMissingDirectCtr = 0;
				boolean running = true;
				while (running) {
          if (useRegen) {
            manageSameNode(driver);
          }
          
					if (iters < NUM_ITERS) {
            sendMsgs(nummsgs, driver);
					}

					try {
            running = printMissingMessages(driver, iters);

						if (useKill && (kill_counter < numnodes * 2 / 3)) {

              driver.killPastryNode();
							kill_counter++;
							System.out.println("KillCounter:" + kill_counter);
						} else {
							iters++;
						}

						sleep(5000);

					} catch (DoneException de) {
						throw de;
					} catch (Exception e) {
						e.printStackTrace();
						//                System.out.println(e);
					}
				}
			} catch (DoneException de) {
        boolean temp2 = useRegen;
        useRegen = false;
        if (sameNode != null) {
          driver.killSameNode();
        }
        while (!driver.pastryNodes.isEmpty()) {
          driver.killPastryNode();
        }
        useRegen = temp2;
				curStat.finished(de.numMissing);
        System.out.println("done " + curStat);        
        long mem1 = Runtime.getRuntime().freeMemory();
        driver = null;
        Runtime.getRuntime().gc();
				port += numnodes + 1;
				bsport = port;
					
        sleep(5000);

        long mem2 = Runtime.getRuntime().freeMemory();
        System.out.println("mem1:"+(mem1/1000000)+" mem2:"+(mem2/1000000));
			}

		} // for 
		Iterator eeee = stats.iterator();
		while (eeee.hasNext()) {
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
              e.printStackTrace();
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

  public void updateMsg(HelloMsg helloMsg) {
//    System.out.println("updateMsg("+helloMsg+")");
    SentMessageLog.put(new Integer(helloMsg.getId()), helloMsg);    
//    System.out.println("updateMsg2("+SentMessageLog.get(new Integer(helloMsg.getId()))+")");
  }

  public synchronized void MessageSent(HelloMsg helloMsg) { // int messageId, NodeId node){
    if (SentMessageLog.get(new Integer(helloMsg.getId())) == null) {    
//      System.out.println("msgSent("+helloMsg+")");    
//      Thread.dumpStack();
      SentMessageLog.put(new Integer(helloMsg.getId()), helloMsg);
    }
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

  public int getKillNum() {
    if (useRegen) {
      return rng.nextInt(pastryNodes.size()-2)+1; // don't kill bootstrap, or sameNode    
    } else {
      return rng.nextInt(pastryNodes.size());
    }
  }

  /**
  * Create a Pastry node and add it to pastryNodes. Also create a client
  * application for this node, so that when this node comes up ( when 
  * pn.isReady() is true) , this application's notifyReady() method
  * is called, and it can do any interesting stuff it wants.
  */
  public void killPastryNode() {
    int killNum = getKillNum();
    DistPastryNode pn =
      (DistPastryNode) pastryNodes.remove(killNum);
    killedNodes.addElement(pn.getLocalHandle());
    HelloWorldAppMultiThread app = (HelloWorldAppMultiThread)helloClients.remove(killNum);     
    SocketNodeHandle snh = (SocketNodeHandle)pn.getLocalHandle();
 
    System.out.println("***********************   killing pastry node:" + pn + ","+app+" "+snh.getAddress());
    pn.kill();
  }
  
  // of NodeHandle
  Vector killedNodes = new Vector();

	protected void finalize() throws Throwable {
    System.out.println("driver.finalize()");
		super.finalize();
	}

}

class Stat {
  public int numNodes;
  public int numSocks; 
  public long lsCompleteTime;
  public long startTime;
  public long endTime;
  boolean useDirect, useNonDirect, useRegen, useKill;
  int numMissing = 0;
  
  public Stat(int numS, int numN, boolean useDirect, boolean useNonDirect, boolean useRegen, boolean useKill) {
    numSocks = numS;
    numNodes = numN;
    this.useDirect = useDirect;
    this.useNonDirect = useNonDirect;
    this.useRegen = useRegen;
    this.useKill = useKill;
    startTime = System.currentTimeMillis();
  }
  
  public void lsComplete() {
    lsCompleteTime = System.currentTimeMillis();
  }
  
  public void finished(int numMissing) {
    endTime = System.currentTimeMillis();
    this.numMissing = numMissing;
  }
  
  public String toString() {
    long t = endTime-startTime;
    long t2 = lsCompleteTime-startTime;
    return "numNodes:"+numNodes+" numSocks:"+numSocks +" timeFinishedCreating:"+t2+" timeFinished:"+t+" useDirect:"+useDirect+" useNonDirect:"+useNonDirect+" useRegen:"+useRegen+" useKill:"+useKill+" numMissing:"+numMissing;
  }
}

class DoneException extends Exception {
  int numMissing = 0;
  public DoneException(int numMissing) {
    super();
    this.numMissing = numMissing;
  }
}
