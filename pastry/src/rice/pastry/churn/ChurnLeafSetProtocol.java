/*
 * Created on May 18, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.leafset.InitiateLeafSetMaintenance;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;
import rice.pastry.socket.LivenessListener;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNode;
import rice.pastry.standard.StandardLeafSetProtocol;

/**
 * @author Jeff Hoye
 */
public class ChurnLeafSetProtocol extends StandardLeafSetProtocol implements FailedSetManager, ProbeListener, Observer, LivenessListener {

  Vector failed = new Vector();
  Vector probing = new Vector();
  int joinState = Probe.STATE_NONE;
  /**
   * This holds the most recent leafset received by each node
   * SocketNodeHandle -> LivenessLeafSet
   */
  Hashtable reachable = new Hashtable();

	public ChurnLeafSetProtocol(
		PastryNode ln,
		NodeHandle local,
		PastrySecurityManager sm,
		LeafSet ls,
		RoutingTable rt) {
		super(ln, local, sm, ls, rt);
    ls.addObserver(this);
	}

	/**
	 * Receives messages.
	 *
	 * @param msg the message.
	 */
	public void receiveMessage(Message msg) {
//		if (msg instanceof BroadcastLeafSet) {
//			// receive a leafset from another node
//			BroadcastLeafSet bls = (BroadcastLeafSet) msg;
//			int type = bls.type();
//
//			NodeHandle from = bls.from();
//			LeafSet remotels = bls.leafSet();
//
//			//System.out.println("received leafBC from " + from.getNodeId() + " at " + 
//			//         localHandle.getNodeId() + "type=" + type + " :" + remotels);
//
//			// first, merge the received leaf set into our own
//			boolean changed = mergeLeafSet(remotels, from);
//			//        if (changed)
//			//          System.out.println("received leafBC from " + from.getNodeId() + " at " + 
//			//                 localHandle.getNodeId() + "type=" + type + " :" + remotels);
//
//			if (!failstop) {
//				// with arbitrary node failures, we need to broadcast whenever we learn something new
//				if (changed)
//					broadcast();
//
//				// then, send ls to sending node if that node's ls is missing nodes
//				checkLeafSet(remotels, from, false);
//				return;
//			}
//
//			// if this node has just joined, notify the members of our new leaf set
//			if (type == BroadcastLeafSet.JoinInitial)
//				broadcast();
//
//			// if we receive a correction to a leafset we have sent out, notify the members of our new leaf set
//			if (type == BroadcastLeafSet.Correction && changed)
//				broadcast();
//
//			// Check if any of our local leaf set members are missing in the received leaf set
//			// if so, we send our leafset to each missing entry and to the source of the leafset
//			// this guarantees correctness in the event of concurrent node joins in the same leaf set
//			checkLeafSet(remotels, from, true);
//		} else if (
//			msg instanceof RequestLeafSet) {
//			// request for leaf set from a remote node
//			RequestLeafSet rls = (RequestLeafSet) msg;
//
//			NodeHandle returnHandle = rls.returnHandle();
//			returnHandle = security.verifyNodeHandle(returnHandle);
//
//			if (returnHandle.isAlive()) {
//				BroadcastLeafSet bls =
//					new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update);
//
//				returnHandle.receiveMessage(bls);
//			}
//		} else 
    if (msg instanceof InitiateLeafSetMaintenance) {
			// request for leafset maintenance

			// perform leafset maintenance
			maintainLeafSet();

		} else
			throw new Error("message received is of unknown type");

	}


  /**
   * Maintain the leaf set. This method checks for dead leafset entries
   * and replaces them as needed. It is assumed that this method be
   * invoked periodically.
   */
  public void maintainLeafSet() {

    //  System.out.println("maintainLeafSet " + localHandle.getNodeId());
    // TODO: suppress if we recently received other data
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
      probe(snh);
    }
  }
  
	/**
	 * probe the entire leafset
	 */
	public void probeLeafSet() {
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
      probe(snh);
    }
	}

