/*
 * Created on Apr 13, 2005
 */
package rice.pastry.standard;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import rice.pastry.NodeHandle;
import rice.pastry.NodeSetUpdate;
import rice.pastry.PastryNode;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;
import rice.selector.TimerTask;

/**
 * Does not setReady until contacting entire leafset which gossips new members.
 * 
 * Provides consistency as long as checkLiveness() never incorrectly reports a node faulty.
 * 
 * Based on MSR-TR-2003-94.  The difference is that our assumption that checkLiveness() is much stronger
 * because we are using DSR rather than checking ourself.  
 * 
 * Another difference is that we are unwilling to pull nodes from our leafset without checkingLiveness() ourself.
 * 
 * @author Jeff Hoye
 */
public class ConsistentJoinProtocol extends StandardJoinProtocol implements Observer {

  public static final boolean verbose = false;
  
  /**
   * Contains NodeHandles that know about us. 
   */
  HashSet gotResponse;
  
  /**
   * Nodes that we think are dead.
   */
  HashSet failed;
  
  /**
   * NodeHandles I'm observing
   */
  HashSet observing;
  
  /**
   * Will retry sending ConsistentJoinMsg to all neighbors who have not responded 
   * on this interval.  Only necessary if somehow the message was dropped.
   */
  public static final int RETRY_INTERVAL = 30000;
  
  /**
   * Constructor takes in the usual suspects.
   */
  public ConsistentJoinProtocol(PastryNode ln, NodeHandle lh,
      PastrySecurityManager sm, RoutingTable rt, LeafSet ls) {
    super(ln, lh, sm, rt, ls);
    gotResponse = new HashSet();
    failed = new HashSet();
    observing = new HashSet();
    ls.addObserver(this);
  }
  
