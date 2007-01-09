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
package rice.pastry.socket;

import java.io.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
import rice.environment.random.RandomSource;
import rice.environment.time.TimeSource;
//import rice.p2p.commonapi.MessageDeserializer;
//import rice.p2p.commonapi.AppSocketReceiver;
import rice.p2p.commonapi.appsocket.AppSocketReceiver;
import rice.p2p.commonapi.exception.NodeIsDeadException;
import rice.p2p.util.TimerWeakHashMap;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

/**
 * Class which keeps track of the best routes to remote nodes.  This class
 * is also therefore in charge of declaring node death and liveness.  
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SocketSourceRouteManager {
  
  // the minimum amount of time between check dead checks on dead routes
  public long CHECK_DEAD_THROTTLE;
  
  // the minimum amount of time between pings
  public long PING_THROTTLE;
  
  public int NUM_SOURCE_ROUTE_ATTEMPTS;
  
  /**
   * millis for the timeout
   * 
   * The idea is that we don't want this parameter to change too fast, 
   * so this is the timeout for it to increase, you could set this to infinity, 
   * but that may be bad because it doesn't account for intermediate link failures
   */
  public int PROX_TIMEOUT;// = 60*60*1000;

  public int DEFAULT_RTO;// = 3000;
  
  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  int RTO_UBOUND;// = 10000; // 10 seconds
  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  int RTO_LBOUND;// = 50;

  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  double gainH;// = 0.25;

  /**
   * RTO helper see RFC 1122 for a detailed description of RTO calculation
   */
  double gainG;// = 0.125;

  // the local pastry node
  private SocketPastryNode spn;
  
  private TimeSource time;
  
  // the socket manager below this manager
  private SocketCollectionManager manager;
  
  // the address of this local node
  private EpochInetSocketAddress localAddress;
  
  private Logger logger;
  
  /**
   * Invariant: if an AM has a message in its queue, it is in here, if not, it isn't.
   * 
   * This prevents outgoing messages from being lost.
   */
  HashSet hardLinks = new HashSet();

  TimerWeakHashSet nodeHandles; 
  
  /**
   * Constructor
   *
   * @param node The local node
   * @param pool The node hanlde pool
   * @param bindAddress The address which the node should bind to
   * @param proxyAddress The address which the node should advertise as it's address
   */
  protected SocketSourceRouteManager(SocketPastryNode node, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress, RandomSource random) throws IOException {
    this.spn = node;
    this.time = spn.getEnvironment().getTimeSource();
    
    Parameters p = node.getEnvironment().getParameters();
    CHECK_DEAD_THROTTLE = p.getLong("pastry_socket_srm_check_dead_throttle"); // 300000
    PING_THROTTLE = p.getLong("pastry_socket_srm_ping_throttle");
    NUM_SOURCE_ROUTE_ATTEMPTS = p.getInt("pastry_socket_srm_num_source_route_attempts");
    PROX_TIMEOUT = p.getInt("pastry_socket_srm_proximity_timeout");
    DEFAULT_RTO = p.getInt("pastry_socket_srm_default_rto"); // 3000 // 3 seconds
    RTO_UBOUND = p.getInt("pastry_socket_srm_rto_ubound");//240000; // 240 seconds
    RTO_LBOUND = p.getInt("pastry_socket_srm_rto_lbound");//1000;
    gainH = p.getDouble("pastry_socket_srm_gain_h");//0.25;
    gainG = p.getDouble("pastry_socket_srm_gain_g");//0.125;
    
//    nodeHandles = Collections.synchronizedMap(new TimerWeakHashMap(node.getEnvironment().getSelectorManager().getTimer(),30000));
    nodeHandles = new TimerWeakHashSet(30000, spn);
    
    this.logger = node.getEnvironment().getLogManager().getLogger(SocketSourceRouteManager.class, null);
    this.manager = new SocketCollectionManager(node, this, bindAddress, proxyAddress, random);
    this.localAddress = bindAddress;
    
  }
  
  /**
   * 
   * @return the best source route for each known EpochInetSocketAddress, keyed by the EISA
   */
  public HashMap getBest() {
    return nodeHandles.getBest();
  }
  
  /**
   * Makes this node resign from the network.  Is designed to be used for
   * debugging and testing.
   */
  public void destroy() throws IOException {
    if (spn.getEnvironment().getSelectorManager().isSelectorThread()) {
      manager.destroy();
    } else {
      spn.getEnvironment().getSelectorManager().invoke(new Runnable() {    
        public void run() {
          try {
            destroy();
          } catch (IOException ioe) {
            if (logger.level <= Logger.WARNING) logger.logException("Exception while destrying SocketSourceRouteManager",ioe);
          }
        }
      });
    }
  }
  
  /**
   * Method which returns the internal manager
   *
   * @return The internal manager
   */
  public SocketCollectionManager getManager() {
    return manager;
  }
  
  /**
   * Internal method which returns (or builds) the manager associated
   * with an address
   *
   * @param address The remote address
   */
  protected AddressManager getAddressManager(EpochInetSocketAddress address, boolean search) {
    synchronized(nodeHandles) {
      AddressManager manager = getAddressManager(address); 
      
      if (manager == null) {
        manager = putAddressManager(address, search);
      }
      
      return manager;
    }
  }

  /**
   * EpochInetSocketAddress -> WeakReference(NodeHandle)
   * 
   * Note that it is critical to keep the key == NodeHandle.eaddress.
   * 
   * And I mean the same object!!! not .equals(). The whole memory management
   * will get confused if this is not the case.
   */
  public SocketNodeHandle coalesce(SocketNodeHandle newHandle) {
    return nodeHandles.coalesce(newHandle); 
  }
