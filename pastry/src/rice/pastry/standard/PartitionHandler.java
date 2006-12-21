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
package rice.pastry.standard;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.dist.DistPastryNodeFactory;
import rice.pastry.join.JoinRequest;
import rice.pastry.routing.*;
import rice.selector.Timer;
import rice.selector.TimerTask;

public class PartitionHandler extends TimerTask implements NodeSetListener {

  PastryNode pastryNode;

  // maybe move this to a subclass
  InetSocketAddress[] bootstraps;
  
  DistPastryNodeFactory factory;
  
  Logger logger;
  
  double bootstrapRate;
  int maxGoneSize;
  int maxGoneAge;
  // map from Id's -> NodeHandle's to keep things unique per NodeId
  Map gone;
  
  Environment env;
  
  // XXX think about multiring
  /**
   * You must call start after construction!!!
   */
  public PartitionHandler(PastryNode pn, DistPastryNodeFactory factory, InetSocketAddress[] bootstraps) {
    pastryNode = pn;
    this.factory = factory;
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
      Object key = gone.keySet().iterator().next();
      gone.remove(key);
    }
    if (logger.level <= Logger.FINER) logger.log("gone size 3 is "+gone.size()+" of "+maxGoneSize);
  }


  private List getRoutingTableAsList() {
    RoutingTable rt = pastryNode.getRoutingTable();
    List rtHandles = new ArrayList(rt.numEntries());

    for (int r = 0; r < rt.numRows(); r++) {
      RouteSet[] row = rt.getRow(r);
      for (int c = 0; c < rt.numColumns(); c++) {
        RouteSet entry = row[c];
        if (entry != null) {
          for (int i = 0; i < entry.size(); i++) {
            NodeHandle nh = entry.get(i);
            if (!nh.equals(pastryNode.getLocalHandle())) {
              rtHandles.add(nh);
            }
          }
        }
      }
    }

    return rtHandles;
  }
  
  private NodeHandle getGone() {
    synchronized (this) {
      int which = env.getRandomSource().nextInt(maxGoneSize);
      if (logger.level <= Logger.FINEST) logger.log("getGone choosing node "+which+" from gone or routing table");
      
      Iterator it = gone.values().iterator();
      while (which>0 && it.hasNext()) {
        which--;
        it.next();
      }
  
      if (it.hasNext()) {
        // assert which==0;
        if (logger.level <= Logger.FINEST) logger.log("getGone chose node from gone "+which);
        return ((GoneSetEntry)it.next()).nh;
      }
    }

    List rtHandles = getRoutingTableAsList();
    
    if (rtHandles.isEmpty()) {
      if (logger.level <= Logger.INFO) logger.log("getGone returning null; routing table is empty!");
      return null;
    }
    
    int which = env.getRandomSource().nextInt(rtHandles.size());

    if (logger.level <= Logger.FINEST) logger.log("getGone choosing node "+which+" from routing table");

    return (NodeHandle)rtHandles.get(which);
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
    if (logger.level <= Logger.INFO) logger.log("running partition handler");
    doGoneMaintainence();
    
    getNodeHandleToProbe(new Continuation() {

      public void receiveResult(Object result) {
        if (result != null) {
          JoinRequest jr = new JoinRequest(pastryNode.getLocalHandle(), pastryNode
              .getRoutingTable().baseBitLength());
  
          RouteMessage rm = new RouteMessage(pastryNode.getLocalHandle().getNodeId(), 
              jr);
          rm.setPrevNode(pastryNode.getLocalHandle());
          rm.getOptions().setRerouteIfSuspected(false);
          NodeHandle nh = pastryNode.coalesce((NodeHandle)result);
          try {
            nh.bootstrap(rm);
          } catch (IOException ioe) {
            if (logger.level <= Logger.WARNING) logger.logException("Error bootstrapping.",ioe); 
          }
        } else {
          if (logger.level <= Logger.INFO) logger.log("getNodeHandleToProbe returned null");
        }
      }

      public void receiveException(Exception result) {
        // oh well
        if (logger.level <= Logger.INFO) logger.logException("exception in PartitionHandler",result);
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

  public void start(Timer timer) {
    if (logger.level <= Logger.INFO) logger.log("installing partition handler");
    timer.schedule(this, env.getParameters().getInt("partition_handler_check_interval"), 
        env.getParameters().getInt("partition_handler_check_interval"));
  }

}
