package rice.pastry.pns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.mpisws.p2p.transport.proximity.ProximityListener;
import org.mpisws.p2p.transport.proximity.ProximityProvider;

import rice.Continuation;
import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.Cancellable;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.MessageDeserializer;
import rice.pastry.Id;
import rice.pastry.NodeHandle;
import rice.pastry.PastryNode;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.pns.messages.LeafSetRequest;
import rice.pastry.pns.messages.LeafSetResponse;
import rice.pastry.pns.messages.RouteRowRequest;
import rice.pastry.pns.messages.RouteRowResponse;
import rice.pastry.routing.RouteSet;
import rice.pastry.standard.ProximityNeighborSelector;
import rice.pastry.transport.PMessageNotification;
import rice.pastry.transport.PMessageReceipt;
import rice.selector.Timer;

/**
 * Can request LeafSet, RouteRow, Proximity of nodes, implemts the PNS algorithim.
 * 
 * TODO: Make use the environment's clock for the wait() calls.
 * 
 * @author Jeff Hoye
 *
 */
public class PNSApplication extends PastryAppl implements ProximityNeighborSelector, ProximityListener<NodeHandle> {
  public static final int DEFAULT_PROXIMITY = ProximityProvider.DEFAULT_PROXIMITY;
  
  /**
   * Hashtable which keeps track of temporary ping values, which are
   * only used during the getNearest() method
   */
  protected Hashtable<NodeHandle,Integer> pingCache = new Hashtable<NodeHandle,Integer>();
  
  protected final byte rtBase;
  
  protected Environment environment;

  protected Timer timer;

  final short depth; // = (Id.IdBitLength / rtBase);

  
  public PNSApplication(PastryNode pn) {
    super(pn, null, 0, null, pn.getEnvironment().getLogManager().getLogger(PNSApplication.class, null));
    setDeserializer(new PNSDeserializer());
    this.environment = pn.getEnvironment();
    rtBase = (byte)environment.getParameters().getInt("pastry_rtBaseBitLength");
    depth = (short)(Id.IdBitLength / rtBase);
  }

  @Override
  public void messageForAppl(Message msg) {
//    logger.log("messageForAppl("+msg+")");
    if (logger.level <= Logger.FINER) logger.log("messageForAppl("+msg+")");

    if (msg instanceof LeafSetRequest) {
      LeafSetRequest req = (LeafSetRequest)msg;
      thePastryNode.send(req.getSender(), new LeafSetResponse(thePastryNode.getLeafSet(), getAddress()), null, null);
      return;
    }
    
    if (msg instanceof LeafSetResponse) {
      LeafSetResponse response = (LeafSetResponse)msg;
      synchronized (waitingForLeafSet) {
        LeafSet ls = response.leafset;
        Collection<Continuation<LeafSet, Exception>> waiters = waitingForLeafSet.remove(ls.get(0));
        if (waiters != null) {
          for (Continuation<LeafSet, Exception> w : waiters) {
            w.receiveResult(ls);            
          }
        }
      }
      return;
    }
    
    if (msg instanceof RouteRowRequest) {
      RouteRowRequest req = (RouteRowRequest)msg;
      thePastryNode.send(req.getSender(), 
          new RouteRowResponse(
              thePastryNode.getLocalHandle(), 
              req.index, 
              thePastryNode.getRoutingTable().getRow(req.index), getAddress()), null, null);
      return;
    }
    
    if (msg instanceof RouteRowResponse) {
      RouteRowResponse response = (RouteRowResponse)msg;
      synchronized (waitingForRouteRow) {
        Collection<Continuation<RouteSet[], Exception>>[] waiters = waitingForRouteRow.get(response.getSender());
        if (waiters != null) {
          if (waiters[response.index] != null) {
            for (Continuation<RouteSet[], Exception> w : waiters[response.index]) {
              w.receiveResult(response.row);            
            }
            waiters[response.index] = null;
            
            // remove the entry if all rows empty
            boolean deleteIt = true;
            for (int i = 0; i < depth; i++) {
              if (waiters[i] != null) {
                deleteIt = false;
                break;
              }
            }
          
            if (deleteIt) waitingForRouteRow.remove(response.getSender());
          }
        }
      }
      return;
    }
    

    if (logger.level <= Logger.WARNING) logger.log("unrecognized message in messageForAppl("+msg+")");
  }

