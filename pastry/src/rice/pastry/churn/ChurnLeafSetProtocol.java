/*
 * Created on May 18, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import java.util.ArrayList;
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
import rice.pastry.socket.ConnectionManager;
import rice.pastry.socket.LivenessListener;
import rice.pastry.socket.SocketNodeHandle;
import rice.pastry.socket.SocketPastryNode;
import rice.pastry.standard.StandardLeafSetProtocol;

/**
 * @author Jeff Hoye
 */
public class ChurnLeafSetProtocol extends StandardLeafSetProtocol implements ProbeListener, Observer, LivenessListener {

  boolean maintainEntireLeafset = true;

  /**
   * of SocketNodeHandle
   */
  Vector probing = new Vector();

  /**
   * This holds the most recent leafset received by each node
   * SocketNodeHandle -> LivenessLeafSet
   */
  Hashtable reachable = new Hashtable();

  int maintenanceInterval = 60000;
  
	public ChurnLeafSetProtocol(
		PastryNode ln,
		NodeHandle local,
		PastrySecurityManager sm,
		LeafSet ls,
		RoutingTable rt,
    int maintenanceInterval) {
		super(ln, local, sm, ls, rt);
    ls.addObserver(this);
    this.maintenanceInterval = maintenanceInterval;
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
    checkLivenessOfLeafset();

    if (maintainEntireLeafset) {
      for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
        SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
        verifyMemberCheckedIn(snh);        
        sendMaintenanceProbe(snh);
      }
    } else {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(1); 
      sendMaintenanceProbe(snh);      
      snh = (SocketNodeHandle)leafSet.get(-1); 
      verifyMemberCheckedIn(snh);      
    }
  }

  protected void verifyMemberCheckedIn(SocketNodeHandle snh) {
    int lastTimeReceived = (int)(System.currentTimeMillis() - snh.getLastTimeProbeReceived());
    if (lastTimeReceived > maintenanceInterval) { 
      // we didn't get the expected probe from the neighbor
      memberDidntCheckIn(snh);
    }
  }
  
  protected void sendMaintenanceProbe(SocketNodeHandle snh) {
    // suppress if we recently received other data
    int lastTimeSent = (int)(System.currentTimeMillis() - snh.getLastTimeProbeSent());
    if (lastTimeSent > maintenanceInterval)
      probe(snh, false);
  }
  
  protected void memberDidntCheckIn(SocketNodeHandle snh) {
    snh.probe(true);        
  }
  
	/**
	 * probe the entire leafset
	 */
	public void probeLeafSet(boolean requestResponse) {
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
      if (snh.getLiveness() < NodeHandle.LIVENESS_UNREACHABLE)
        probe(snh, requestResponse);
    }
	}