  /**
   * This is where we start out, when the StandardJoinProtocol would call
   * setReady();
   */
  protected void setReady() {    
    // send a probe to everyone in the leafset
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      if (i != 0) {
        NodeHandle nh = leafSet.get(i);          
        sendTheMessage(nh, false);
      }
    }
    retryTask = localNode.scheduleMsg(new RequestFromEveryoneMsg(getAddress()), RETRY_INTERVAL, RETRY_INTERVAL);
  }  
  
  /**
   * Observes all NodeHandles added to LeafSet
   * @param nh the nodeHandle to add
   */
  public void addToLeafSet(NodeHandle nh) {
    leafSet.put(nh);
    if (!observing.contains(nh)) {
      nh.addObserver(this);
      observing.add(nh);
    }
  }

  /**
   * Used to trigger timer events.
   * @author Jeff Hoye
   */
  class RequestFromEveryoneMsg extends Message {
    public RequestFromEveryoneMsg(Address dest) {
      super(dest);
    }    
  }
  
  TimerTask retryTask;
  
  public void requestFromEveryoneWeHaventHeardFrom() {
    if (localNode.isReady()) {      
      retryTask.cancel();
      return; 
    }
    
    Iterator i = whoDoWeNeedAResponseFrom().iterator();
    while(i.hasNext()) {
      NodeHandle nh = (NodeHandle)i.next(); 
      //nh.checkLiveness();
      sendTheMessage(nh, false);
    }
  }
  
  /**
   * Returns all members of the leafset that are not in gotResponse
   * @return
   */
  public Collection whoDoWeNeedAResponseFrom() {
    HashSet ret = new HashSet();
    for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
      if (i != 0) {
        NodeHandle nh = leafSet.get(i);          
        if (!gotResponse.contains(nh)) {
          ret.add(nh);
        }
      }
    }
    return ret;
  }

  /**
   * Handle the CJM as in the MSR-TR
   */
  public void receiveMessage(Message msg) {
    if (msg instanceof ConsistentJoinMsg) {
      ConsistentJoinMsg cjm = (ConsistentJoinMsg)msg;
      // identify node j, the sender of the message
      NodeHandle j = cjm.ls.get(0);

      // failed_i := failed_i - {j}
      failed.remove(j);
      
      if (localNode.isReady()) {
        if (cjm.request) {
          sendTheMessage(j, true); 
        }
        return;
      }
      
      // L_i.add(j);
      addToLeafSet(j);
      
      // for each n in L_i and failed do {probe}
      // rather than removing everyone in the remote failedset,
      // checkLiveness on them all
      Iterator it = cjm.failed.iterator();
      while(it.hasNext()) {
        NodeHandle nh = (NodeHandle)it.next(); 
        if (leafSet.member(nh)) {
          if (nh.getLiveness() == NodeHandle.LIVENESS_DEAD) {
            // if we already found them dead, don't bother
            // hopefully this is redundant with the leafset protocol
            leafSet.remove(nh); 
          } else {
            if (verbose) System.out.println("CJP:"+System.currentTimeMillis()+" checking liveness2 on "+nh);
            nh.checkLiveness();
          }
        }
      }
      
      // we don't do the L_i.remove() because we don't trust this info

      // L' stuff
      // this is so we don't probe too many dudez.  For example:
      // my leafset is not complete, so I will add anybody, but then keep
      // getting closer and closer, each time knocking out the next guy
      LeafSet lprime = leafSet.copy();
      for (int i=-cjm.ls.ccwSize(); i<=cjm.ls.cwSize(); i++) {
        NodeHandle nh = cjm.ls.get(i);
        if (!failed.contains(nh) && nh.getLiveness() < NodeHandle.LIVENESS_DEAD) {
          lprime.put(nh);
        }
      }
      
      HashSet addThese = new HashSet(); 
      for (int i=-lprime.ccwSize(); i<=lprime.cwSize(); i++) {
        if (i != 0) {
          NodeHandle nh = lprime.get(i);
          if (!leafSet.member(nh)) {
            addThese.add(nh);
          }
        }
      }
      
      Iterator it2 = addThese.iterator();
      while(it2.hasNext()) {
        NodeHandle nh = (NodeHandle)it2.next();
//        if (!leafSet.member(nh)) {
//          if (leafSet.test(nh)) {
            // he's not a member, but he could be
            if (!failed.contains(nh) && nh.getLiveness() < NodeHandle.LIVENESS_DEAD) {
              addToLeafSet(nh);
              // probe
              //if (verbose) System.out.println("CJP:"+System.currentTimeMillis()+" checking liveness1 on "+nh);
              //nh.checkLiveness(); // maybe this is automatic the first time I send a message
              sendTheMessage(nh, false);
//            }
//          }
        }
      } 
      
      if (cjm.request) {
        // send reply
        sendTheMessage(j, true);
      } //else {
        // done_probing:
        
        // mark that he knows about us
        gotResponse.add(j);
        doneProbing();
//      }      
    } else if (msg instanceof RequestFromEveryoneMsg) {
      requestFromEveryoneWeHaventHeardFrom();
    } else {
      super.receiveMessage(msg);      
    }
  }

  /** 
   * Similar to the MSR-TR
   *
   */
  void doneProbing() {
    if (leafSet.isComplete()) {
      // here is where we see if we can go active
      HashSet toHearFrom = new HashSet();
      HashSet seen = new HashSet();
      String toHearFromStr = "";
      int numToHearFrom = 0;
      for (int i=-leafSet.ccwSize(); i<=leafSet.cwSize(); i++) {
        if (i != 0) {
          NodeHandle nh = leafSet.get(i);          
          if (!seen.contains(nh) && !gotResponse.contains(nh)) {
            numToHearFrom++;
            toHearFrom.add(nh);
            toHearFromStr+=nh+":"+nh.getLiveness()+",";
          }
          seen.add(nh);
        }
      }
      
      if (numToHearFrom == 0) {
        if (!localNode.isReady()) {
          // active_i = true;
    //        System.out.println("j = "+j+"setReady() probed "+seen.size()+":"+gotResponse.size()+" nodes.");
          localNode.setReady(); 
          retryTask.cancel();
        }
        // failed_i = {}
    //      gotResponse.clear();
        failed.clear();
        Iterator it2 = observing.iterator();
        while(it2.hasNext()) {
          NodeHandle nh = (NodeHandle)it2.next();
          nh.deleteObserver(this);
          it2.remove();
        }
      } else {
        if (verbose) System.out.println("CJP:"+System.currentTimeMillis()+" still need to hear from:"+toHearFromStr);
      }
    } else {
      if (verbose) System.out.println("CJP:"+System.currentTimeMillis()+" LS is not complete: "+leafSet);
      // need to poll left and right neighbors
      // sendTheMessage to leftmost and rightmost?
      // send leafsetMaintenance to self?
    }
  }
  
  /**
   * Sends a consistent join protocol message.
   * @param nh
   * @param reply
   */
  public void sendTheMessage(NodeHandle nh, boolean reply) {
    if (verbose) System.out.println("CJP:"+System.currentTimeMillis()+" sendTheMessage("+nh+","+reply+")");
    // todo, may want to repeat this message as long as the node is alive if we 
    // are worried about rare message drops
    if (localNode.isReady()) {
      failed.clear(); 
    }
    nh.receiveMessage(new ConsistentJoinMsg(getAddress(),leafSet,failed,!reply));      
  }
  
  /**
   * Can be leafset updates, or nodehandle updates.
   */
  public void update(Observable arg0, Object arg) {
    if (localNode.isReady()) {
      arg0.deleteObserver(this);
      return; 
    }
    
    if (arg instanceof NodeSetUpdate) {
      NodeSetUpdate nsu = (NodeSetUpdate)arg;
      if (nsu.wasAdded()) {
        if (!gotResponse.contains(nsu.handle())) {
          sendTheMessage(nsu.handle(),false);
        }
      }
      return;
    }
      // assume it's a NodeHandle, cause we
      // want to throw the exception if it is something we don't recognize
    NodeHandle nh = (NodeHandle)arg0;
    if (((Integer) arg) == NodeHandle.DECLARED_DEAD) {
      failed.add(nh);
      leafSet.remove(nh); 
      doneProbing();
    }

    if (((Integer) arg) == NodeHandle.DECLARED_LIVE) {
      failed.remove(nh);
      if (!localNode.isReady()) {
        if (leafSet.test(nh)) {
          leafSet.put(nh);
          sendTheMessage(nh, false);
        }
      }
    }
  }
}