  public void getNearHandles(Collection<NodeHandle> bootHandles, Continuation<Collection<NodeHandle>, Exception> deliverResultToMe) {
    if (bootHandles == null || bootHandles.size() == 0 || bootHandles.iterator().next() == null) {
      deliverResultToMe.receiveResult(bootHandles);
      return;
    }
    
    thePastryNode.addProximityListener(this);
    NodeHandle f = bootHandles.iterator().next();
    List<NodeHandle> ret = Arrays.asList(getNearest(f));
    thePastryNode.removeProximityListener(this);
    deliverResultToMe.receiveResult(ret);

  }
  
  /**
   * This method returns the remote leafset of the provided handle
   * to the caller, in a protocol-dependent fashion.  Note that this method
   * may block while sending the message across the wire.
   *
   * @param handle The node to connect to
   * @return The leafset of the remote node
   */
  public LeafSet getLeafSet(NodeHandle handle) throws IOException {
    if (logger.level <= Logger.FINER) logger.log("getLeafSet("+handle+")");
    final LeafSet[] container = new LeafSet[1];
    // 20 second timeout
    synchronized(container) {
      getLeafSet(handle, new Continuation<LeafSet, Exception>() {      
        public void receiveResult(LeafSet result) {
          synchronized (container) {
            container[0] = result;
            container.notify();
          }
        }      
        public void receiveException(Exception exception) {
          synchronized (container) {
            container.notify();
          }          
        }      
      });
      
      try {
        container.wait(20000);
      } catch (InterruptedException e) {
        // continue
      }
    }
    
    if (logger.level <= Logger.FINE) logger.log("getLeafSet("+handle+") returning "+container[0]);
    return container[0];
  }
  
  /**
   * Non-blocking version.
   * 
   * @param handle
   * @param c
   * @return
   * @throws IOException
   */
  public Cancellable getLeafSet(final NodeHandle handle, final Continuation<LeafSet, Exception> c) {
    final Cancellable[] subCancellable = new Cancellable[1];
    
    Cancellable ret = new Cancellable() {    
      public boolean cancel() {
        if (subCancellable[0] != null) subCancellable[0].cancel();
        return removeFromWaitingForLeafSet(handle, c);
      }    
    };
    
    addToWaitingForLeafSet(handle, c);
    
    subCancellable[0] = thePastryNode.send(handle, new LeafSetRequest(this.getNodeHandle(), this.getAddress()), new PMessageNotification() {
      public void sent(PMessageReceipt msg) {        
      }
      public void sendFailed(PMessageReceipt msg, Exception reason) {
        removeFromWaitingForLeafSet(handle, c);
        c.receiveException(reason);
      }    
    }, null);
    
    return ret;
  }

  Map<NodeHandle, Collection<Continuation<LeafSet, Exception>>> waitingForLeafSet = 
    new HashMap<NodeHandle, Collection<Continuation<LeafSet, Exception>>>();
  
  protected boolean removeFromWaitingForLeafSet(NodeHandle handle, Continuation<LeafSet, Exception> c) {
    synchronized (waitingForLeafSet) {
      Collection<Continuation<LeafSet, Exception>> waiters = waitingForLeafSet.get(handle);
      if (waiters == null) return false;          
      boolean ret = waiters.remove(c);
      if (waiters.isEmpty()) waitingForLeafSet.remove(handle);
      return ret;
    }    
  }
  
  protected void addToWaitingForLeafSet(NodeHandle handle, Continuation<LeafSet, Exception> c) {
    synchronized (waitingForLeafSet) {
      Collection<Continuation<LeafSet, Exception>> waiters = waitingForLeafSet.get(handle);
      if (waiters == null) {
        waiters = new ArrayList<Continuation<LeafSet,Exception>>();
        waitingForLeafSet.put(handle, waiters);
      }
      waiters.add(c);
    }    
  }
  