//probe(j)
//  if (j != probing ^ j != failed)
//    send hLS-PROBE; i; L; failedii to j
//    probingi += j
//    probe-retriesi(j) := 0
  public void probe(SocketNodeHandle snh, boolean requestResponse) {
    if (snh.equals(localHandle)) return; // don't probe self
    if (!probing.contains(snh) && snh.getLiveness() < NodeHandle.LIVENESS_UNREACHABLE) {
      probing.add(snh);      
      snh.probe(requestResponse);
//      probeRetries.put(snh,new Integer(0));
    }
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
    if (p.getState() < Probe.STATE_JOINED) {
      return;
    }    
    
//  Li.add(j); 
    LivenessLeafSet ls2 = p.getLeafset();
    
    handleRemoteLeafset(ls2,(SocketNodeHandle)p.getSender());
    
    
//  Ri.add(j);
    routeTable.put(p.getSender());
    
//  for each n in Li and failed do { probei(n) }
    Collection failedSet = p.getFailedSet();
    if (failedSet != null) {
      for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
        SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
        Iterator i2 = failedSet.iterator();
        while(i2.hasNext()) {
          LivenessHandle lh = (LivenessHandle)i2.next();
          if (lh.nh.equals(snh)) {
//            System.out.println(this+".probeReceived() found "+snh+" failed.");
            probe(snh, true);
          }
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

    Collection fs = p.getFailedSet();
    if (fs != null) {
      Iterator it = fs.iterator();
      while(it.hasNext()) {
        LivenessHandle nh = (LivenessHandle)it.next();
        lsprime.remove(nh.nh);
      }
    }


//  L0:add(L - failedi)
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)leafSet.get(i); 
      if (snh.getLiveness() < NodeHandle.LIVENESS_FAULTY) {
        lsprime.put(snh);
      }
    }    

//  for each n in L0 - Li do { probei(n) }
    for (int i=-lsprime.ccwSize(); i<=lsprime.cwSize(); i++) {
      SocketNodeHandle snh = (SocketNodeHandle)lsprime.get(i); 
      if (!leafSet.member(snh)) {
        probe(snh, true);
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
    checkLivenessOfLeafset();
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
      if (handle.liveness < NodeHandle.LIVENESS_FAULTY) {
        leafSet.put(handle.nh);
      }
    }     
    
    reachable.put(snh,lls);        
  }

  public void checkLivenessOfLeafset() {
    for (int ii=-leafSet.ccwSize(); ii<=leafSet.cwSize(); ii++) {
      SocketNodeHandle snh2 = (SocketNodeHandle)leafSet.get(ii); 
      if (snh2 != null)
        checkLiveness(snh2);
    }               
  }

  public void checkLiveness(SocketNodeHandle snh) {
    //System.out.println("CLSP.checkLiveness()");
    if (snh.getLiveness() >= NodeHandle.LIVENESS_UNREACHABLE) {
      int bestLiveness = getBestLivenessValue(snh);
      if (bestLiveness >= NodeHandle.LIVENESS_UNREACHABLE) {
        markDead(snh);
      } else {
//        System.out.println("CLSP.checkLiveness("+snh+") bestLiveness = "+bestLiveness);
      }
    }    
  }
  
  /**
   * Go through all of the nodes and see what everyone's opinion is.
   */
  public int getBestLivenessValue(SocketNodeHandle snh) {
    Iterator i = reachable.keySet().iterator();
    ArrayList needToRemove = new ArrayList();
    int bestLiveness = NodeHandle.LIVENESS_FAULTY;
    while(i.hasNext()) {
      SocketNodeHandle key = (SocketNodeHandle)i.next();
      if (key.getLiveness() <= NodeHandle.LIVENESS_SUSPECTED) {
        LivenessLeafSet lls = (LivenessLeafSet)reachable.get(key);
        int liveness = lls.getLiveness(snh);
        if ((liveness > NodeHandle.LIVENESS_UNKNOWN) && (liveness < bestLiveness)) {
          bestLiveness = liveness;
//          if (liveness <= NodeHandle.LIVENESS_SUSPECTED)
//            System.out.println("CLSP.getBestLivenessValue("+snh+"):"+liveness+" "+key+" val:"+key.getLiveness());
        }
      } else {
        needToRemove.add(key);
      }
    }
    i = needToRemove.iterator();
    while(i.hasNext()) {
      reachable.remove(i.next());
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
    probing.remove(snh);    
//  if (probingi = {})
    if (probing.size() == 0) {
//    if (Li:complete)
      if (leafSet.isComplete()) {
//      activei := true; 
        ((SocketPastryNode)localNode).setJoinedState(Probe.STATE_READY);
        localNode.setReady();
//      failed := {}
      } else {
//      if (jLi:leftj < l=2)
        if (leafSet.cwSize() < leafSet.maxSize()/2)
//        probe(Li:leftmost)
          probe((SocketNodeHandle)leafSet.get(leafSet.cwSize()),true);
//      if (jLi:rightj < l=2)
        if (leafSet.ccwSize() < leafSet.maxSize()/2)
//        probe(Li:rightmost)
          probe((SocketNodeHandle)leafSet.get(-leafSet.ccwSize()),true);        
      }
    } else {
      // Leafset is not complete
//      if (!((SocketPastryNode)localNode).isReady()) {
////        System.out.println("CLSP.doneNode()"+leafSet);
//  
//        System.out.println("CLSP.doneNode(): still probing");
//        Iterator i = probing.iterator();
//        while (i.hasNext()) {
//          SocketNodeHandle h = (SocketNodeHandle)i.next();
//          ConnectionManager cm = ((SocketPastryNode)localNode).sManager.getConnectionManager(h);
//          String s = "null";
//          if (cm != null) {
//            s = cm.getStatus();              
//          }
//          System.out.println("  "+h+" "+h.getLiveness()+" "+s);
//        }
//      }      
    }
  }

	public void printProbing() {
    Iterator i = probing.iterator();
    while(i.hasNext()) {
      System.out.println("  "+i.next());
    }
  }

	public void update(Observable arg0, Object arg1) {
//    NodeSetUpdate nsu = (NodeSetUpdate)arg1;		
//    if (nsu.)
	}

	public void updateLiveness(NodeHandle nh, int liveness) {
    if (nh == null) return;
    if (liveness >= NodeHandle.LIVENESS_UNREACHABLE) {
      doneProbing((SocketNodeHandle)nh, true);
      reachable.remove(nh);      
      checkLiveness((SocketNodeHandle)nh);
      probeLeafSet(false); // notify all other members
    }     
	}

	public Collection getProbing() {
		return (Collection)probing.clone();
	}
}
