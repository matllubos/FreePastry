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
  contributors may be  used to endorse or promote products derived from
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
package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.socket.messaging.*;
import rice.selector.*;

/**
 * Class which keeps track of the best routes to remote nodes.  This class
 * is also therefore in charge of declaring node death and liveness.  
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SocketSourceRouteManager {
  
  // the local pastry node
  private PastryNode spn;
  
  // the list of best routes
  private Hashtable managers;
  
  // the socket manager below this manager
  private SocketCollectionManager manager;
  
  // the node hanlde pool
  private SocketNodeHandlePool pool;
  
  // the address of this local node
  private EpochInetSocketAddress localAddress;
  
  /**
   * Constructor
   *
   * @param node The local node
   * @param pool The node hanlde pool
   * @param bindAddress The address which the node should bind to
   * @param proxyAddress The address which the node should advertise as it's address
   */
  protected SocketSourceRouteManager(PastryNode node, SocketNodeHandlePool pool, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress) {
    this.spn = node;
    this.pool = pool;
    this.managers = new Hashtable();
    this.manager = new SocketCollectionManager(node, pool, this, bindAddress, proxyAddress);
    this.localAddress = bindAddress;
  }
  
  public HashMap getBest() {
    HashMap result = new HashMap();
    
    Iterator i = managers.keySet().iterator();
    
    while (i.hasNext()) {
      Object addr = i.next();
      
      if (((AddressManager) managers.get(addr)).getLiveness() < SocketNodeHandle.LIVENESS_DEAD)
        result.put(addr, ((AddressManager) managers.get(addr)).best);
    }
    
    return result;
  }
  
  /**
   * Makes this node resign from the network.  Is designed to be used for
   * debugging and testing.
   */
  public void resign() throws IOException {
    manager.resign();
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
    AddressManager manager = (AddressManager) managers.get(address);
    
    if (manager == null) {
      manager = new AddressManager(address, search);
      managers.put(address, manager);
    }
    
    return manager;
  }
  
  /**
   * Method which sends a bootstrap message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void bootstrap(EpochInetSocketAddress address, Message message) {
    manager.bootstrap(SourceRoute.build(address), message);
  }
  
  /**
   * Method which sends a message across the wire.
   *
   * @param message The message to send
   * @param address The address to send the message to
   */
  public void send(EpochInetSocketAddress address, Message message) {
    getAddressManager(address, true).send(message);
  }
  
  /**
   * Method which suggests a ping to the remote node.
   *
   * @param address DESCRIBE THE PARAMETER
   * @param prl DESCRIBE THE PARAMETER
   */
  public void ping(EpochInetSocketAddress address) {
    AddressManager am = (AddressManager) managers.get(address);
    
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
    AddressManager am = (AddressManager) managers.get(address);
    
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
    debug("Found route " + route + " to be dead");
    
    AddressManager am = (AddressManager) managers.get(route.getLastHop());
    
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
    AddressManager am = (AddressManager) managers.get(address);
    
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
    debug("Found route " + route + " to be alive");
    
    getAddressManager(route.getLastHop(), false).markAlive(route);
  }
  
  /**
   * This method should be called when a known route is declared
   * suspected.
   *
   * @param route The now-live route
   */
  protected void markSuspected(SourceRoute route) {
    debug("Found route " + route + " to be suspected");
    
    getAddressManager(route.getLastHop(), false).markSuspected(route);
  }
  
  /**
   * This method should be called when a known route has its proximity updated
   *
   * @param route The route
   * @param proximity The proximity
   */
  protected synchronized void markProximity(SourceRoute route, int proximity) {
    AddressManager am = (AddressManager) managers.get(route.getLastHop());
    
    if (am != null)
      am.markProximity(route, proximity);
  }
  
  /**
  * Reroutes the given message. If this node is alive, send() is called. If
   * this node is not alive and the message is a route message, it is rerouted.
   * Otherwise, the message is dropped.
   *
   * @param m The message
   * @param address The address of the remote node
   */
  protected void reroute(EpochInetSocketAddress address, Message m) {
    if (getLiveness(address) == SocketNodeHandle.LIVENESS_ALIVE) {
      debug("Attempting to resend message " + m + " to alive address " + address);
      send(address, m);
    } else {
      if (m instanceof RouteMessage) {
        if (((RouteMessage) m).getOptions().multipleHopsAllowed()) {
          debug("Attempting to reroute route message " + m);
          ((RouteMessage) m).nextHop = null;
          spn.receiveMessage(m);
        }
      } else {
        System.out.println("Dropping message " + m + " because next hop is dead!");
      }
    }  
  }
    
  /**
   * Internal method which returns a list of all possible routes 
   * to a given address. Currently, this method simply sees if any of the
   * leafset members are able to reach the node.  
   *
   * @param address The foreign address
   * @return All possible source routes to the destination
   */
  protected SourceRoute[] getAllRoutes(EpochInetSocketAddress destination) {
    NodeSet nodes = spn.getLeafSet().neighborSet(Integer.MAX_VALUE);
    nodes.randomize();
    Vector result = new Vector();
    result.add(SourceRoute.build(destination));
    
    for (int i=0; i<nodes.size(); i++) {
      SocketNodeHandle handle = (SocketNodeHandle) nodes.get(i);
      
      if ((! handle.isLocal()) && (! handle.getEpochAddress().equals(destination)) &&
          (getBestRoute(handle.getEpochAddress()) != null)) 
        result.add(getBestRoute(handle.getEpochAddress()).append(destination));
    }
    
    return (SourceRoute[]) result.toArray(new SourceRoute[0]);
  }
  
  /**
   * Internal method which returns the best known route to the given destination
   *
   * @param address The address
   */
  protected SourceRoute getBestRoute(EpochInetSocketAddress address) {
    AddressManager am = (AddressManager) managers.get(address);
    
    if ((am == null) || (am.getLiveness() == SocketNodeHandle.LIVENESS_DEAD) ||
        (am.getLiveness() == SocketNodeHandle.LIVENESS_DEAD_FOREVER))
      return null;
    else
      return am.best;
  }
  
  
  /**
   * Debugging method
   *
   * @param s The string to print
   */
  private void debug(String s) {
    if (Log.ifp(8)) {
      System.out.println(spn.getNodeId() + " (SSRM): " + s);
    }
  }
  
  /**
   * Internal class which is tasked with maintaining the status of a single
   * remote address.  This class is in charge of all source routes to that address,
   * as well as declaring liveness/death of this address
   */
  protected class AddressManager {
    
    // the remote address of this manager
    protected EpochInetSocketAddress address;
    
    // the current best route to this remote address
    protected SourceRoute best;
    
    // the queue of messages waiting for a route
    protected Vector queue;
    
    // the list of known-dead routes for this manager
    protected HashSet deadRoutes;

    // the list of currently-checking routes for this manager
    protected HashSet pendingRoutes;
    
    // the current liveness of this address
    protected int liveness;
    
    // the current best-known proximity of this address
    protected int proximity;
    
    /**
     * Constructor, given an address
     */
    public AddressManager(EpochInetSocketAddress address, boolean search) {
      this.address = address;
      this.queue = new Vector();
      this.pendingRoutes = new HashSet();
      this.deadRoutes = new HashSet();
      this.liveness = SocketNodeHandle.LIVENESS_SUSPECTED;
      this.proximity = SocketNodeHandle.DEFAULT_PROXIMITY;
      
      if (SocketPastryNode.verbose) System.out.println("ADDRESS MANAGER CREATED AT " + localAddress + " FOR " + address);
      
      if (search)
        checkRoute(SourceRoute.build(address));
    }
    
    /**
     * This method should be called when a known route is declared
     * alive.
     *
     * @param route The now-live route
     */
    protected synchronized void markAlive(SourceRoute route) {
      deadRoutes.remove(route);
      pendingRoutes.remove(route);
      
      // first, we check and see if we have no best route (this can happen if the best just died)
      if (best == null) {
        debug("No previous best route existed to " + address + " route " + route + " is now the best");
        best = route;        
      }
      
      // now, we check if the route is (a) shorter, or (b) the same length but quicker
      // if se, we switch our best route to that one
      if ((best.getNumHops() > route.getNumHops()) || 
          ((best.getNumHops() == route.getNumHops()) &&
           (manager.proximity(best)) > manager.proximity(route))) {
        debug("Route " + route + " is better than previous best route " + best + " - replacing");
        best = route;  
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
      // mark this address as suspected, if this is currently the best route
      if ((best != null) && (best.equals(route)))
          setSuspected();
    }    
    
    /**
     * This method should be called when a known route is declared
     * dead.
     *
     * @param route The now-dead route
     */
    protected synchronized void markDead(SourceRoute route) {
      deadRoutes.add(route);      
      pendingRoutes.remove(route);
      
      // if we're already dead, who cares
      if ((liveness == SocketNodeHandle.LIVENESS_DEAD) || (liveness == SocketNodeHandle.LIVENESS_DEAD_FOREVER))
        return;
      
      // if this route was the best, or if we have no best, we need to
      // look for alternate routes - if all alternates are now dead,
      // we mark ourselves as dead
      if ((best == null) || (route.equals(best))) {
        best = null;

        SourceRoute[] routes = getAllRoutes(route.getLastHop());
        boolean found = false;

        for (int i=0; i<routes.length; i++) {
          if (! deadRoutes.contains(routes[i])) {
            found = true;

            if (! pendingRoutes.contains(routes[i]))
              checkRoute(routes[i]);            
          }
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
      System.out.println("MARKING ADDRESS " + address + " AS DEAD FOREVER!");
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
      if (this.proximity > proximity) {
        this.proximity = proximity;
        pool.update(address, SocketNodeHandle.PROXIMITY_CHANGED);
      }
    }
      
    /**
     * Method which enqueues a message to this address
     *
     * @param message The message to send
     */
    public synchronized void send(Message message) {
      // if we're dead, we go ahead and just checkDead on our best route
      if (liveness == SocketNodeHandle.LIVENESS_DEAD)
        manager.checkDead(SourceRoute.build(address));
      
      // and in any case, we either send if we have a best route or add the message
      // to the queue
      if (best == null) {
        queue.add(message);
        
        if (pendingRoutes.size() == 0)
          System.err.println("ERROR: Enqueueing message to " + address + " without any pending routes - very very bad!!!");
      } else if (! manager.isOpen(best)) {
        queue.add(message);
        
        System.err.println("NOTE: Found closed socket to best route " + best + " - pinging route to ensure still valid.");
        checkRoute(best);
        best = null;
      } else {
        manager.send(best, message);
      }
    }
    
    /**
     * Method which suggests a ping to the remote node.
     */
    public void ping() {
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          return;
        case SocketNodeHandle.LIVENESS_DEAD:
          System.out.println("PING: CHECKING DEAD ON DEAD ADDRESS " + address + " - JUST IN CASE, NO HARM ANYWAY");
          manager.checkDead(SourceRoute.build(address));
          break;
        default:
          if (best != null) {
            manager.ping(best);
        
            // check to see if the direct route is available
            if (! best.isDirect()) 
              manager.ping(SourceRoute.build(address));
          }
          
          break;
      }
    }  
    
    
    /**
     * Method which suggests a ping to the remote node.
     */
    public void checkLiveness() {
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          return;
        case SocketNodeHandle.LIVENESS_DEAD:
          System.out.println("CHECKLIVENESS: CHECKING DEAD ON DEAD ADDRESS " + address + " - JUST IN CASE, NO HARM ANYWAY");
          manager.checkDead(SourceRoute.build(address));
          break;
        default:
          if (best != null) {
            manager.checkLiveness(best);
            
            // check to see if the direct route is available
            if (! best.isDirect()) 
              manager.ping(SourceRoute.build(address));
          }
          
          break;
      }  
    }  
    
    /**
     * Method which returns the last cached proximity value for the given address.
     * If there is no cached value, then DEFAULT_PROXIMITY is returned.
     *
     * @param address The address to return the value for
     * @return The ping value to the remote address
     */
    public int proximity() {
      return proximity;
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
     * Method which checks to see if the given address is dead
     *
     * @param route The route to check
     */
    protected synchronized void checkRoute(SourceRoute route) {
      if (! pendingRoutes.contains(route)) {
        pendingRoutes.add(route);
        manager.checkDead(route);
      }
    }
    
    /**
     * Internal method which marks this address as being alive.  If we were dead before, it
     * sends an update out to the observers.
     */
    protected void setAlive() {
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD:
          liveness = SocketNodeHandle.LIVENESS_ALIVE;
          pool.update(address, SocketNodeHandle.DECLARED_LIVE);
          if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " " + localAddress + " Found address " + address + " to be alive again.");
          break;
        case SocketNodeHandle.LIVENESS_SUSPECTED:
          liveness = SocketNodeHandle.LIVENESS_ALIVE;
          if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " " + localAddress + " Found address " + address + " to be unsuspected.");
          break;
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          System.out.println("ERROR: Found dead-forever handle to " + address + " to be alive again!");
          break;
      }
      
      // and finally we can now send any pending messages
      while (queue.size() > 0)
        manager.send(best, (Message) queue.remove(0));    
    }
    
    /**
     * Internal method which marks this address as being suspected.
     */
    protected void setSuspected() {
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_ALIVE:
          liveness = SocketNodeHandle.LIVENESS_SUSPECTED;
          if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " " + localAddress + " Found address " + address + " to be suspected.");
          break;
        case SocketNodeHandle.LIVENESS_DEAD:
          System.out.println("ERROR: Found node handle " + address + " to be suspected from dead - should not happen!");
          break;
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          System.out.println("ERROR: Found node handle " + address + " to be suspected from dead forever - should never ever happen!");
          break;
      }
      
      // and finally we can now reroute any route messages
      Object[] array = queue.toArray();
      
      for (int i=0; i<array.length; i++) 
        if (array[i] instanceof RouteMessage) {
          System.out.println("REROUTE: Rerouting message " + array[i] + " due to suspected next hop " + address);
          reroute(address, (Message) array[i]);
          queue.remove(array[i]);
        }
    }
    
    /**
     * Internal method which marks this address as being dead.  If we were alive or suspected before, it
     * sends an update out to the observers.
     */
    protected void setDead() {
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD:
          return;
        case SocketNodeHandle.LIVENESS_DEAD_FOREVER:
          System.out.println("ERROR: Found node handle " + address + " to be dead from dead forever - should not happen!");
          break;
        default:
          this.best = null;
          this.liveness = SocketNodeHandle.LIVENESS_DEAD;
          pool.update(address, SocketNodeHandle.DECLARED_DEAD);        
          if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " " + localAddress + " Found address " + address + " to be dead.");
          break;
      }
      
      // and finally we can now send any pending messages
      while (queue.size() > 0)
        reroute(address, (Message) queue.remove(0));
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
          if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " " + localAddress + " Found address " + address + " to be dead forever.");
          break;
        default:
          this.best = null;
          this.liveness = SocketNodeHandle.LIVENESS_DEAD_FOREVER;
          pool.update(address, SocketNodeHandle.DECLARED_DEAD);        
          if (SocketPastryNode.verbose) System.out.println("COUNT: " + System.currentTimeMillis() + " " + localAddress + " Found address " + address + " to be dead forever.");
          break;
      }
    }
  }
}
