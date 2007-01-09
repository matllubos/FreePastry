/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package rice.pastry.standard;

import java.io.IOException;
import java.util.*;

import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.environment.random.simple.SimpleRandomSource;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.util.TimerWeakHashMap;
import rice.pastry.*;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.BroadcastLeafSet;
import rice.pastry.leafset.InitiateLeafSetMaintenance;
import rice.pastry.leafset.LeafSet;
import rice.pastry.leafset.LeafSetProtocolAddress;
import rice.pastry.leafset.RequestLeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.RoutingTable;
import rice.selector.TimerTask;

/**
 * An implementation of a periodic-style leafset protocol
 * 
 * @version $Id$
 * 
 * @author Alan Mislove
 */
public class PeriodicLeafSetProtocol extends PastryAppl implements ReadyStrategy, NodeSetListener, Observer {

  protected NodeHandle localHandle;

  protected PastryNode localNode;

  protected LeafSet leafSet;

  protected RoutingTable routeTable;

  /**
   * NodeHandle -> Long remembers the TIME when we received a BLS from that
   * NodeHandle
   */
  protected Map lastTimeReceivedBLS; // the leases you have
  protected Map lastTimeSentBLS; // the leases you have issued

  /**
   * Related to rapidly determining direct neighbor liveness.
   */
  public final int PING_NEIGHBOR_PERIOD;

  public final int LEASE_PERIOD;
  
  public final int CHECK_LIVENESS_PERIOD;

  public final int BLS_THROTTLE = 5000;

  ScheduledMessage pingNeighborMessage;

  RandomSource random;
  
  public static class PLSPMessageDeserializer extends PJavaSerializedDeserializer {

    public PLSPMessageDeserializer(PastryNode pn) {
      super(pn); 
    }
    
