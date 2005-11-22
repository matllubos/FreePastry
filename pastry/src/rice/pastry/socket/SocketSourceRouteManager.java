package rice.pastry.socket;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.environment.params.Parameters;
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
  
  // the minimum amount of time between check dead checks on dead routes
  public long CHECK_DEAD_THROTTLE;
  
  // the minimum amount of time between pings
  public long PING_THROTTLE;
  
  // the local pastry node
  private SocketPastryNode spn;
  
  // the list of best routes
  private Hashtable managers;
  
  // the socket manager below this manager
  private SocketCollectionManager manager;
  
  // the node hanlde pool
  private SocketNodeHandlePool pool;
  
  // the address of this local node
  private EpochInetSocketAddress localAddress;
  
  private Logger logger;
  /**
   * Constructor
   *
   * @param node The local node
   * @param pool The node hanlde pool
   * @param bindAddress The address which the node should bind to
   * @param proxyAddress The address which the node should advertise as it's address
   */
  protected SocketSourceRouteManager(SocketPastryNode node, SocketNodeHandlePool pool, EpochInetSocketAddress bindAddress, EpochInetSocketAddress proxyAddress) {
    this.spn = node;
    Parameters p = node.getEnvironment().getParameters();
    CHECK_DEAD_THROTTLE = p.getLong("pastry_socket_srm_check_dead_throttle");
    PING_THROTTLE = p.getLong("pastry_socket_srm_ping_throttle");
    
    this.logger = node.getEnvironment().getLogManager().getLogger(SocketSourceRouteManager.class, null);
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
  public void destroy() throws IOException {
    manager.destroy();
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
    if (logger.level <= Logger.FINE) logger.log( "(SSRM) Found route " + route + " to be dead");
    
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
    if (logger.level <= Logger.FINE) logger.log( "(SSRM) Found route " + route + " to be alive");
    
    getAddressManager(route.getLastHop(), false).markAlive(route);
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
  protected void reroute(EpochInetSocketAddress address, Message m) {
    if (getLiveness(address) == SocketNodeHandle.LIVENESS_ALIVE) {
      if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to resend message " + m + " to alive address " + address);
      send(address, m);
    } else {
      if (m instanceof RouteMessage) {
        if (((RouteMessage) m).getOptions().multipleHopsAllowed()) {
          if (logger.level <= Logger.INFO) logger.log( "(SSRM) Attempting to reroute route message " + m);
          ((RouteMessage) m).nextHop = null;
          spn.receiveMessage(m);
        }
      } else {
        if (logger.level <= Logger.WARNING) logger.log("(SSRM) Dropping message " + m + " because next hop is dead!");
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
   * Internal class which is tasked with maintaining the status of a single
   * remote address.  This class is in charge of all source routes to that address,
   * as well as declaring liveness/death of this address
   */
  protected class AddressManager {
    
    // the remote address of this manager
    protected EpochInetSocketAddress address;
    
    /**
     * the current best route to this remote address
     * 
     * if best == null, we are already in a CheckDead, which means
     * we are searching for a path
     */
    protected SourceRoute best;
    
    // the queue of messages waiting for a route
    protected Vector queue;
    
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
    public AddressManager(EpochInetSocketAddress address, boolean search) {
      this.address = address;
      this.queue = new Vector();
      this.routes = new HashMap();
      this.liveness = SocketNodeHandle.LIVENESS_SUSPECTED;
      this.updated = 0L;
      
      if (logger.level <= Logger.FINE) logger.log( "(SSRM) ADDRESS MANAGER CREATED AT " + localAddress + " FOR " + address);
      
      if (search) {
        getRouteManager(SourceRoute.build(address)).checkLiveness();
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
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
        pool.update(address, SocketNodeHandle.PROXIMITY_CHANGED);
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
          if (getRouteManager(routes[i]).checkLiveness()) 
            found = true;
        
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
      if (route.equals(best))
        pool.update(address, SocketNodeHandle.PROXIMITY_CHANGED);
    }

    /**
     * Method which enqueues a message to this address
     *
     * @param message The message to send
     */
    public synchronized void send(Message message) {
      // if we're dead, we go ahead and just checkDead on the direct route
      if (liveness == SocketNodeHandle.LIVENESS_DEAD) {
        getRouteManager(SourceRoute.build(address)).checkLiveness();
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      }
      
      // and in any case, we either send if we have a best route or add the message
      // to the queue
      if (best == null) {
        queue.add(message);
      } else if (! getRouteManager(best).isOpen()) {
        queue.add(message);
        
        getRouteManager(best).checkLiveness();
        this.best = null;
        this.updated = spn.getEnvironment().getTimeSource().currentTimeMillis();
      } else {
        getRouteManager(best).send(message);
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
            getRouteManager(SourceRoute.build(address)).ping();
            break;
          default:
            if (best != null) {
              getRouteManager(best).ping();
              
              // check to see if the direct route is available
              if (! best.isDirect()) 
                getRouteManager(SourceRoute.build(address)).ping();
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
          getRouteManager(SourceRoute.build(address)).checkLiveness();
          break;
        default:
          if (best != null) {
            getRouteManager(best).checkLiveness();
            
            // check to see if the direct route is available
            if (! best.isDirect()) 
              getRouteManager(SourceRoute.build(address)).checkLiveness();
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
      while (queue.size() > 0)
        getRouteManager(best).send((Message) queue.remove(0));    
      
      switch (liveness) {
        case SocketNodeHandle.LIVENESS_DEAD:
          liveness = SocketNodeHandle.LIVENESS_ALIVE;
          pool.update(address, SocketNodeHandle.DECLARED_LIVE);
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
        if (array[i] instanceof RouteMessage) {
          if (logger.level <= Logger.FINE) logger.log( "REROUTE: Rerouting message " + array[i] + " due to suspected next hop " + address);
          reroute(address, (Message) array[i]);
          queue.remove(array[i]);
        }
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
          if (logger.level <= Logger.WARNING) logger.log( "ERROR: Found node handle " + address + " to be dead from dead forever - should not happen!");
          break;
        default:
          this.best = null;
          this.liveness = SocketNodeHandle.LIVENESS_DEAD;
          pool.update(address, SocketNodeHandle.DECLARED_DEAD);   
          manager.declaredDead(address);
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be dead.");
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
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be dead forever.");
          break;
        default:
          this.best = null;
          this.liveness = SocketNodeHandle.LIVENESS_DEAD_FOREVER;
          pool.update(address, SocketNodeHandle.DECLARED_DEAD);        
          if (logger.level <= Logger.FINE) logger.log( "COUNT: " + localAddress + " Found address " + address + " to be dead forever.");
          break;
      }
      
      // and finally we can now send any pending messages
      while (queue.size() > 0)
        reroute(address, (Message) queue.remove(0));      
    }
    
    /**
     * Internal class which is charges with managing the remote connection via
     * a specific route
     */
    public class SourceRouteManager {
      
      // the remote route of this manager
      protected SourceRoute route;
      
      // the current liveness of this route
      protected int liveness;
      
      // the current best-known proximity of this route
      protected int proximity;
      
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
        this.proximity = SocketNodeHandle.DEFAULT_PROXIMITY;
        this.pending = false;
        this.updated = 0L;
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
        if (this.proximity > proximity) 
          this.proximity = proximity;
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
      public synchronized void send(Message message) {
        manager.send(route, message);
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
    }
  }
}