//probe(j)
//  if (j != probing ^ j != failed)
//    send hLS-PROBE; i; L; failedii to j
//    probingi += j
//    probe-retriesi(j) := 0
  public void probe(SocketNodeHandle snh) {
    if (snh.equals(localHandle)) return; // don't probe self
    if (!probing.contains(snh) && !failed.contains(snh)) {
      addLivessListener(snh);
      probing.add(snh);      
      snh.probe();
//      probeRetries.put(snh,new Integer(0));
    }
  }

	public Collection getFailedSet() {
		return failed;
	}

//  RECEIVE(LS-PROBE | LS-PROBE-REPLY; j; L; failed)
//    failedi := failedi - j
//    Li.add(j); 
//    Ri.add(j);
//    for each n in Li and failed do { probei(n) }
//    Li:remove(failed)
//    L0 := Li; 
//    L0:add(L - failedi)
//    for each n in L0 - Li do { probei(n) }
//    if (message is LS-PROBE)
//      send hLS-PROBE-REPLY; i; Li; failedii to j
//    else
//      done-probingi(j)    
	public void probeReceived(Probe p) {
//    System.out.println(localNode+" probeReceived("+p+")");
//    System.out.println(localNode+" probeReceived()1"+leafSet);
//  failedi := failedi - j
    failed.remove(p.getSender());
    if (p.getState() < Probe.STATE_JOINED) {
      return;
    }    
    
//  Li.add(j); 
    LivenessLeafSet ls2 = p.getLeafset();
    
    handleRemoteLeafset(ls2,(SocketNodeHandle)p.getSender());
    
    
//  Ri.add(j);
    routeTable.put(p.getSender());
    
//  for each n in Li and failed do { probei(n) }
    Collection failedSet = p.getFailedset();
    if (failedSet != null) {
      for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
        SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
        if (failedSet.contains(snh)) {
          System.out.println("found "+snh+" failed.");
          probe(snh);      
        }
      }    
    }
    
    // TODO: this is contraversial
//  Li:remove(failed)
//    Collection fs = p.getFailedset();
//    if (fs != null) {
//      Iterator it = fs.iterator();
//      while(it.hasNext()) {
//        NodeHandle nh = (NodeHandle)it.next();
//        leafSet.remove((NodeId)nh.getId());
//      }
//    }

//  L0 := Li; 
    LeafSet lsprime = leafSet.copy();

    Collection fs = p.getFailedset();
    if (fs != null) {
      Iterator it = fs.iterator();
      while(it.hasNext()) {
        NodeHandle nh = (NodeHandle)it.next();
        lsprime.remove(nh);
      }
    }


//  L0:add(L - failedi)
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
      if (!failed.contains(snh)) {
        lsprime.put(snh);
      }
    }    

//  for each n in L0 - Li do { probei(n) }
    for (int i=-lsprime.ccwSize(); i<=lsprime.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)lsprime.get(i); 
      if (leafSet.get((NodeId)snh.getId()) == null) {
        probe(snh);
      }
    }    

//  if (message is LS-PROBE)
//    send hLS-PROBE-REPLY; i; Li; failedii to j
//  automatic by Socket
//  else
//    done-probingi(j)    
    if (p.isResponse())
      doneProbing((SocketNodeHandle)p.getSender(), false);