//  public NodeHandle coalesce(NodeHandle newHandle) {
//    SocketNodeHandle snh = (SocketNodeHandle) newHandle;
//    synchronized (nodeHandles) {
//      WeakReference wr = (WeakReference) nodeHandles.get(snh.eaddress);
//      if (wr == null) {
//        logger.log("SSRM.coalesce1("+snh+")");
//        addNodeHandle(snh);
//        return snh;
//      } else {
//        SocketNodeHandle ret = (SocketNodeHandle) wr.get();
//        if (ret == null) {
//          // if this happens, then the handle got collected, but not the
//          // eaddress yet. Grumble...
//          logger.log("SSRM.coalesce2("+snh+")");
//          addNodeHandle(snh);
//          return snh;
//        } else {
//          // inflates a stub NodeHandle
//          if (ret.getNodeId() == null) {
//            ret.setNodeId(newHandle.getNodeId());
//          }
////          logger.log("SSRM.coalesce3("+newHandle+";"+snh.eaddress.hashCode()+"):"+ret+";"+ret.eaddress.hashCode());
//          return ret;
//        }
//      }
//    }
//  }

//  private void addNodeHandle(SocketNodeHandle snh) {
//    logger.log("addNodeHandle("+snh+"):"+spn.getEnvironment().getSelectorManager().isSelectorThread());
//    WeakReference wr = new WeakReference(snh);
//    nodeHandles.put(snh.eaddress, wr);
//    snh.setLocalNode(spn);
//  }

  public SocketNodeHandle getNodeHandle(EpochInetSocketAddress address) {
    SocketNodeHandle ret = nodeHandles.get(address);
//    synchronized (nodeHandles) {
//      WeakReference wr = (WeakReference) nodeHandles.get(address);
//      if (wr == null)
//        return null;
//
//      SocketNodeHandle ret = (SocketNodeHandle) wr.get();
//      if (ret == null)
//        return null;
      if (ret == null || ret.getNodeId() == null)
        return null;
      return ret;
//    }
  }

  public AddressManager getAddressManager(EpochInetSocketAddress address) {
    SocketNodeHandle snh = nodeHandles.get(address);
    
//    WeakReference wr = (WeakReference) nodeHandles.get(address);
//    if (wr == null)
//      return null;
//
//    SocketNodeHandle snh = (SocketNodeHandle) wr.get();
    if (snh == null)
      return null;
    return snh.addressManager;
  }

  /**
   * Should be called while synchronized on nodeHandles
   * 
   * @param address
   * @param manager
   */
  public AddressManager putAddressManager(EpochInetSocketAddress address,
      boolean search) {

    SocketNodeHandle snh = nodeHandles.get(address);
    if (snh == null) {
      snh = nodeHandles.coalesce(new SocketNodeHandle(address, null)); 
    }
    
//    WeakReference wr = (WeakReference) nodeHandles.get(address);
//    logger.log("putAddressManager("+address+","+search+"):"+wr+" "+spn.getEnvironment().getSelectorManager().isSelectorThread());
//    SocketNodeHandle snh;
//    if (wr == null) {
//      snh = new SocketNodeHandle(address, null);
//      addNodeHandle(snh);    
//    } else {
//      snh = (SocketNodeHandle) wr.get();
//      if (snh == null) {
//        // WARNING: this code must be repeated because of a very slight timing
//        // issue with the garbage collector
//        snh = new SocketNodeHandle(address, null);
//        addNodeHandle(snh);      
//      }
//    }

    if (snh.addressManager != null)
      throw new IllegalStateException("Address manager for address " + address
          + " already exists.");

    AddressManager manager = new AddressManager(snh, search);
    
    // TODO make this time configurable
    // yes, this is bizarre, becasue manager is not actually a key in nodeHandles, but it will work
    // refresh will hold a hard link to the manager for an amount of time, this keeps it from 
    // expiring early, and the manager holds a reference to the snh.  This is important in case no 
    // data-structures in FP are holding a reference to the SNH, we don't want it to evaporate and 
    // for all of this work to go away.
    spn.getEnvironment().getSelectorManager().getTimer().schedule(
        new TimerWeakHashMap.HardLinkTimerTask(manager), 30000);
    //nodeHandles.refresh(manager);
    snh.addressManager = manager;
    return manager;
  }
  
  /**
   * Method which sends a bootstrap message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void bootstrap(EpochInetSocketAddress address, Message msg) throws IOException {
//    PRawMessage rm;
//    if (msg instanceof PRawMessage) {
//      rm = (PRawMessage)msg; 
//    } else {
//      rm = new PJavaSerializedMessage(msg); 
//    }
//    // todo, pool
//    final SocketBuffer message = new SocketBuffer(defaultDeserializer);
//    message.serialize(rm, logger);
    manager.bootstrap(SourceRoute.build(address), msg);
  }
  
  public void send(EpochInetSocketAddress address, Message msg) throws IOException {    
    PRawMessage rm;
    if (msg instanceof PRawMessage) {
      rm = (PRawMessage)msg; 
    } else {
      rm = new PJavaSerializedMessage(msg); 
    }
    // todo, pool
    final SocketBuffer buffer = new SocketBuffer(manager.defaultDeserializer, manager.pastryNode);
    buffer.serialize(rm, true);
    send(address, buffer);
  }  
  
  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void send(final EpochInetSocketAddress address, final SocketBuffer message) {    
    if (spn.getEnvironment().getSelectorManager().isSelectorThread()) {
      getAddressManager(address, true).send(message);
    } else {
      if (logger.level <= Logger.FINE) logger.log("Application attempted to send "+message+" to "+address+" on a non-selector thread.");
      spn.getEnvironment().getSelectorManager().invoke(new Runnable() {      
        public void run() {
          getAddressManager(address, true).send(message);
        }
      });
    }
  }
  
  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void connect(final EpochInetSocketAddress address, final int appAddress, final AppSocketReceiver receiver, final int timeout) {
    if (spn.getEnvironment().getSelectorManager().isSelectorThread()) {
      getAddressManager(address, true).connect(appAddress, receiver, timeout);
    } else {
      if (logger.level <= Logger.FINE) logger.log("Application "+appAddress+" attempted to open a connection to "+address+" on a non-selector thread.");
      spn.getEnvironment().getSelectorManager().invoke(new Runnable() {      
        public void run() {
          getAddressManager(address, true).connect(appAddress, receiver, timeout);
        }
      });
    }
  }
  
  /**
   * Method which suggests a ping to the remote node.
   *
   * @param address DESCRIBE THE PARAMETER
   * @param prl DESCRIBE THE PARAMETER
   */
  public void ping(EpochInetSocketAddress address) {
    AddressManager am = getAddressManager(address);
    
    if (am == null)
      manager.ping(SourceRoute.build(address));
    else
      am.ping();
  } 
  
  /**
   * Method which FORCES a check of liveness of the remote node.  Note that
   * this method should ONLY be called by internal Pastry maintenance algorithms - 
   * this is NOT to be used by applications.  Doing so will likely cause a
   * blowup of liveness traffic.
   *
   * @return true if node is currently alive.
   */
  public void checkLiveness(EpochInetSocketAddress address) {
    getAddressManager(address, true).checkLiveness();
  }
  
  /**
   * Method which returns the last cached proximity value for the given address.
   * If there is no cached value, then DEFAULT_PROXIMITY is returned.
   *
   * @param address The address to return the value for
   * @return The ping value to the remote address
   */
  public int proximity(EpochInetSocketAddress address) {
    AddressManager am = getAddressManager(address);
    
    if (am == null)
      return SocketNodeHandle.DEFAULT_PROXIMITY;
    else
      return am.proximity();  
  }

  /**
   * Method which returns the last cached liveness value for the given address.
   * If there is no cached value, then LIVENESS_ALIVE
   *
   * @param address The address to return the value for
   * @return The liveness value
   */
  public int getLiveness(EpochInetSocketAddress address) {
    return getAddressManager(address, true).getLiveness();
  }  
  
  /**
   * This method should be called when a known route is declared
   * dead.
   *
   * @param route The now-dead route
   */
  protected void markDead(SourceRoute route) {
    if (logger.level <= Logger.FINE) logger.log( "(SSRM) Found route " + route + " to be dead");

    AddressManager am = getAddressManager(route.getLastHop());
    
    if (am != null)
      am.markDead(route);
  }
  
  /**
   * This method should be called when a known node is declared dead - this is
   * ONLY called when a new epoch of that node is detected.  Note that this method
   * is silent - no checks are done.  Caveat emptor.
   *
   * @param address The now-dead address
   */
  protected void markDead(EpochInetSocketAddress address) {
    AddressManager am = getAddressManager(address);
    
    if (am != null)
      am.markDeadForever();
  }
  
  /**
   * This method should be called when a known route is declared
   * alive.
   *
   * @param route The now-live route
   */
  protected void markAlive(SourceRoute route) {
    if (logger.level <= Logger.FINE) logger.log( "(SSRM) Found route " + route + " to be alive");
    
    getAddressManager(route.getLastHop(), false).markAlive(route);
  }
  
  protected int proximity(SourceRoute route) {
    return getAddressManager(route.getLastHop(), false).proximity();    
  }
  
  protected int rto(SourceRoute route) {
    return getAddressManager(route.getLastHop(), false).rto();    
  }
  
  /**
   * This method should be called when a known route is declared
   * suspected.
   *
   * @param route The now-live route
   */
  protected void markSuspected(SourceRoute route) {
    if (logger.level <= Logger.FINE) logger.log( "(SSRM) Found route " + route + " to be suspected");
    
    getAddressManager(route.getLastHop(), false).markSuspected(route);
  }
  
  /**
   * This method should be called when a known route has its proximity updated
   *
   * @param route The route
   * @param proximity The proximity
   */
  protected synchronized void markProximity(SourceRoute route, int proximity) {
    getAddressManager(route.getLastHop(), false).markProximity(route, proximity);
  }
  
  /**
  * Reroutes the given message. If this node is alive, send() is called. If
   * this node is not alive and the message is a route message, it is rerouted.
   * Otherwise, the message is dropped.
   *
   * @param m The message
   * @param address The address of the remote node
   */