    public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
      switch (type) {
        case RequestLeafSet.TYPE:
          return new RequestLeafSet(sender, buf);
        case BroadcastLeafSet.TYPE:
          return new BroadcastLeafSet(buf, pn);
      }
      return null;
    }
     
  }
  
  /**
   * Builds a periodic leafset protocol
   * 
   */
  public PeriodicLeafSetProtocol(PastryNode ln, NodeHandle local,
      LeafSet ls, RoutingTable rt) {
    super(ln, null, LeafSetProtocolAddress.getCode(), new PLSPMessageDeserializer(ln));    
    this.localNode = ln;

    Parameters params = ln.getEnvironment().getParameters();
    if (params.contains("pastry_periodic_leafset_protocol_use_own_random")
        && params.getBoolean("pastry_periodic_leafset_protocol_use_own_random")) {
      if (params.contains("pastry_periodic_leafset_protocol_random_seed")
          && !params.getString("pastry_periodic_leafset_protocol_random_seed").equalsIgnoreCase(
              "clock")) {
        this.random = new SimpleRandomSource(params
            .getLong("pastry_periodic_leafset_protocol_random_seed"), ln.getEnvironment().getLogManager(),
            "socket");
      } else {
        this.random = new SimpleRandomSource(ln.getEnvironment().getLogManager(), "periodic_leaf_set");
      }
    } else {
      this.random = ln.getEnvironment().getRandomSource();
    }
    
    this.localHandle = local;
    
    // make sure to register all the existing leafset entries
    this.leafSet = ls;
    Iterator i = this.leafSet.asList().iterator();
    while(i.hasNext()) {
      NodeHandle nh = (NodeHandle)i.next(); 
      nh.addObserver(this);
    }

    this.routeTable = rt;
    this.lastTimeReceivedBLS = new TimerWeakHashMap(ln.getEnvironment().getSelectorManager().getTimer(), 300000);
    this.lastTimeSentBLS = new TimerWeakHashMap(ln.getEnvironment().getSelectorManager().getTimer(), 300000);
    Parameters p = ln.getEnvironment().getParameters();
    PING_NEIGHBOR_PERIOD = p.getInt("pastry_protocol_periodicLeafSet_ping_neighbor_period");
    LEASE_PERIOD = p.getInt("pastry_protocol_periodicLeafSet_lease_period");  // 30000
    CHECK_LIVENESS_PERIOD = PING_NEIGHBOR_PERIOD
        + p.getInt("pastry_protocol_periodicLeafSet_checkLiveness_neighbor_gracePeriod");
    this.lastTimeRenewedLease = new TimerWeakHashMap(ln.getEnvironment().getSelectorManager().getTimer(), LEASE_PERIOD*2);

    // Removed after meeting on 5/5/2005 Don't know if this is always the
    // appropriate policy.
    // leafSet.addObserver(this);
    pingNeighborMessage = localNode.scheduleMsgAtFixedRate(
        new InitiatePingNeighbor(), PING_NEIGHBOR_PERIOD, PING_NEIGHBOR_PERIOD);
  }

  private void updateRecBLS(NodeHandle from, long time) {
    if (time == 0) return;
    Long oldTime = (Long) lastTimeReceivedBLS.get(from);
    if ((oldTime == null) || (oldTime.longValue() < time)) {
      lastTimeReceivedBLS.put(from, new Long(time));      
      if (logger.level <= Logger.FINE) logger.log("PLSP.updateRecBLS("+from+","+time+")");
      // need to do this so that nodes are notified
      if (hasSetStrategy)
        isReady();
    } 
  }
  
  /**
   * Receives messages.
   * 
   * @param msg the message.
   */
  public void receiveMessage(Message msg) {
    if (msg instanceof BroadcastLeafSet) {
      // receive a leafset from another node
      BroadcastLeafSet bls = (BroadcastLeafSet) msg;

      // if we have now successfully joined the ring, set the local node ready
      if (bls.type() == BroadcastLeafSet.JoinInitial) {
        // merge the received leaf set into our own
        leafSet.merge(bls.leafSet(), bls.from(), routeTable, false,
            null);

        // localNode.setReady();
        broadcastAll();
      } else {
        // first check for missing entries in their leafset
        NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

        // if we find any missing entries, check their liveness
        for (int i = 0; i < set.size(); i++)
          if (bls.leafSet().test(set.get(i)))
            set.get(i).checkLiveness();

        // now check for assumed-dead entries in our leafset
        set = bls.leafSet().neighborSet(Integer.MAX_VALUE);

        // if we find any missing entries, check their liveness
        for (int i = 0; i < set.size(); i++)
          if (!set.get(i).isAlive())
            set.get(i).checkLiveness();

        // merge the received leaf set into our own
        leafSet.merge(bls.leafSet(), bls.from(), routeTable, false,
            null);
      }
      // do this only if you are his proper neighbor and he is yours !!!
      if ((bls.leafSet().get(1) == localHandle) ||
          (bls.leafSet().get(-1) == localHandle)) {
        updateRecBLS(bls.from(), bls.getTimeStamp());
      }
      
    } else if (msg instanceof RequestLeafSet) {
      // request for leaf set from a remote node
      RequestLeafSet rls = (RequestLeafSet) msg;

      thePastryNode.send(rls.returnHandle(),
          new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update, rls.getTimeStamp()));
      if (rls.getTimeStamp() > 0) {
        // remember that we gave out a lease, and go unReady() if the node goes faulty
        lastTimeRenewedLease.put(rls.returnHandle(),new Long(localNode.getEnvironment().getTimeSource().currentTimeMillis()));
      }
    } else if (msg instanceof InitiateLeafSetMaintenance) {
      // perform leafset maintenance
      NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

      if (set.size() > 1) {
        NodeHandle handle = set.get(random.nextInt(set.size() - 1) + 1);
        thePastryNode.send(handle,
            new RequestLeafSet(localHandle, localNode.getEnvironment().getTimeSource().currentTimeMillis()));
        thePastryNode.send(handle,
            new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update, 0));

        NodeHandle check = set.get(random
            .nextInt(set.size() - 1) + 1);
        check.checkLiveness();
      }
    } else if (msg instanceof InitiatePingNeighbor) {
      // IPN every 20 seconds
      NodeHandle left = leafSet.get(-1);
      NodeHandle right = leafSet.get(1);

      // send BLS to left neighbor
      if (left != null) {
        sendBLS(left);
      }
      if (right != null) {
        sendBLS(right);
      }
      // see if received BLS within past 30 seconds from right neighbor
      // now handled in sendBLS()
//      if (right != null) {
//        Long time = (Long) lastTimeReceivedBLS.get(right);
//        if (time == null
//            || (time.longValue() < (localNode.getEnvironment().getTimeSource()
//                .currentTimeMillis() - CHECK_LIVENESS_PERIOD))) {
//          // else checkLiveness() on right neighbor
//          if (logger.level <= Logger.FINE)
//            logger
//                .log("PeriodicLeafSetProtocol: Checking liveness on right neighbor:"
//                    + right);
//          right.checkLiveness();
//        }
//      }
//      if (left != null) {
//        Long time = (Long) lastTimeReceivedBLS.get(left);
//        if (time == null
//            || (time.longValue() < (localNode.getEnvironment().getTimeSource()
//                .currentTimeMillis() - CHECK_LIVENESS_PERIOD))) {
//          // else checkLiveness() on left neighbor
//          if (logger.level <= Logger.FINE)
//            logger
//                .log("PeriodicLeafSetProtocol: Checking liveness on left neighbor:"
//                    + left);
//          left.checkLiveness();
//        }
//      }
    }
  }

  /**
   * Broadcast the leaf set to all members of the local leaf set.
   * 
   * @param type the type of broadcast message used
   */
  protected void broadcastAll() {
    BroadcastLeafSet bls = new BroadcastLeafSet(localHandle, leafSet,
        BroadcastLeafSet.JoinAdvertise, 0);
    NodeSet set = leafSet.neighborSet(Integer.MAX_VALUE);

    for (int i = 1; i < set.size(); i++)
      thePastryNode.send(set.get(i), bls);
  }

  // Ready Strategy
  boolean hasSetStrategy = false;

  public void start() {
    if (!hasSetStrategy) {
      if (logger.level <= Logger.INFO) logger.log("PLSP.start(): Setting self as ReadyStrategy");
      localNode.setReadyStrategy(this);
      hasSetStrategy = true;
      localNode.addLeafSetListener(this);
      // to notify listeners now if we have proper leases
      isReady();
    }
  }
  
  /**
   * Called when the leafset changes
   */
  NodeHandle lastLeft;
  NodeHandle lastRight;
  public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added) {
//    if ((!added) && (
//      handle == lastLeft ||
//      handle == lastRight
//      )) {
//      // check to see if we have an existing lease
//      long curTime = localNode.getEnvironment().getTimeSource().currentTimeMillis();
//      long leaseOffset = curTime-LEASE_PERIOD;
//
//      Long time = (Long)lastTimeRenewedLease.get(handle);
//      if (time != null
//          && (time.longValue() >= leaseOffset)) {
//        // we gave out a lease too recently
//        TimerTask deadLease = 
//        new TimerTask() {        
//          public void run() {
//            deadLeases.remove(this);
//            isReady();            
//          }        
//        };
//        deadLeases.add(deadLease);
//        localNode.getEnvironment().getSelectorManager().getTimer().schedule(deadLease,
//            time.longValue()-leaseOffset);
//        isReady();
//      }      
//    }
    NodeHandle newLeft = leafSet.get(-1);
    if (newLeft != null && (lastLeft != newLeft)) {
      lastLeft = newLeft;
      sendBLS(lastLeft);
    }
    NodeHandle newRight = leafSet.get(1);
    if (newRight != null && (lastRight != newRight)) {
      lastRight = newRight;
      sendBLS(lastRight);
    }
  }
  
  boolean ready = false;
  public void setReady(boolean r) {
    if (ready != r) {
      synchronized(thePastryNode) {
        ready = r; 
      }
      thePastryNode.notifyReadyObservers();
    }
  }
  
  /**
   * 
   *
   */
  public boolean isReady() {
    // check to see if we've heard from the left/right neighbors recently enough
    boolean shouldBeReady = shouldBeReady(); // temp
//    if (!shouldBeReady) {      
//      // I suspect this is unnecessary because this timer should be well underway of sorting this out
//      receiveMessage(new InitiatePingNeighbor());
//    }
    
    if (shouldBeReady != ready) {
      thePastryNode.setReady(shouldBeReady); // will call back in to setReady() and notify the observers
    }
    
//    logger.log("isReady() = "+shouldBeReady);
    return shouldBeReady;
  }
  
