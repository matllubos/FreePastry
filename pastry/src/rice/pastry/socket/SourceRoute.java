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

package rice.pastry.socket;

import java.io.*;
import java.lang.ref.*;
import java.net.*;
import java.util.*;

import rice.pastry.*;
import rice.pastry.dist.*;
import rice.pastry.messaging.*;

/**
 * Class which represets a source route to a remote IP address.
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SourceRoute implements Serializable {
  
  // serialver, for backward compatibility
  private static final long serialVersionUID = -4402277039316685149L;
    
  // Support for coalesced Ids - ensures only one copy of each Id is in memory
  private static WeakHashMap SOURCE_ROUTE_MAP = new WeakHashMap();
  
  // the default distance, which is used before a ping
  protected EpochInetSocketAddress[] path;

  /**
   * Constructor
   *
   * @param nodeId This node handle's node Id.
   * @param address DESCRIBE THE PARAMETER
   */
  private SourceRoute(EpochInetSocketAddress[] path) {
    this.path = path;
  }  

  /**
   * Method which performs the coalescing and interaction with the weak hash map
   *
   * @param id The Id to coalesce
   * @return The Id to use
   */
  protected static SourceRoute resolve(WeakHashMap map, SourceRoute route) {
    synchronized (map) {
      WeakReference ref = (WeakReference) map.get(route);
      SourceRoute result = null;
      
      if ((ref != null) && ((result = (SourceRoute) ref.get()) != null)) {
        return result;
      } else {
        map.put(route, new WeakReference(route));
        return route;
      }
    }
  }
  
  /**
   * Constructor.
   *
   * @param path The path of the route
   */
  public static SourceRoute build(EpochInetSocketAddress[] path) {
    return resolve(SOURCE_ROUTE_MAP, new SourceRoute(path));
  }
  
  /**
   * Constructor.
   *
   * @param path The path of the route
   */
  public static SourceRoute build(EpochInetSocketAddress address) {
    return resolve(SOURCE_ROUTE_MAP, new SourceRoute(new EpochInetSocketAddress[] {address}));
  }
  
  /**
   * Define readResolve, which will replace the deserialized object with the canootical
   * one (if one exists) to ensure Id coalescing.
   *
   * @return The real Id
   */
  private Object readResolve() throws ObjectStreamException {
    return resolve(SOURCE_ROUTE_MAP, this);
  }

  /**
   * Returns the hashCode of this source route
   *
   * @return The hashCode
   */
  public int hashCode() {
    int result = 399388937;
    
    for (int i=0; i<path.length; i++)
      result ^= path[i].hashCode();
    
    return result;
  }
  
  /**
   * Checks equaltiy on source routes
   *
   * @param o The source route to compare to
   * @return The equality
   */
  public boolean equals(Object o) {
    if (o == null)
      return false;
    
    return Arrays.equals(path, ((SourceRoute) o).path);
  }
  
  /**
   * Internal method for computing the toString of an array of InetSocketAddresses
   *
   * @param path The path
   * @return THe string
   */
  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append("{");
    
    for (int i=0; i<path.length; i++) {
      result.append(path[i].getAddress().getAddress().getHostAddress() + ":" + path[i].getAddress().getPort());
      if (i < path.length - 1) result.append(" -> ");
    }
    
    result.append("}");
    
    return result.toString();
  }
  
  /**
   * Internal method for computing the toString of an array of InetSocketAddresses
   *
   * @param path The path
   * @return THe string
   */
  public String toStringFull() {
    StringBuffer result = new StringBuffer();
    result.append("{");
    
    for (int i=0; i<path.length; i++) {
      result.append(path[i].toString());
      if (i < path.length - 1) result.append(" -> ");
    }
    
    result.append("}");
    
    return result.toString();
  }
  
  /**
   * Method which revereses path and cliams the corresponding address
   *
   * @param path The path to reverse
   * @param address The address to claim
   */
  public SourceRoute reverse(EpochInetSocketAddress localAddress) {
    EpochInetSocketAddress[] result = new EpochInetSocketAddress[path.length];
    
    for (int i=0; i<path.length-1; i++)
      result[i] = path[path.length-2-i];
    
    result[result.length-1] = localAddress;
    
    return SourceRoute.build(result);
  }
  
  /**
   * Method which revereses path
   *
   */
  public SourceRoute reverse() {
    EpochInetSocketAddress[] result = new EpochInetSocketAddress[path.length];
    
    for (int i=0; i<path.length; i++)
      result[i] = path[path.length-1-i];
    
    return SourceRoute.build(result);
  }
  
  /**
   * Method which returns the first "hop" of this source route
   *
   * @return The first hop of this source route
   */
  public EpochInetSocketAddress getFirstHop() {
    return path[0];
  }
  
  /**
   * Method which returns the first "hop" of this source route
   *
   * @return The first hop of this source route
   */
  public EpochInetSocketAddress getLastHop() {
    return path[path.length-1];
  }
  
  /**
   * Returns the number of hops in this source route
   *
   * @return The number of hops 
   */
  public int getNumHops() {
    return path.length;
  }
  
  /**
   * Returns the hop at the given index
   *
   * @param i The hop index
   * @return The hop
   */
  public EpochInetSocketAddress getHop(int i) {
    return path[i];
  }
  
  /**
   * Returns whether or not this route is direct
   *
   * @return whether or not this route is direct
   */
  public boolean isDirect() {
    return (path.length == 1);
  }
  
  /**
   * Internal method which returns an array representing the source
   * route
   *
   * @return An array represetning the route
   */
  public InetSocketAddress[] toArray() {
    InetSocketAddress[] result = new InetSocketAddress[path.length];
    
    for (int i=0; i<result.length; i++)
      result[i] = path[i].getAddress();
    
    return result;
  }
  
  /**
   * Method which creates a new source route by appending the given address
   * to the end of this one
   *
   * @param address The address to append
   */
  public SourceRoute append(EpochInetSocketAddress address) {
    EpochInetSocketAddress[] result = new EpochInetSocketAddress[path.length+1];
    System.arraycopy(path, 0, result, 0, path.length);
    result[result.length-1] = address;
    
    return SourceRoute.build(result);
  }
  
  /**
   * Method which creates a new source route by appending the given address
   * to the end of this one
   *
   * @param address The address to append
   */
  public SourceRoute prepend(EpochInetSocketAddress address) {
    EpochInetSocketAddress[] result = new EpochInetSocketAddress[path.length+1];
    System.arraycopy(path, 0, result, 1, path.length);
    result[0] = address;
    
    return SourceRoute.build(result);
  }
}