//  protected void reroutee(EpochInetSocketAddress address, Message m) {
//    if (getLiveness(address) == SocketNodeHandle.LIVENESS_ALIVE) {
//      if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to resend message " + m + " to alive address " + address);
//      send(address, m);
//    } else {
//      if (m instanceof RouteMessage) {
//        if (((RouteMessage) m).getOptions().multipleHopsAllowed()) {
//          if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to reroute route message " + m);
//          ((RouteMessage) m).nextHop = null;
//          // kick it back to pastry
//          spn.receiveMessage(m);
//        } else if (getLiveness(address) <= SocketNodeHandle.LIVENESS_SUSPECTED) {
//          // it's required to go to this address only
//          send(address, m);
//        } else {
//          // this address is dead, and the routemessage is not allowed to go anywhere else
//          if (logger.level <= Logger.WARNING) logger.log("(SSRM) Dropping message " + m + " because next hop "+address+" is dead!");          
//        }        
//      } else {
//        if (logger.level <= Logger.WARNING) logger.log("(SSRM) Dropping message " + m + " because next hop "+address+" is dead!");
//      }
//    }  
//  }
  
   /**
    * Reroutes the given message. If this node is alive, send() is called. If
    * this node is not alive and the message is a route message, it is rerouted.
    * Otherwise, the message is dropped.
    * 
    * Can be called when a socket is closed, if for example a different source route is found.  This is 
    * how non-routemessages may be called here
    * 
    * For suspected/dead, it will get called with all RouteMessages
    *
    * @param m The message
    * @param address The address of the remote node
    */
  protected void reroute(EpochInetSocketAddress address, SocketBuffer m) {
    if (m.discard) {
      if (logger.level <= Logger.FINE) logger.log( "(SSRM) Dropping garbage in resend message " + m + " address " + address+" with liveness "+getLiveness(address));
      return;
    }
    
    switch (getLiveness(address)) {
      case SocketNodeHandle.LIVENESS_ALIVE:
        if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to resend message " + m + " to alive address " + address);
        send(address, m);
        return;
      case SocketNodeHandle.LIVENESS_SUSPECTED:
        if (m.isRouteMessage()) {
          if (m.getOptions().multipleHopsAllowed() && m.getOptions().rerouteIfSuspected()) {
            // kick it back to pastry
            if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to reroute route message " + m + " because suspected address " + address);            
            RouteMessage rm = m.getRouteMessage();            
            rm.nextHop = null; 
            spn.receiveMessage(rm);
            return;
          }
        } else {
          if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to resend message " + m + " to alive address " + address);
          send(address, m); 
          return;
        }
      case SocketNodeHandle.LIVENESS_DEAD:        
      case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
        if (m.isRouteMessage()) {
          if (m.getOptions().multipleHopsAllowed()) {
            if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to reroute route message " + m + " because dead address " + address);
            RouteMessage rm = m.getRouteMessage();
            rm.nextHop = null; 
            spn.receiveMessage(rm);
            return;
          }
        }        
    }
    if (logger.level <= Logger.INFO) logger.log("(SSRM) Dropping message " + m + " because next hop "+address+" is dead!");    
  }
  
  /**
   * Internal method which returns a list of all possible routes 
   * to a given address. Currently, this method simply sees if any of the
   * leafset members are able to reach the node.  
   *
   * @param address The foreign address
   * @return All possible source routes to the destination
   */
  /*
  protected SourceRoute[] getAllRoutes(EpochInetSocketAddress destination) {
    NodeSet nodes = spn.getLeafSet().neighborSet(NUM_SOURCE_ROUTE_ATTEMPTS); // includes
    nodes.randomize(spn.getEnvironment().getRandomSource());
    Vector result = new Vector();
    result.add(SourceRoute.build(destination));
    
    for (int i=0; i<nodes.size(); i++) {
      SocketNodeHandle handle = (SocketNodeHandle) nodes.get(i);
      
      if ((! handle.isLocal()) && (! handle.getEpochAddress().equals(destination)) &&
          (getBestRoute(handle.getEpochAddress()) != null) && 
          (! getBestRoute(handle.getEpochAddress()).goesThrough(destination))) 
        result.add(getBestRoute(handle.getEpochAddress()).append(destination));
    }
    
    return (SourceRoute[]) result.toArray(new SourceRoute[0]);
  }
*/  
  protected SourceRoute[] getAllRoutes(EpochInetSocketAddress destination) {
    
    Vector result = new Vector(NUM_SOURCE_ROUTE_ATTEMPTS);
    walkLeafSet(destination, NUM_SOURCE_ROUTE_ATTEMPTS, result);
    
    LinkedList ll = new LinkedList();
    
    // randomize
    while(result.size() > 0) { 
      ll.add(result.remove(spn.getEnvironment().getRandomSource().nextInt(result.size())));
    }
    
    // Note: need the direct route to try to recover after temporary outage
    ll.addFirst(SourceRoute.build(destination));
    if (logger.level <= Logger.FINER) {
      String s = "";
      Iterator i = ll.iterator();
      while(i.hasNext()) {
        s+=" "+i.next(); 
      }
      logger.log("getAllRoutes("+destination+"):"+ll.size()+","+spn.getLeafSet().getUniqueCount()+"/"+NUM_SOURCE_ROUTE_ATTEMPTS+s);
    } else if (logger.level <= Logger.FINE) 
      logger.log("getAllRoutes("+destination+"):"+ll.size()+","+spn.getLeafSet().getUniqueCount()+"/"+NUM_SOURCE_ROUTE_ATTEMPTS);
    return (SourceRoute[]) ll.toArray(new SourceRoute[0]);
  }
  
  /**
   * Walks leafset from center out, making sure to not add the destination, 
   * but get the number requested.
   * @param destination
   */
  private Collection walkLeafSet(EpochInetSocketAddress destination, int numRequested, Collection result) {
    LeafSet leafset = spn.getLeafSet();
    for (int i = 1; i < leafset.maxSize()/2; i++) {      
      SocketNodeHandle snh = (SocketNodeHandle)leafset.get(-i);
      if (addMember(snh, destination, result)) {
        numRequested--;
        if (numRequested == 0) return result;
      }      
      snh = (SocketNodeHandle)leafset.get(i);
      if (addMember(snh, destination, result)) {
        numRequested--;
        if (numRequested == 0) return result;
      }
    }
    return result;
  }
  
  /**
   * This is a helper method for getAllRoutes()
   * 
   * Return true if the member should be added.  
   * 
   * @param handle
   * @param destination
   * @param result
   * @return
   */
  private boolean addMember(SocketNodeHandle handle, EpochInetSocketAddress destination, Collection result) {
    if ((handle != null) && 
        (! handle.isLocal()) && 
        (! handle.getEpochAddress().equals(destination)) &&
        (getBestRoute(handle.getEpochAddress()) != null) && 
        (! getBestRoute(handle.getEpochAddress()).goesThrough(destination))) {
      result.add(getBestRoute(handle.getEpochAddress()).append(destination)); 
      return true;
    }
    return false;
  }
  
  /**
   * Internal method which returns the best known route to the given destination
   *
   * @param address The address
   */
  protected SourceRoute getBestRoute(EpochInetSocketAddress address) {
    AddressManager am = getAddressManager(address);
    
    if ((am == null) || (am.getLiveness() == SocketNodeHandle.LIVENESS_DEAD) ||
        (am.getLiveness() == SocketNodeHandle.LIVENESS_DEAD_FOREVER))
      return null;
    else
      return am.best;
  }
    
  /**
   * Internal class which is tasked with maintaining the status of a single
   * remote address.  This class is in charge of all source routes to that address,
   * as well as declaring liveness/death of this address
   */
  protected class AddressManager {
    
    // the remote address of this manager
    protected SocketNodeHandle address;
    
    /**
     * the current best route to this remote address
     * 
     * if best == null, we are already in a CheckDead, which means
     * we are searching for a path
     */
    protected SourceRoute best;
    
    /** 
     *  the queue of messages waiting for a route
     *  
     *  of SocketBuffer
     */
    protected LinkedList queue;
    
    // the queue of appSockets waiting for a connection
    protected LinkedList pendingAppSockets;
    
    // the list of known route -> managers for this manager
    protected HashMap routes;
    
    // the current liveness of this address
    protected int liveness;
    
    // the last time this address was pinged
    protected long updated;
    
    /**
     * Constructor, given an address and whether or not it should attempt to
     * find the best route
     *
     * @param address The address
     * @param search Whether or not the manager should try and find a route
     */
    public AddressManager(SocketNodeHandle address, boolean search) {
      this.address = address;
      this.queue = new LinkedList();
      this.pendingAppSockets = new LinkedList();
      this.routes = new HashMap();
      this.liveness = SocketNodeHandle.LIVENESS_SUSPECTED;
      this.updated = 0L;
      
      if (logger.level <= Logger.FINE) logger.log( "(SSRM) ADDRESS MANAGER CREATED AT " + localAddress + " FOR " + address);
      
      if (search) {
        getRouteManager(SourceRoute.build(address.eaddress)).checkLiveness();
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      }
      if ((address.getNodeId() != null) && address.isLocal()) {
        best = SourceRoute.build(address.eaddress);
      }
    }
    
    /**
     * Method which returns the route manager for the given route
     *
     * @param route The route
     * @return THe manager
     * @throws IllegalArgumentException if route is null
     */
    protected SourceRouteManager getRouteManager(SourceRoute route) {
      if (route == null) throw new IllegalArgumentException("route is null in "+toString());
      
      SourceRouteManager result = (SourceRouteManager) routes.get(route);
      
      if (result == null) {
        result = new SourceRouteManager(route);
        routes.put(route, result);
      }
      
      return result;
    }
    
    /**
     * Method which returns the last cached proximity value for the given address.
     * If there is no cached value, then DEFAULT_PROXIMITY is returned.
     *
     * @param address The address to return the value for
     * @return The ping value to the remote address
     */
    public int proximity() {
      if (best == null)
        return SocketNodeHandle.DEFAULT_PROXIMITY;
      else
        return getRouteManager(best).proximity();
    }  
    
    public int rto() {
      if (best == null)
        return DEFAULT_RTO;
      else
        return getRouteManager(best).rto();
    }  
    
    /**
     * Method which returns the last cached liveness value for the given address.
     * If there is no cached value, then true is returned.
     *
     * @param address The address to return the value for
     * @return The Alive value
     */
    public int getLiveness() {
      return liveness;
    } 
    
    /**
     * This method should be called when a known route is declared
     * alive.
     *
     * @param route The now-live route
     */
    protected synchronized void markAlive(SourceRoute route) {
      getRouteManager(route).markAlive();
      
      // first, we check and see if we have no best route (this can happen if the best just died)
      if (best == null) {
        if (logger.level <= Logger.FINE) logger.log( "(SSRM) No previous best route existed to " + address + " route " + route + " is now the best");
        best = route;        
      }
      
      // now, we check if the route is (a) shorter, or (b) the same length but quicker
      // if se, we switch our best route to that one
      if ((best.getNumHops() > route.getNumHops()) || 
          ((best.getNumHops() == route.getNumHops()) &&
           (getRouteManager(best).proximity() > getRouteManager(route).proximity()))) {
        if (logger.level <= Logger.FINE) logger.log( "(SSRM) Route " + route + " is better than previous best route " + best + " - replacing");
            
        best = route;  
        
        if (address != null) {
          address.update(SocketNodeHandle.PROXIMITY_CHANGED); 
        }
      }
      
      // finally, mark this address as alive
      setAlive();
    }
    
    /**
     * This method should be called when a known route is declared
     * suspected.
     *
     * @param route The now-suspected route
     */
    protected synchronized void markSuspected(SourceRoute route) {      
      getRouteManager(route).markSuspected();
      
      // mark this address as suspected, if this is currently the best route
      if (((best == null) || (best.equals(route))) && // because we set the best == null when we didn't have a route
          (liveness < SocketNodeHandle.LIVENESS_DEAD)) // don't promote the node
          setSuspected();
    }
    
    /**
     * This method should be called when a known route is declared
     * dead.
     *
     * @param route The now-dead route
     */
    protected synchronized void markDead(SourceRoute route) {
      getRouteManager(route).markDead();
      
      // if we're already dead, who cares
      if (liveness >= SocketNodeHandle.LIVENESS_DEAD)
        return;
      
      // if this route was the best, or if we have no best, we need to
      // look for alternate routes - if all alternates are now dead,
      // we mark ourselves as dead
      if ((best == null) || (route.equals(best))) {
        best = null;

        SourceRoute[] routes = getAllRoutes(route.getLastHop());
        boolean found = false;

        for (int i=0; i<routes.length; i++) 
          if (getRouteManager(routes[i]).checkLiveness()) {
//            logger.log(this+" Found "+routes[i]);
            found = true;
          }
        
        if (! found) 
          setDead();
      } 
    }    
    
    /**
     * This method should be called when a known node is declared dead - this is
     * ONLY called when a new epoch of that node is detected.  Note that this method
     * is silent - no checks are done.  Caveat emptor.
     *
     * @param address The now-dead address
     */
    protected synchronized void markDeadForever() {      
      this.best = null;            
      setDeadForever();
    }
    
    /**
     * This method should be called when a known route has its proximity updated
     *
     * @param route The route
     * @param proximity The proximity
     */
    protected synchronized void markProximity(SourceRoute route, int proximity) {
      getRouteManager(route).markAlive();
      getRouteManager(route).markProximity(proximity);
      
      // first, we check and see if we have no best route (this can happen if the best just died)
      if (best == null) {
        if (logger.level <= Logger.FINE) logger.log( "(SSRM) No previous best route existed to " + address + " route " + route + " is now the best");
        best = route;        
      }
      
      setAlive();
        
      // next, we update everyone if this is the active route
      if (route.equals(best)) {
        if (address != null) address.update(SocketNodeHandle.PROXIMITY_CHANGED);        
      }
    }

    /**
     * Method which enqueues a message to this address
     *
     * @param message The message to send
     */
    public synchronized void send(SocketBuffer message) {
      // if we're dead, we go ahead and just checkDead on the direct route
      if (liveness == SocketNodeHandle.LIVENESS_DEAD) {
        getRouteManager(SourceRoute.build(address.eaddress)).checkLiveness();
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      }
      
      // and in any case, we either send if we have a best route or add the message
      // to the queue
      if (best == null) {
        queue.addLast(message);
        hardLinks.add(this);
      } else if (! getRouteManager(best).isOpen()) {
        queue.addLast(message);
        hardLinks.add(this);
        
        getRouteManager(best).checkLiveness();
        this.best = null;
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      } else {
        getRouteManager(best).send(message);
      }
    }
    
    /**
     * Method which opens an app socket to this address
     *
     * @param message The message to send
     */
    public synchronized void connect(int appAddress, AppSocketReceiver receiver, int timeout) {
      // if we're dead, we go ahead and just checkDead on the direct route
      if (liveness == SocketNodeHandle.LIVENESS_DEAD) {
        getRouteManager(SourceRoute.build(address.eaddress)).checkLiveness();
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      }
      
      // and in any case, we either send if we have a best route or add the message
      // to the queue
      
      if (best == null) {
        pendingAppSockets.addLast(new PendingAppSocket(appAddress, receiver));
        hardLinks.add(this);
//      } else if (! getRouteManager(best).isOpen()) {
//        pendingAppSockets.addLast(new PendingAppSocket(appAddress, receiver));
//        hardLinks.add(this);
//        
//        getRouteManager(best).checkLiveness();
//        this.best = null;
//        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      } else {
        getRouteManager(best).connect(appAddress, receiver, timeout);
      }
    }
    
    /**
     * Method which suggests a ping to the remote node.
     */
    public void ping() {
      if (spn.getEnvironment().getTimeSource().currentTimeMillis() - updated > PING_THROTTLE) {
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
        
        switch (liveness) {
          case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
            return;
          case SocketNodeHandle.LIVENESS_DEAD:
            if (logger.level <= Logger.FINE) logger.log( "(SSRM) PING: PINGING DEAD ADDRESS " + address + " - JUST IN CASE, NO HARM ANYWAY");
            getRouteManager(SourceRoute.build(address.eaddress)).ping();
            break;
          default:
            if (best != null) {
              getRouteManager(best).ping();
              
              // check to see if the direct route is available
              if (! best.isDirect()) 
                getRouteManager(SourceRoute.build(address.eaddress)).ping();
            }
            
            break;
        }
      }
    }  
    
    /**
     * Method which suggests a ping to the remote node.
     */
    public void checkLiveness() {
      this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          return;
        case SocketNodeHandle.LIVENESS_DEAD:
          if (logger.level <= Logger.FINE) logger.log( "(SSRM) CHECKLIVENESS: CHECKING DEAD ON DEAD ADDRESS " + address + " - JUST IN CASE, NO HARM ANYWAY");
          getRouteManager(SourceRoute.build(address.eaddress)).checkLiveness();
          break;
        default:
          if (best != null) {
            getRouteManager(best).checkLiveness();
            
            // check to see if the direct route is available
            if (! best.isDirect()) 
              getRouteManager(SourceRoute.build(address.eaddress)).checkLiveness();
          }
          
          break;
      }  
    }   
    
    /**
     * Internal method which marks this address as being alive.  If we were dead before, it
     * sends an update out to the observers.
     * 
     * best must be non-null
     * 
     * @throws IllegalStateException if best is null.
     */
    protected void setAlive() {
      if (best == null) throw new IllegalStateException("best is null in "+toString());
      
      // we can now send any pending messages
      while (!queue.isEmpty())
        getRouteManager(best).send((SocketBuffer) queue.removeFirst());          
      // we can now send any pending messages
      while (!pendingAppSockets.isEmpty()) {
        PendingAppSocket pas = (PendingAppSocket)pendingAppSockets.removeFirst();
        getRouteManager(best).connect(pas.appAddress, pas.receiver, 0);          
      }
      if (queue.isEmpty() && pendingAppSockets.isEmpty()) hardLinks.remove(this);      
      
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD:
          liveness = SocketNodeHandle.LIVENESS_ALIVE;
          if (address != null) address.update(SocketNodeHandle.DECLARED_LIVE);
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be alive again.");
          break;
        case SocketNodeHandle.LIVENESS_SUSPECTED:
          liveness = SocketNodeHandle.LIVENESS_ALIVE;
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be unsuspected.");
          break;
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          if (logger.level <= Logger.WARNING) logger.log( "ERROR: Found dead-forever handle to " + address + " to be alive again!");
          break;
      }
    }
    
    /**
     * Internal method which marks this address as being suspected.
     */
    protected void setSuspected() {
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_ALIVE:
          liveness = SocketNodeHandle.LIVENESS_SUSPECTED;
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + spn.getEnvironment().getTimeSource().currentTimeMillis() + " " + localAddress + " Found address " + address + " to be suspected.");
          break;
        case SocketNodeHandle.LIVENESS_DEAD:
          if (logger.level <= Logger.WARNING) logger.log( "ERROR: Found node handle " + address + " to be suspected from dead - should not happen!");
          break;
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          if (logger.level <= Logger.WARNING) logger.log( "ERROR: Found node handle " + address + " to be suspected from dead forever - should never ever happen!");
          break;
      }
      
      // and finally we can now reroute any route messages
      Object[] array = queue.toArray();
      
      for (int i=0; i<array.length; i++) {
        SocketBuffer sb = (SocketBuffer)array[i];
        if (sb.isRouteMessage()) {
          if (sb.getOptions().multipleHopsAllowed() && sb.getOptions().rerouteIfSuspected()) {
            //if (logger.level <= Logger.FINE) logger.log( "REROUTE: Rerouting message " + sb + " due to suspected next hop " + address);
            reroute(address.eaddress, sb);
            queue.remove(sb);
          }
        }
      }
      if (queue.isEmpty() && pendingAppSockets.isEmpty()) hardLinks.remove(this);
    }
    
    /**
     * Internal method which marks this address as being dead.  If we were alive or suspected before, it
     * sends an update out to the observers.
     */
    protected void setDead() {
//      logger.log(this+" marking as dead.");
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD:
          return;
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          if (logger.level <= Logger.WARNING) logger.log( "ERROR: Found node handle " + address + " to be dead from dead forever - should not happen!");
          break;
        default:
          this.best = null;
          this.liveness = SocketNodeHandle.LIVENESS_DEAD;
          if (address != null) address.update(SocketNodeHandle.DECLARED_DEAD);   
          if (address.eaddress != null) manager.declaredDead(address.eaddress);
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be dead.");
          break;
      }

      purgeQueue();
    }

    public String toString() {
      return "AM"+this.address; 
    }
    
    /**
     * Internal method which marks this address as being dead.  If we were alive or suspected before, it
     * sends an update out to the observers.
     */
    protected void setDeadForever() {
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          return;
        case SocketNodeHandle.LIVENESS_DEAD:
          this.liveness = SocketNodeHandle.LIVENESS_DEAD_FOREVER;
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be dead forever.");
          break;
        default:
          this.best = null;
          this.liveness = SocketNodeHandle.LIVENESS_DEAD_FOREVER;
          if (address != null) address.update(SocketNodeHandle.DECLARED_DEAD);        
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be dead forever.");
          break;
      }
      purgeQueue();
    }
    
    protected void purgeQueue() {
      // and finally we can now send any pending messages
      while (!queue.isEmpty())
        reroute(address.eaddress, (SocketBuffer) queue.removeFirst());
      while (!pendingAppSockets.isEmpty()) {
        PendingAppSocket pas = (PendingAppSocket)pendingAppSockets.removeFirst();
        pas.receiver.receiveException(null, new NodeIsDeadException());
      }
      hardLinks.remove(this);      
    }
    
    /**
     * Internal class which is charges with managing the remote connection via
     * a specific route
     */
    public class SourceRouteManager {
      
      /**
       * Retransmission Time Out
       */
      int RTO = DEFAULT_RTO; 

      /**
       * Average RTT
       */
      double RTT = 0;

      /**
       * Standard deviation RTT
       */
      double standardD = RTO/4.0;  // RFC1122 recommends choose value to target RTO = 3 seconds
      
      
      // the remote route of this manager
      protected SourceRoute route;
      
      // the current liveness of this route
      protected int liveness;
      
      // the current best-known proximity of this route
      protected int proximity;
      protected long proximityTimeout; // when the proximity is no longer valid
      
      // the last time the liveness information was updated
      protected long updated;
      
      // whether or not a check dead is currently being carried out on this route
      protected boolean pending;
      
      /**
       * Constructor - builds a route manager given the route
       *
       * @param route The route
       */
      public SourceRouteManager(SourceRoute route) {
        if (route == null) throw new IllegalArgumentException("route is null");
        this.route = route;
        this.liveness = SocketNodeHandle.LIVENESS_SUSPECTED;

        proximity = SocketNodeHandle.DEFAULT_PROXIMITY;
        proximityTimeout = time.currentTimeMillis()+PROX_TIMEOUT;
        
        this.pending = false;
        this.updated = 0L;
      }
      
      public int rto() {
        return (int)RTO; 
      }
      
      /**
       * Method which returns the last cached proximity value for the given address.
       * If there is no cached value, then DEFAULT_PROXIMITY is returned.
       *
       * @param address The address to return the value for
       * @return The ping value to the remote address
       */
      public int proximity() {
        long now = time.currentTimeMillis();
        // prevent from changing too much
        if (proximityTimeout > now) return proximity;

        proximity = (int)RTT;
        proximityTimeout = now+PROX_TIMEOUT;
        
        // TODO, schedule notification
        
        return proximity;
      }
       
      /**
       * This method should be called when this route is declared
       * alive.
       */
      protected void markAlive() {
        this.liveness = SocketNodeHandle.LIVENESS_ALIVE;
        this.pending = false;
      }
      
      /**
       * This method should be called when this route is declared
       * suspected.
       */
      protected void markSuspected() {      
        this.liveness = SocketNodeHandle.LIVENESS_SUSPECTED;
      }    
      
      /**
       * This method should be called when this route is declared
       * dead.
       */
      protected void markDead() {
        this.liveness = SocketNodeHandle.LIVENESS_DEAD;
        this.pending = false;
      }
      
      /**
       * This method should be called when this route has its proximity updated
       *
       * @param proximity The proximity
       */
      protected void markProximity(int proximity) {
        if (proximity < 0) throw new IllegalArgumentException("proximity must be >= 0, was:"+proximity);
        updateRTO(proximity);
        if (this.proximity > proximity) {
          proximityTimeout = time.currentTimeMillis();
          this.proximity = proximity;
        }
        // TODO: Schedule notification
      }

      /**
       * Adds a new round trip time datapoint to our RTT estimate, and 
       * updates RTO and standardD accordingly.
       * 
       * @param m new RTT
       */
      private void updateRTO(long m) {
        // rfc 1122
        double err = m-RTT;
        double absErr = err;
        if (absErr < 0) {
          absErr *= -1;
        }
        RTT = RTT+gainG*err;
        standardD = standardD + gainH*(absErr-standardD);
        RTO = (int)(RTT+(4.0*standardD));
        if (RTO > RTO_UBOUND) {
          RTO = RTO_UBOUND;
        }
        if (RTO < RTO_LBOUND) {
          RTO = RTO_LBOUND;
        }
//          System.out.println("CM.updateRTO() RTO = "+RTO+" standardD = "+standardD+" suspected in "+getTimeToSuspected(RTO)+" faulty in "+getTimeToFaulty(RTO));
      }      
      
      /**
       * Method which checks to see this route is dead.  If this address has
       * been checked within the past CHECK_DEAD_THROTTLE millis, then
       * this method does not actually do a check.
       *
       * @return Whether or not a check will actually be carried out
       */
      protected boolean checkLiveness() {
        if (this.pending)
          return true;
        
        if ((this.liveness < SocketNodeHandle.LIVENESS_DEAD) || 
            (this.updated < spn.getEnvironment().getTimeSource().currentTimeMillis() - CHECK_DEAD_THROTTLE)) {
          this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
          this.pending = true;
          manager.checkLiveness(route);
          return true;
        }
        
        return false;
      }
      
      /**
       * Method which enqueues a message along this route
       *
       * @param message The message to send
       */
      public synchronized void send(SocketBuffer message) {
        manager.send(route, message, AddressManager.this);
      }
      
      /**
       * Method which opens a socket along this route
       *
       * @param message The message to send
       */
      public synchronized void connect(int appId, AppSocketReceiver receiver, int timeout) {
        manager.connect(route, appId, receiver, timeout);
      }
      
      /**
       * Method which suggests a ping to the remote node.
       */
      public void ping() {
        manager.ping(route);
      }
      
      /**
       * Returns whether or not a socket is currently open to this 
       * route
       *
       * @return Whether or not a socket is currently open to this route
       */
      public boolean isOpen() {
        return manager.isOpen(route);
      }
      
      public String toString() {
        return "SRM"+route;
      }
    }
  }
}
