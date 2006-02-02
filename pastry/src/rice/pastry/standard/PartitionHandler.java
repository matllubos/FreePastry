package rice.pastry.standard;

import java.net.InetSocketAddress;
import java.util.*;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.join.JoinRequest;
import rice.pastry.leafset.LeafSet;
import rice.pastry.routing.*;
import rice.pastry.security.*;
import rice.selector.TimerTask;

public class PartitionHandler extends TimerTask implements NodeSetListener {

  PastryNode pastryNode;

  // maybe move this to a subclass
  InetSocketAddress[] bootstraps;
  
  DistPastryNodeFactory factory;
  
  PastrySecurityManager sec;
  
  Logger logger;
  
  double bootstrapRate;
  int maxGoneSize;
  int maxGoneAge;
  // map from Id's -> NodeHandle's to keep things unique per NodeId
  Map gone;
  
  Environment env;
  
  // XXX think about multiring
  
  public PartitionHandler(PastryNode pn, DistPastryNodeFactory factory, PastrySecurityManager sec, InetSocketAddress[] bootstraps) {
    pastryNode = pn;
    this.factory = factory;
    this.sec = sec;
    this.bootstraps = bootstraps;
    env = pastryNode.getEnvironment();
    gone = new HashMap();
    this.logger = pn.getEnvironment().getLogManager().getLogger(PartitionHandler.class,"");
    
    maxGoneSize = env.getParameters().getInt("partition_handler_max_history_size");
    maxGoneAge = env.getParameters().getInt("partition_handler_max_history_age");
    bootstrapRate = env.getParameters().getDouble("partition_handler_bootstrap_check_rate");
    
    pastryNode.getLeafSet().addNodeSetListener(this);
    pastryNode.getRoutingTable().addNodeSetListener(this);
  }
  
  private synchronized void doGoneMaintainence() {
    Iterator it = gone.values().iterator();
    long now = env.getTimeSource().currentTimeMillis();

    if (logger.level <= Logger.FINE) logger.log("Doing maintainence in PartitionHandler "+now);

    if (logger.level <= Logger.FINER) logger.log("gone size 1 is "+gone.size()+" of "+maxGoneSize);

    while (it.hasNext()) {
      GoneSetEntry g = (GoneSetEntry)it.next();
      if (now - g.timestamp > maxGoneAge) { // toss if too old
        if (logger.level <= Logger.FINEST) logger.log("Removing "+g+" from gone due to expiry");
        it.remove();
      } else if (g.nh.getLiveness() > NodeHandle.LIVENESS_DEAD) { // toss if epoch changed
        if (logger.level <= Logger.FINEST) logger.log("Removing "+g+" from gone due to death");
        it.remove();
      }
    }
    
    if (logger.level <= Logger.FINER) logger.log("gone size 2 is "+gone.size()+" of "+maxGoneSize);

    while (gone.size() > maxGoneSize) {
      gone.entrySet().iterator().remove();
    }
    if (logger.level <= Logger.FINER) logger.log("gone size 3 is "+gone.size()+" of "+maxGoneSize);
  }

  private NodeHandle getGone() {
    RoutingTable rt = pastryNode.getRoutingTable();
    synchronized (this) {
      int size = gone.size()+rt.numEntries();
      if (size > maxGoneSize)
        size = maxGoneSize;
      
      int which = env.getRandomSource().nextInt(size);
      
      Iterator it = gone.values().iterator();
      while (which>0 && it.hasNext()) {
        which--;
        it.next();
      }
  
      if (it.hasNext()) {
        // assert which==0
        if (logger.level <= Logger.FINEST) logger.log("getGone chose node from gone "+which);
        return ((GoneSetEntry)it.next()).nh;
      }
    }

    // pick a new random one, since we don't just want to pick the top few
    // entries of the routing table.
    if (logger.level <= Logger.FINEST) logger.log("getGone choosing node from routing table");
    
    int which = env.getRandomSource().nextInt(rt.numEntries());
    // else look in routing table
    for (int r = 0; r < rt.numRows(); r++) {
      RouteSet[] row = rt.getRow(r);
      for (int c = 0; c < rt.numColumns(); c++) {
        RouteSet entry = row[c];
        if (which > entry.size()) {
          which -= entry.size();
        } else {
          return entry.get(which);
        }
      }  
    }
    
    if (logger.level <= Logger.INFO) logger.log("getGone returning null; oops!");
    // oops, routing table has less entries than it claims
    return null;
  }
  
  // possibly make this abstract
  private void getNodeHandleToProbe(Continuation c) {
    if (env.getRandomSource().nextDouble() > bootstrapRate) {
      NodeHandle nh = getGone();
      if (logger.level <= Logger.FINEST) logger.log("getGone chose "+nh);
      if (nh != null) {
        c.receiveResult(nh);
        return;
      }
    }
    if (logger.level <= Logger.FINEST) logger.log("getNodeHandleToProbe choosing bootstrap");
    
    factory.getNodeHandle(bootstraps, c);
  }
  
  public void run() {
    doGoneMaintainence();
    
    getNodeHandleToProbe(new Continuation() {

      public void receiveResult(Object result) {
        JoinRequest jr = new JoinRequest(pastryNode.getLocalHandle(), pastryNode
            .getRoutingTable().baseBitLength());

        RouteMessage rm = new RouteMessage(pastryNode.getLocalHandle().getNodeId(), jr,
            new PermissiveCredentials(), jr.getDestination());
        rm.getOptions().setRerouteIfSuspected(false);
        ((NodeHandle)result).bootstrap(rm);
        
      }

      public void receiveException(Exception result) {
        // oh well
      }
      
    });
  }

  public void nodeSetUpdate(NodeSetEventSource nodeSetEventSource, NodeHandle handle, boolean added) {
    if (nodeSetEventSource.equals(pastryNode.getLeafSet())) {
      if (added) {
        synchronized(this) {
          gone.remove(handle.getId());
        }
      }
    }
    if (!added) {
      synchronized (this) {
        if (handle.getLiveness() == NodeHandle.LIVENESS_DEAD) {
          if (gone.containsKey(handle.getId())) {
            if (logger.level <= Logger.FINEST) logger.log("PartitionHandler updating node "+handle);
            ((GoneSetEntry)gone.get(handle.getId())).nh = handle;
          } else {
            GoneSetEntry g = new GoneSetEntry(handle, env.getTimeSource().currentTimeMillis());
            if (logger.level <= Logger.FINEST) logger.log("PartitionHandler adding node "+g);
            gone.put(handle.getId(),g);
          }
        }
      }
    }
  }
  
  static private class GoneSetEntry {
    public NodeHandle nh;
    public long timestamp;
    
    public GoneSetEntry(NodeHandle nh, long timestamp) {
      this.nh = nh;
      this.timestamp = timestamp;
    }
    
    public boolean equals(Object o) {
      GoneSetEntry other = (GoneSetEntry)o;
      return other.nh.getId().equals(nh.getId());
    }

    public String toString() {
      return nh.toString() + " "+timestamp;
    }    
  }

}
