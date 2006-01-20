package rice.pastry.standard;

import java.net.InetSocketAddress;
import java.util.*;

import rice.Continuation;
import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.leafset.LeafSet;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;
import rice.selector.TimerTask;

public class PartitionHandler extends TimerTask implements NodeSetListener {

  PastryNode pastryNode;

  // maybe move this to a subclass
  InetSocketAddress[] bootstraps;
  
  DistPastryNodeFactory factory;
  
  PastrySecurityManager sec;
  
  double bootstrapRate;
  int maxGoneSize;
  int maxGoneAge;
  // map from Id's -> NodeHandle's to keep things unique per NodeId
  Map gone;
  
  Environment env;
  
  public PartitionHandler(PastryNode pn, DistPastryNodeFactory factory, PastrySecurityManager sec, InetSocketAddress[] bootstraps) {
    pastryNode = pn;
    this.factory = factory;
    this.sec = sec;
    this.bootstraps = bootstraps;
    env = pastryNode.getEnvironment();
    gone = new HashMap();
    
    maxGoneSize = env.getParameters().getInt("partition_handler_max_history_size");
    maxGoneAge = env.getParameters().getInt("partition_handler_max_history_age");
    bootstrapRate = env.getParameters().getDouble("partition_handler_bootstrap_check_rate");
    
    pastryNode.getLeafSet().addNodeSetListener(this);
    pastryNode.getRoutingTable().addNodeSetListener(this);
  }
  
  private synchronized void doGoneMaintainence() {
    Iterator it = gone.values().iterator();
    long now = env.getTimeSource().currentTimeMillis();
    
    while (it.hasNext()) {
      GoneSetEntry g = (GoneSetEntry)it.next();
      if ((now - g.timestamp > maxGoneAge) || // toss if too old
          (g.nh.getLiveness() > NodeHandle.LIVENESS_DEAD)) { // toss if epoch changed
        it.remove();
      } 
    }
    
    while (gone.size() > maxGoneSize) {
      gone.entrySet().iterator().remove();
    }
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
        return ((GoneSetEntry)it.next()).nh;
      }
    }

    // pick a new random one, since we don't just want to pick the top few
    // entries of the routing table.
    
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
    
    // oops, routing table has less entries than it claims
    return null;
  }
  
  // possibly make this abstract
  private void getNodeHandleToProbe(Continuation c) {
    if (env.getRandomSource().nextDouble() > bootstrapRate) {
      NodeHandle nh = getGone();
      if (nh != null) {
        c.receiveResult(nh);
        return;
      }
    }
    
    factory.getNodeHandle(bootstraps, c);
  }
  
  public void run() {
    doGoneMaintainence();
    
    getNodeHandleToProbe(new Continuation() {

      public void receiveResult(Object result) {
        // XXX can't do getNearest() because it will likely stay in our partition
        // have to route a message (like a JoinRequest) to our key via the result node
        final NodeHandle nearest = factory.getNearest(pastryNode.getLocalHandle(), (NodeHandle)result);
        if (!nearest.equals(pastryNode.getLocalHandle())) {
          factory.getLeafSet(nearest, new Continuation() {

            public void receiveResult(Object result) {
              LeafSet nearestLeafSet = (LeafSet)result;
              HashSet inserted = new HashSet(); 
              pastryNode.getLeafSet().merge(nearestLeafSet, nearest, pastryNode.getRoutingTable(), sec, false, inserted);
              Iterator it = inserted.iterator();
              while(it.hasNext()) {
                ((NodeHandle)it.next()).checkLiveness(); 
              }
            }
            
            public void receiveException(Exception result) {
              // oh well
            } 
          });
        }
        
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
            ((GoneSetEntry)gone.get(handle.getId())).nh = handle;
          } else {
            gone.put(handle.getId(),new GoneSetEntry(handle, env.getTimeSource().currentTimeMillis()));
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
    
  }

}
