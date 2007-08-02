package org.mpisws.p2p.transport.proximity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.liveness.PingListener;
import org.mpisws.p2p.transport.liveness.Pinger;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.time.TimeSource;

public class MinRTTProximityProvider<Identifier> implements ProximityProvider<Identifier>, PingListener<Identifier> {
  /**
   * millis for the timeout
   * 
   * The idea is that we don't want this parameter to change too fast, 
   * so this is the timeout for it to increase, you could set this to infinity, 
   * but that may be bad because it doesn't account for intermediate link failures
   */
   public int PROX_TIMEOUT;// = 60*60*1000;

   /**
    * Holds only pending DeadCheckers
    */
   Map<Identifier, EntityManager> managers;

   Pinger<Identifier> tl;
   
   Logger logger;
   
   TimeSource time;
   
   int pingThrottle = 5000; // TODO: Make configurable
   
  public MinRTTProximityProvider(Pinger<Identifier> tl, Environment env) {
    this.tl = tl;
    this.logger = env.getLogManager().getLogger(MinRTTProximityProvider.class, null);
    this.time = env.getTimeSource();
    tl.addPingListener(this);
    this.managers = new HashMap<Identifier, EntityManager>();
  }
  
  public int proximity(Identifier i) {
    EntityManager manager = getManager(i);
    int ret = manager.proximity;
    if (ret == DEFAULT_PROXIMITY) {
      manager.ping();
    }
    return ret;
  }
  
  public void pingResponse(Identifier i, int rtt, Map<String, Integer> options) {
    getManager(i).markProximity(rtt, options);
  }

  public void pingReceived(Identifier i, Map<String, Integer> options) {

  }
  
  public EntityManager getManager(Identifier i) {
    synchronized(managers) {
      EntityManager manager = managers.get(i);
      if (manager == null) {
        manager = new EntityManager(i);
        managers.put(i,manager);
      }
      return manager;
    }
  }

  /**
   * Internal class which is charges with managing the remote connection via
   * a specific route
   * 
   */
  public class EntityManager {
    
    // the remote route of this manager
    protected Identifier identifier;
    
    // the current best-known proximity of this route
    protected int proximity;
    
    protected long lastPingTime = 0;
    /**
     * Constructor - builds a route manager given the route
     *
     * @param route The route
     */
    public EntityManager(Identifier route) {
      if (route == null) throw new IllegalArgumentException("route is null");
      this.identifier = route;
      proximity = DEFAULT_PROXIMITY;
    }
    
    public void ping() {
      long now = time.currentTimeMillis();
      if ((now - lastPingTime) < pingThrottle) {
        return;
      }          
      lastPingTime = now;
      tl.ping(identifier, null);
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
     * This method should be called when this route has its proximity updated
     *
     * @param proximity The proximity
     */
    protected void markProximity(int proximity, Map<String, Integer> options) {
      if (proximity < 0) throw new IllegalArgumentException("proximity must be >= 0, was:"+proximity);
      if (logger.level <= Logger.FINER) logger.log(this+".markProximity("+proximity+")");
      if (this.proximity > proximity) {
        if (logger.level <= Logger.FINE) logger.log(this+" updating proximity to "+proximity);
        this.proximity = proximity;
        notifyProximityListeners(identifier, proximity, options);
      }
    }

    public String toString() {
      return identifier.toString();
    }
  }

  Collection<ProximityListener<Identifier>> listeners = new ArrayList<ProximityListener<Identifier>>();
  public void addProximityListener(ProximityListener<Identifier> listener) {
    synchronized(listeners) {
      listeners.add(listener);
    }
  }

  public boolean removeProximityListener(ProximityListener<Identifier> listener) {
    synchronized(listeners) {
      return listeners.remove(listener);
    }
  }
  
  public void notifyProximityListeners(Identifier i, int prox, Map<String, Integer> options) {
    Collection<ProximityListener<Identifier>> temp;
    synchronized(listeners) {
      temp = new ArrayList<ProximityListener<Identifier>>(listeners);
    }
    for (ProximityListener<Identifier> p : temp) {
      p.proximityChanged(i, prox, options);
    }
  }
}