//  HashSet deadLeases = new HashSet();
  
  public boolean shouldBeReady() {
    long curTime = localNode.getEnvironment().getTimeSource().currentTimeMillis();
    long leaseOffset = curTime-LEASE_PERIOD;
    
    NodeHandle left = leafSet.get(-1);
    NodeHandle right = leafSet.get(1);
    
    // see if received BLS within past 30 seconds from right neighbor
    if (right != null) {
      Long time = (Long) lastTimeReceivedBLS.get(right);
      if (time == null
          || (time.longValue() < leaseOffset)) {
            sendBLS(right);
        // we don't have a lease
        return false;
      }      
    }
    if (left != null) {
      Long time = (Long) lastTimeReceivedBLS.get(left);
      if (time == null
          || (time.longValue() < leaseOffset)) {
            sendBLS(left);
        // we don't have a lease
        return false;
      }      
    }     
//    if (deadLeases.size() > 0) return false;
    return true;
  }
  
  /**
   * 
   * @param sendTo
   * @return true if we sent it, false if we didn't because of throttled
   */
  private boolean sendBLS(NodeHandle sendTo) {
    Long time = (Long) lastTimeSentBLS.get(sendTo);
    long currentTime = localNode.getEnvironment().getTimeSource().currentTimeMillis();
    if (time == null
        || (time.longValue() < (currentTime - BLS_THROTTLE))) {
      if (logger.level <= Logger.FINE) // only log if not throttled
        logger.log("PeriodicLeafSetProtocol: Checking liveness on neighbor:"
              + sendTo+" "+time);
      lastTimeSentBLS.put(sendTo, new Long(currentTime));

      thePastryNode.send(sendTo, new BroadcastLeafSet(localHandle, leafSet, BroadcastLeafSet.Update, 0));
      thePastryNode.send(sendTo, new RequestLeafSet(localHandle, currentTime));
      sendTo.checkLiveness();
      return true;
    } 
    return false;
  }
  
  /**
   * NodeHandle -> time
   * 
   * Leases we have issued.  We cannot remove the node from the leafset until this expires.
   * 
   * If this node is found faulty (and you took over the leafset), must go non-ready until lease expires
   */
  Map lastTimeRenewedLease;
  
  /**
   * Used to kill self if leafset shrunk by too much. NOTE: PLSP is not
   * registered as an observer.
   * 
   */
  // public void update(Observable arg0, Object arg1) {
  // NodeSetUpdate nsu = (NodeSetUpdate)arg1;
  // if (!nsu.wasAdded()) {
  // if (localNode.isReady() && !leafSet.isComplete() && leafSet.size() <
  // (leafSet.maxSize()/2)) {
  // // kill self
  // localNode.getEnvironment().getLogManager().getLogger(PeriodicLeafSetProtocol.class,
  // null).log(Logger.SEVERE,
  // "PeriodicLeafSetProtocol:
  // "+localNode.getEnvironment().getTimeSource().currentTimeMillis()+" Killing
  // self due to leafset collapse. "+leafSet);
  // localNode.resign();
  // }
  // }
  // }
  /**
   * Should not be called becasue we are overriding the receiveMessage()
   * interface anyway.
   */
  public void messageForAppl(Message msg) {
    throw new RuntimeException("Should not be called.");
  }

  /**
   * We always want to receive messages.
   */
  public boolean deliverWhenNotReady() {
    return true;
  }

  public void destroy() {
    if (logger.level <= Logger.INFO)
      logger.log("PLSP: destroy() called");
    if (pingNeighborMessage != null)
      pingNeighborMessage.cancel();
    pingNeighborMessage = null;
    lastLeft = null;
    lastRight = null;
    lastTimeReceivedBLS.clear();
    lastTimeRenewedLease.clear();
    lastTimeSentBLS.clear();
//    deadLeases.clear();
  }

  @Override
  public void leafSetChange(NodeHandle nh, boolean wasAdded) {
    super.leafSetChange(nh, wasAdded);
    if (wasAdded) {
      nh.addObserver(this); 
    } else {
      if (logger.level <= Logger.FINE) logger.log("Removed "+nh+" from the LeafSet.");
      nh.deleteObserver(this); 
    }
  }

  /**
   * Only remove the item if you did not give a lease.
   */
  public void update(final Observable o, final Object arg) {
//  if (o instanceof NodeHandle) {      
    if (arg == NodeHandle.DECLARED_DEAD) {
      Long l_time = (Long)lastTimeRenewedLease.get(o);
      if (l_time == null) {
        // there is no lease on record
        leafSet.remove((NodeHandle)o);
      } else {
        long leaseExpiration = l_time.longValue()+LEASE_PERIOD;
        long now = thePastryNode.getEnvironment().getTimeSource().currentTimeMillis();
        if (leaseExpiration > now) {
          if (logger.level <= Logger.INFO) logger.log("Removing "+o+" from leafset later."+(leaseExpiration-now));
          // remove it later when lease expries
          thePastryNode.getEnvironment().getSelectorManager().getTimer().schedule(new TimerTask() {          
            @Override
            public void run() {
              if (logger.level <= Logger.FINE) logger.log("Calling update("+o+","+arg+")");
              // do this recursively in case we issue a new lease
              update(o,arg);
            }          
          }, leaseExpiration-now);
        } else {
          // lease has expired
          leafSet.remove((NodeHandle)o);
        }      
      }        
    }
//  }    
}

}
