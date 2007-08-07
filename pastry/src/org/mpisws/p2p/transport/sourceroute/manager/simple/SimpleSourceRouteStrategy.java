/**
 * 
 */
package org.mpisws.p2p.transport.sourceroute.manager.simple;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.mpisws.p2p.transport.liveness.LivenessProvider;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;
import org.mpisws.p2p.transport.sourceroute.SourceRouteFactory;
import org.mpisws.p2p.transport.sourceroute.manager.SourceRouteStrategy;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.util.TimerWeakHashMap;

/**
 * This is a simple implementation of the SourceRouteStrategy.  It caches 
 * routes to destinations but relies on a NextHopeStrategy to provide 
 * new nodes that can be used to source route through.  If we already 
 * source-route to get to them, we simply prepend the route with that one.
 * 
 * @author Jeff Hoye
 *
 */
public class SimpleSourceRouteStrategy<Identifier> implements SourceRouteStrategy<Identifier> {
  /**
   * Destination -> route
   * 
   * The order of the list is from here to the end.  The last hop is always the destination.
   * The local node is implied and is not included.  Direct routes are also stored here.
   */
  TimerWeakHashMap<Identifier, SourceRoute>routes;
  NextHopStrategy<Identifier> strategy;
  Environment environment;
  Logger logger;
  LivenessProvider<SourceRoute> livenessManager;
  SourceRouteFactory<Identifier> srFactory;
  Identifier localAddress;
  
  public SimpleSourceRouteStrategy(
      Identifier localAddress,
      SourceRouteFactory<Identifier> srFactory, 
      NextHopStrategy<Identifier> strategy, 
      Environment env) {
    this.localAddress = localAddress;
    this.srFactory = srFactory;
    this.strategy = strategy;
    this.environment = env;
    this.logger = environment.getLogManager().getLogger(SimpleSourceRouteStrategy.class, null);
    routes = new TimerWeakHashMap<Identifier, SourceRoute>(environment.getSelectorManager(),300000);
  }

  /**
   * Note, this implementation only allows 1 - hop routes, need to check the liveness, of a route
   * to determine longer routes.  In most cases a 1-hop route should be sufficient.
   */
  public Collection<SourceRoute<Identifier>> getSourceRoutes(Identifier destination) {
    Collection<Identifier> nextHops = strategy.getNextHops(destination);
    List<SourceRoute<Identifier>> ret = new ArrayList<SourceRoute<Identifier>>(nextHops.size());
    for (Identifier intermediate : nextHops) {
      if (!intermediate.equals(destination)) {
        List<Identifier> hopList = new ArrayList<Identifier>(3);
        
        hopList.add(localAddress);
        hopList.add(intermediate);
        hopList.add(destination);
        SourceRoute<Identifier> route = srFactory.getSourceRoute(hopList);
        ret.add(route);
      }
    }
    return ret;
  }
  
  
  
  /**
   * Produces a route to the destination.  A direct route if there is not 
   * a cached multi-hop route.
   * 
   * @param dest
   */
  private SourceRoute<Identifier> getRoute(Identifier intermediate, Identifier dest) {
    SourceRoute route = routes.get(dest);
    if (route == null) {
      route = srFactory.getSourceRoute(localAddress,dest);
      routes.put(dest, route);
    }    
    return route;
  }
  
}