  /**
   * This method returns the remote route row of the provided handle
   * to the caller, in a protocol-dependent fashion.  Note that this method
   * may block while sending the message across the wire.
   *
   * @param handle The node to connect to
   * @param row The row number to retrieve
   * @return The route row of the remote node
   */
  public RouteSet[] getRouteRow(final NodeHandle handle, final short row) throws IOException {
    if (logger.level <= Logger.FINER) logger.log("getRouteRow("+handle+")");
    final RouteSet[][] container = new RouteSet[1][0];
    // 20 second timeout
    synchronized(container) {
      getRouteRow(handle, row, new Continuation<RouteSet[], Exception>() {      
        public void receiveResult(RouteSet[] result) {
          synchronized (container) {
            container[0] = result;
            container.notify();
          }
        }      
        public void receiveException(Exception exception) {
          synchronized (container) {
            container.notify();
          }          
        }      
      });
      
      try {
        container.wait(20000);
      } catch (InterruptedException e) {
        // continue
      }
    }
    
    if (logger.level <= Logger.FINE) logger.log("getRouteRow("+handle+") returning "+container[0]);
    return container[0];
  }
  
  /**
   * Non-blocking version.
   * 
   * @param handle
   * @param row
   * @param c
   * @return
   * @throws IOException
   */
  public Cancellable getRouteRow(final NodeHandle handle, final short row, final Continuation<RouteSet[], Exception> c) {
    final Cancellable[] subCancellable = new Cancellable[1];
    
    Cancellable ret = new Cancellable() {    
      public boolean cancel() {
        if (subCancellable[0] != null) subCancellable[0].cancel();
        return removeFromWaitingForRouteRow(handle, row, c);
      }    
    };
    
    addToWaitingForRouteRow(handle, row, c);
    
    subCancellable[0] = thePastryNode.send(handle, new RouteRowRequest(this.getNodeHandle(), row, this.getAddress()), new PMessageNotification() {
      public void sent(PMessageReceipt msg) {        
      }
      public void sendFailed(PMessageReceipt msg, Exception reason) {
        removeFromWaitingForRouteRow(handle, row, c);
        c.receiveException(reason);
      }    
    }, null);
    
    return ret;
  }

  Map<NodeHandle, Collection<Continuation<RouteSet[], Exception>>[]> waitingForRouteRow = 
    new HashMap<NodeHandle, Collection<Continuation<RouteSet[], Exception>>[]>();
  
  protected void addToWaitingForRouteRow(NodeHandle handle, int row, Continuation<RouteSet[], Exception> c) {
    synchronized (waitingForRouteRow) {
      Collection<Continuation<RouteSet[], Exception>>[] waiters = waitingForRouteRow.get(handle);
      if (waiters == null) {
        waiters = new Collection[depth];
        waitingForRouteRow.put(handle, waiters);
      }
      if (waiters[row] == null) {
        waiters[row] = new ArrayList<Continuation<RouteSet[],Exception>>();        
      }
      waiters[row].add(c);
    }    
  }
  
  protected boolean removeFromWaitingForRouteRow(NodeHandle handle, int row, Continuation<RouteSet[], Exception> c) {
    synchronized (waitingForRouteRow) {
      Collection<Continuation<RouteSet[], Exception>>[] waiters = waitingForRouteRow.get(handle);
      if (waiters == null) return false;
      if (waiters[row] == null) return false;
      boolean ret = waiters[row].remove(c);
      
      // remove the row if empty
      if (waiters[row].isEmpty()) {
        waiters[row] = null;
      }
      
      // remove the entry if all rows empty
      boolean deleteIt = true;
      for (int i = 0; i < depth; i++) {
        if (waiters[i] != null) {
          deleteIt = false;
          break;
        }
      }
      
      if (deleteIt) waitingForRouteRow.remove(handle);

      return ret;
    }    
  }
  
  
  /**
   * This method determines and returns the proximity of the current local
   * node the provided NodeHandle.  This will need to be done in a protocol-
   * dependent fashion and may need to be done in a special way.
   *
   * Timeout of 5 seconds.
   *
   * @param handle The handle to determine the proximity of
   * @return The proximity of the provided handle
   */
  public int getProximity(NodeHandle handle) {    
    final int[] container = new int[1];
    container[0] = DEFAULT_PROXIMITY;
    synchronized (container) {
      getProximity(handle, new Continuation<Integer, IOException>() {      
        public void receiveResult(Integer result) {
          synchronized(container) {
            container[0] = result.intValue();
            container.notify();
          }
        }
      
        public void receiveException(IOException exception) {
          synchronized(container) {
            container.notify();
          }
        }      
      });
      
      if (container[0] == DEFAULT_PROXIMITY) {
        try {
          container.wait(5000);
        } catch(InterruptedException ie) {
          // continue to return        
        }
      }
    }
    if (logger.level <= Logger.FINE) logger.log("getProximity(handle) returning "+container[0]);
    return container[0];
  }