//    System.out.println(localNode+" probeReceived()2"+leafSet);
	}

  public void mergeLeafSet(LeafSet ls2) {
    for (int i=-ls2.ccwSize(); i<=ls2.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)ls2.get(i); 
      leafSet.put(snh);
    }           
  }

  public void handleRemoteLeafset(LivenessLeafSet lls, SocketNodeHandle snh) {

    // merge the leafset
    Iterator i = lls.leafSet.iterator();
    while(i.hasNext()) {
      LivenessHandle handle = (LivenessHandle)i.next(); 
      leafSet.put(handle.nh);
    }     
    
    reachable.put(snh,lls);        
  }

  public void checkLivenessOfLeafset() {
    for (int ii=-leafSet.ccwSize(); ii<=leafSet.cwSize(); ii++) {
      SocketNodeHandle snh2 = (SocketNodeHandle)leafSet.get(ii); 
      checkLiveness(snh2);
    }               
  }

  public void checkLiveness(SocketNodeHandle snh) {
    if (snh.getLiveness() >= NodeHandle.LIVENESS_UNREACHABLE) {
      if (getBestLivenessValue(snh) >= NodeHandle.LIVENESS_UNREACHABLE) {
        markDead(snh);
      }
    }    
  }
  
  /**
   * Go through all of the nodes and see what everyone's opinion is.
   */
  public int getBestLivenessValue(SocketNodeHandle snh) {
    Iterator i = reachable.keySet().iterator();
    int bestLiveness = NodeHandle.LIVENESS_FAULTY;
    while(i.hasNext()) {
      SocketNodeHandle key = (SocketNodeHandle)i.next();
      LivenessLeafSet lls = (LivenessLeafSet)reachable.get(key);
      int liveness = lls.getLiveness(snh);
      if ((liveness > NodeHandle.LIVENESS_UNKNOWN) && (liveness < bestLiveness))
        bestLiveness = liveness;
    }
    return bestLiveness;
  }
  
  public void markDead(SocketNodeHandle snh) {
    ((SocketPastryNode)localNode).markDead(snh);
  }
  
//  done-probingi(j)
//    probingi := probingi - j
//    if (probingi = {})
//      if (Li:complete)
//        activei := true; failed := {}
//      else
//        if (jLi:leftj < l=2)
//          probe(Li:leftmost)
//        if (jLi:rightj < l=2)
//          probe(Li:rightmost)
  public void doneProbing(SocketNodeHandle snh, boolean nodeHasFailed) {
//    System.out.println(localNode+" doneProbing("+snh+") "+probing.size());
//    printProbing();
//  probingi := probingi - j
    if (snh != null)
      if (nodeHasFailed) {
        failed.add(snh);        
      }
      removeLivenessListener(snh);
      probing.remove(snh);    
//  if (probingi = {})
    if (probing.size() == 0) {
//    if (Li:complete)
      if (leafSet.isComplete()) {
//      activei := true; 
        joinState = Probe.STATE_READY;
        localNode.setReady();
//      failed := {}
        while (!failed.isEmpty()) {
          failed.remove(0);
        }
      } else {
//      if (jLi:leftj < l=2)
        if (leafSet.cwSize() < leafSet.maxSize()/2)
//        probe(Li:leftmost)
          probe((SocketNodeHandle)leafSet.get(leafSet.cwSize()));
//      if (jLi:rightj < l=2)
        if (leafSet.ccwSize() < leafSet.maxSize()/2)
//        probe(Li:rightmost)
          probe((SocketNodeHandle)leafSet.get(-leafSet.ccwSize()));        
      }
    }
  }

  /**
   * Listens to liveness to trigger doneprobing
   * @param snh
   */
  private void addLivessListener(SocketNodeHandle snh) {
    ((SocketPastryNode)localNode).addLivenessListener(snh, this);
  }


  /**
   * Listens to liveness to trigger doneprobing
	 * @param snh
	 */
	private void removeLivenessListener(SocketNodeHandle snh) {
    ((SocketPastryNode)localNode).removeLivenessListener(snh, this);
	}

	public void printProbing() {
    Iterator i = probing.iterator();
    while(i.hasNext()) {
      System.out.println("  "+i.next());
    }
  }

	public int getJoinState() {
    if (localNode.isReady()) return Probe.STATE_READY;
		return joinState;
	}

	public void update(Observable arg0, Object arg1) {
//    NodeSetUpdate nsu = (NodeSetUpdate)arg1;		
//    if (nsu.)
	}

	public void updateLiveness(NodeHandle nh, int liveness) {
    if (nh == null) return;
    if (liveness >= NodeHandle.LIVENESS_UNREACHABLE) {
      doneProbing((SocketNodeHandle)nh, true);
    }
	}
}
