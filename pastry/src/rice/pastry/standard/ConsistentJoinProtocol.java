/*
 * Created on Apr 13, 2005
 */
package rice.pastry.standard;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.pastry.NodeHandle;
import rice.pastry.NodeSetUpdate;
import rice.pastry.PastryNode;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Address;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;
import rice.selector.LoopObserver;
import rice.selector.SelectorManager;
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
public class ConsistentJoinProtocol extends StandardJoinProtocol implements Observer, LoopObserver {

  /**
   * This variable is set to prevent the process from going to sleep or not
   * being scheduled for too long.
   */
  protected final int MAX_TIME_TO_BE_SCHEDULED;

  /**
   * Suppresses sendTheMessage() if we are not ready to do this part of the join process,
   * or we are already done.
   */
  protected boolean tryingToGoReady = false;
  
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
  public final int RETRY_INTERVAL;
  
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
    ln.addObserver(this);
    Parameters p = ln.getEnvironment().getParameters();
    MAX_TIME_TO_BE_SCHEDULED = p.getInt("pastry_protocol_consistentJoin_max_time_to_be_scheduled");
    RETRY_INTERVAL = p.getInt("pastry_protocol_consistentJoin_retry_interval");
    ln.getEnvironment().getSelectorManager().addLoopObserver(this);
  }
  
  /**
   * This is where we start out, when the StandardJoinProtocol would call
   * setReady();
   */
  protected void setReady() {    
    if (tryingToGoReady) return;
    tryingToGoReady = true;
    log(Logger.INFO, "ChurnJonProtocol.setReady()");
    gotResponse.clear();
    failed.clear();
    // send a probe to everyone in the leafset
    Iterator i = leafSet.neighborSet(Integer.MAX_VALUE).iterator();
    while(i.hasNext()) {
      NodeHandle nh = (NodeHandle)i.next();
      sendTheMessage(nh, false);      
    }
    
    retryTask = thePastryNode.scheduleMsg(new RequestFromEveryoneMsg(getAddress()), RETRY_INTERVAL, RETRY_INTERVAL);
  }  
  
  /**
   * Observes all NodeHandles added to LeafSet
   * @param nh the nodeHandle to add
   */
  public void addToLeafSet(NodeHandle nh) {
    leafSet.put(nh);
    if (!observing.contains(nh)) {
      log(Logger.FINE, "CJP observing "+nh);
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
    if (thePastryNode.isReady()) {      
      retryTask.cancel();
      return; 
    }
    
    Collection c = whoDoWeNeedAResponseFrom();
    log(Logger.INFO, "CJP: timeout1, still waiting to hear from "+c.size()+" nodes.");
    
    Iterator i = c.iterator();
    while(i.hasNext()) {
      NodeHandle nh = (NodeHandle)i.next(); 
      log(Logger.FINE, "CJP: timeout2, still waiting to hear from "+nh);
      //nh.checkLiveness();
      sendTheMessage(nh, false);
    }
  }
  
  /**
   * Call this if there is an event such that you may have 
   * not received messages for long enough for other nodes
   * to call you faulty.  
   * 
   * This method will call PastryNode.setReady(false) which will
   * stop delivering messages, and then via the observer pattern
   * will call this.update(PN, FALSE) which will call setReady()
   * which will begin the join process again.
   *
   */
  public void otherNodesMaySuspectFaulty() {
    thePastryNode.setReady(false);
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
      
      if (thePastryNode.isReady()) {
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
            log(Logger.FINE, "CJP: checking liveness2 on "+nh);
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
        // he's not a member, but he could be
        if (!failed.contains(nh) && nh.getLiveness() < NodeHandle.LIVENESS_DEAD) {
          addToLeafSet(nh);
          // probe
          sendTheMessage(nh, false);
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
        if (!thePastryNode.isReady()) {
          // active_i = true;
          thePastryNode.setReady(); 
          retryTask.cancel();
          tryingToGoReady = false;
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
        log(Logger.FINE, "CJP: still need to hear from:"+toHearFromStr);
      }
    } else {
      log(Logger.FINE, "CJP: LS is not complete: "+leafSet);
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
    if (!reply) {
      if (!tryingToGoReady) return;
//      logException(Logger.FINEST, "StackTrace", new Exception("Stack Trace")); 
    }
    log(Logger.FINE, "CJP:  sendTheMessage("+nh+","+reply+")");
    
    // todo, may want to repeat this message as long as the node is alive if we 
    // are worried about rare message drops
    if (thePastryNode.isReady()) {
      failed.clear(); 
    }    
    nh.receiveMessage(new ConsistentJoinMsg(getAddress(),leafSet,failed,!reply));      
  }
  

  private void log(int level, String s) {
    thePastryNode.getEnvironment().getLogManager().getLogger(ConsistentJoinProtocol.class, null).log(level,s);
  }
  
  private void logException(int level, String s, Throwable t) {
    thePastryNode.getEnvironment().getLogManager().getLogger(ConsistentJoinProtocol.class, null).logException(level,s, t);
  }
  

  /**
   * Can be PastryNode updates, leafset updates, or nodehandle updates.
   */
  public void update(Observable arg0, Object arg) {
    
    // we went offline for whatever reason.  Now we need to try to come back online.
    if (arg0 == thePastryNode) {
      if (((Boolean)arg).booleanValue() == false) {
        setReady();
      }
    }
    
    if (arg instanceof NodeSetUpdate) {
      if (thePastryNode.isReady()) return;
      NodeSetUpdate nsu = (NodeSetUpdate)arg;
      if (nsu.wasAdded()) {
        if (!gotResponse.contains(nsu.handle())) {
          sendTheMessage(nsu.handle(),false);
        }
      } else {
        doneProbing(); 
      }
      
      return;
    }
    
    if (arg0 instanceof NodeHandle) {
      if (thePastryNode.isReady()) {
        observing.remove(arg0);
        arg0.deleteObserver(this);
        return; 
      }
          
        // assume it's a NodeHandle, cause we
        // want to throw the exception if it is something we don't recognize
      NodeHandle nh = (NodeHandle)arg0;
      if (((Integer) arg) == NodeHandle.DECLARED_DEAD) {
        log(Logger.FINE, "CJP:"+arg0+" declared dead");
        failed.add(nh);
        leafSet.remove(nh); 
        doneProbing();
      }
  
      if (((Integer) arg) == NodeHandle.DECLARED_LIVE) {
        failed.remove(nh);
        if (!thePastryNode.isReady()) {
          if (leafSet.test(nh)) {
            leafSet.put(nh);
            sendTheMessage(nh, false);
          }
        }
      }
    }
  }

  /**
   * Part of the LoopObserver interface.  Used to detect if 
   * we may have been found faulty by other nodes.
   * 
   * @return the minimum loop time we are interested in being notified about.
   */
  public int delayInterest() {
    return MAX_TIME_TO_BE_SCHEDULED;
  }


  /**
   * If it took longer than the time to detect faultiness, then other nodes
   * may believe we are faulty.  So we best rejoin.
   * 
   * @param loopTime the time it took to do a single selection loop.
   */
  public void loopTime(int loopTime) {
    if (loopTime > delayInterest()) {
      otherNodesMaySuspectFaulty(); 
    }
  }
  
  public void destroy() {
    log(Logger.INFO, "CJP: destroy() called");
    thePastryNode.getEnvironment().getSelectorManager().removeLoopObserver(this);
  }
}