  /**
   * Non-blocking version, no timeout.
   * 
   * TODO: Make this fail early if faulty.
   * 
   * @param handle
   * @param c
   */
  public void getProximity(NodeHandle handle, Continuation<Integer, IOException> c) {
    int prox;
    // acquire a lock that will block proximityChanged()
    synchronized(waitingForPing) {
      // see what we have for proximity (will initiate a checkLiveness => proximityChanged() if DEFAULT)
      prox = thePastryNode.proximity(handle);
      if (prox == DEFAULT_PROXIMITY) {
        // need to wait
        Collection<Continuation<Integer, IOException>> waiters = waitingForPing.get(handle);
        if (waiters == null) {          
          waiters = new ArrayList<Continuation<Integer,IOException>>();
          waitingForPing.put(handle, waiters);
        }
        waiters.add(c);
        return;
      }
      
      // else we have the proximity, no need to wait for it, just return the correct result
    }
    // we already had the right proximity
    c.receiveResult(prox);    
  }


  Map<NodeHandle, Collection<Continuation<Integer, IOException>>> waitingForPing = 
    new HashMap<NodeHandle, Collection<Continuation<Integer, IOException>>>();
  public void proximityChanged(NodeHandle i, int newProximity, Map<String, Integer> options) {
    synchronized(waitingForPing) {
      if (waitingForPing.containsKey(i)) {
        Collection<Continuation<Integer, IOException>> waiting = waitingForPing.remove(i);
        for (Continuation<Integer, IOException>c : waiting) {
          c.receiveResult(newProximity);
        }
      }
    }    
  }

  
  /**
   * Method which checks to see if we have a cached value of the remote ping, and
   * if not, initiates a ping and then caches the value
   *
   * @param handle The handle to ping
   * @return The proximity of the handle
   */
  protected int proximity(NodeHandle handle) {
    Hashtable<NodeHandle,Integer> localTable = pingCache;
    
    if (pingCache.get(handle) == null) {
      int value = getProximity(handle);
      pingCache.put(handle, value);

      return value;
    } else {
      return ((Integer) pingCache.get(handle)).intValue();
    }
  }
  
  private void purgeProximityCache() {
    pingCache.clear(); 
  }
  
  public NodeHandle[] sortedProximityCache() {
    ArrayList<NodeHandle> handles = new ArrayList<NodeHandle>(pingCache.keySet());
    Collections.sort(handles,new Comparator<NodeHandle>() {
    
      public int compare(NodeHandle a, NodeHandle b) {
        return pingCache.get(a).intValue()-pingCache.get(b).intValue();
      }    
    });
    
//    Iterator<NodeHandle> i = handles.iterator();
//    while(i.hasNext()) {
//      NodeHandle nh = i.next();
//      System.out.println(nh+":"+localTable.get(nh));
//    }
    
    return (NodeHandle[])handles.toArray(new NodeHandle[0]);
  }
  
