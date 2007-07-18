/**
 * 
 */
package org.mpisws.p2p.transport.sourceroute.manager;

import java.util.Collection;
import java.util.List;

import org.mpisws.p2p.transport.multiaddress.MultiInetSocketAddress;
import org.mpisws.p2p.transport.sourceroute.SourceRoute;

/**
 * @author Jeff Hoye
 *
 */
public interface SourceRouteStrategy<Identifier> {
  
  /**
   * Do not include the destination in the list.
   * 
   * @param the destination
   * @return a collection of paths to the destination.  Don't include the local node at the beginning
   * of the path, nor the destination at the end.
   */
  Collection<SourceRoute<Identifier>> getSourceRoutes(Identifier destination);
}