  /**
   * This method implements the algorithm in the Pastry locality paper
   * for finding a close node the the current node through iterative
   * leafset and route row requests.  The seed node provided is any
   * node in the network which is a member of the pastry ring.  This
   * algorithm is designed to work in a protocol-independent manner, using
   * the getResponse(Message) method provided by subclasses.
   *
   * @param seed Any member of the pastry ring
   * @return A node suitable to boot off of (which is close the this node)
   */
  public NodeHandle[] getNearest(NodeHandle seed) {
    try {
      // if the seed is null, we can't do anything
      if (seed == null)
        return null;
      
      // seed is the bootstrap node that we use to enter the pastry ring
      NodeHandle currentClosest = seed;
      NodeHandle nearNode = seed;
      
      // get closest node in leafset
      nearNode = closestToMe(nearNode, getLeafSet(nearNode));
      
      // get the number of rows in a routing table
      // -- Here, we're going to be a little inefficient now.  It doesn't
      // -- impact correctness, but we're going to walk up from the bottom
      // -- of the routing table, even through some of the rows are probably
      // -- unfilled.  We'll optimize this in a later iteration.
      short i = 0;

      
      
      // make "ALL" work
      if (!environment.getParameters().getString("pns_num_rows_to_use").
          equalsIgnoreCase("all")) {
        i = (short)(depth-(short)(environment.getParameters().getInt("pns_num_rows_to_use")));
      }
      
      // fix it up to not throw an error if the number is too big
      if (i < 0) i = 0;
      
      // now, iteratively walk up the routing table, picking the closest node
      // each time for the next request
      while (i < depth) {
        nearNode = closestToMe(nearNode, getRouteRow(nearNode, i));
        i++;
      }
      
      // finally, recursively examine the top level routing row of the nodes
      // until no more progress can be made
      do {
        currentClosest = nearNode;
        nearNode = closestToMe(nearNode, getRouteRow(nearNode, (short)(depth-1)));
      } while (! currentClosest.equals(nearNode));
      
      if (nearNode.getLocalNode() == null) {
        // this is messy, but here's the deal:
        // when this is called by user code, local will have a localNode
        // when SPNF calls it, the node may not, because I needed to move
        // getNearest() to before the creation of the pastry node so that it
        // uses the same port, this is necessary so that the firewall settings
        // will work, otherwies, getNearest() used to have to bind to the next port
        // because the pastry node was already bound to its port, now, we do 
        // getNearest() first so we can keep the port the same
        if (thePastryNode != null)
          nearNode = thePastryNode.coalesce(nearNode);
      }
      
      // return the resulting closest node
//      return nearNode;
      return sortedProximityCache();
    } catch (IOException e) {
      if (logger.level <= Logger.WARNING) logger.logException(
        "ERROR occured while finding best bootstrap.", e);
      return new NodeHandle[]{seed};
    } finally {
      purgeProximityCache(); 
    }
  }

  /**
   * This method returns the closest node to the current node out of
   * the union of the provided handle and the node handles in the
   * leafset
   *
   * @param handle The handle to include
   * @param leafSet The leafset to include
   * @return The closest node out of handle union leafset
   */
  private NodeHandle closestToMe(NodeHandle handle, LeafSet leafSet)  {
    Vector handles = new Vector();

    for (int i = 1; i <= leafSet.cwSize() ; i++)
      handles.add(leafSet.get(i));

    for (int i = -leafSet.ccwSize(); i < 0; i++)
      handles.add(leafSet.get(i));

    return closestToMe(handle, (NodeHandle[]) handles.toArray(new NodeHandle[0]));
  }

  /**
   * This method returns the closest node to the current node out of
   * the union of the provided handle and the node handles in the
   * routeset
   *
   * @param handle The handle to include
   * @param routeSet The routeset to include
   * @return The closest node out of handle union routeset
   */
  private NodeHandle closestToMe(NodeHandle handle, RouteSet[] routeSets) {
    Vector handles = new Vector();

    for (int i=0 ; i<routeSets.length ; i++) {
      RouteSet set = routeSets[i];

      if (set != null) {
        for (int j=0; j<set.size(); j++)
          handles.add(set.get(j));
      }
    }

    return closestToMe(handle, (NodeHandle[]) handles.toArray(new NodeHandle[0]));
  }

  /**
   * This method returns the closest node to the current node out of
   * the union of the provided handle and the node handles in the
   * array
   *
   * @param handle The handle to include
   * @param handles The array to include
   * @return The closest node out of handle union array
   */
  private NodeHandle closestToMe(NodeHandle handle, NodeHandle[] handles) {
    NodeHandle closestNode = handle;

    // shortest distance found till now    
    int nearestdist = proximity(closestNode);  

    for (int i=0; i < handles.length; i++) {
      NodeHandle tempNode = handles[i];

      int prox = proximity(tempNode);
      
      if ((prox > 0) && (prox < nearestdist) && tempNode.isAlive()) {
        nearestdist = prox;
        closestNode = tempNode;
      }
    }
    
    return closestNode;
  }


  
  class PNSDeserializer implements MessageDeserializer {
    public rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type, int priority, rice.p2p.commonapi.NodeHandle sender) throws IOException {
      switch(type) {
      case LeafSetRequest.TYPE:
        return LeafSetRequest.build(buf, (NodeHandle)sender, getAddress());
      case LeafSetResponse.TYPE:
        return LeafSetResponse.build(buf, thePastryNode, getAddress());
      case RouteRowRequest.TYPE:
        return RouteRowRequest.build(buf, (NodeHandle)sender, getAddress());
      case RouteRowResponse.TYPE:
        return new RouteRowResponse(buf, thePastryNode, (NodeHandle)sender, getAddress());
      }
      // TODO Auto-generated method stub
      return null;
    }

  }
}
