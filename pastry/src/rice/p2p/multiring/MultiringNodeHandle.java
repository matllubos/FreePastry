/*************************************************************************

"Free Pastry" Peer-to-Peer Application Development Substrate 

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

package rice.p2p.multiring;

import rice.p2p.commonapi.*;

import java.io.*;
import java.util.*;

/**
 * @(#) MultiringNodeHandle.java
 *
 * @author Alan Mislove
 */
public class MultiringNodeHandle extends NodeHandle implements Observer  {
  
  /**
   * The internal handle
   */
  protected NodeHandle handle;
  
  /**
   * The handle's ringId
   */
  protected Id ringId;
  
  /**
    * Constructor
   *
   * @param handle The handle to wrap
   * @param ringId The handle's ringId
   */
  public MultiringNodeHandle(Id ringId, NodeHandle handle) {
    this.handle = handle;
    this.ringId = ringId;
    
    handle.addObserver(this);
  }
  
  /**
   * Returns the internal handle
   *
   * @return The internal handle
   */
  protected NodeHandle getHandle() {
    return handle;
  }
  
  /**
   * Returns this node's id.
   *
   * @return The corresponding node's id.
   */
  public Id getId() {
    return new RingId(ringId, handle.getId());
  }
  
  /**
   * Returns whether or not this node is currently alive
   *
   * @return Whether or not this node is currently alive
   */
  public boolean isAlive() {
    return handle.isAlive();
  }
  
  /**
   * Returns the current proximity value of this node
   *
   * @return The current proximity value of this node
   */
  public int proximity() {
    return handle.proximity();
  }
  
  /**
   * Observable callback.  Simply rebroadcasts
   *
   * @param o the updated object
   * @param obj The paramter
   */
  public void update(Observable o, Object obj) {
    setChanged();
    notifyObservers(obj);
  }
  
  /**
   * Prints out the string
   *
   * @return A string
   */
  public String toString() {
    return "{RingId " + ringId + " " + handle.toString() + "}";
  }
  
  /**
   * Returns whether or not this object is equal to the provided one
   *
   * @param o The object to compare to
   * @return Whether or not this is object is equal
   */
  public boolean equals(Object o) {
    if (! (o instanceof MultiringNodeHandle))
      return false;
    
    return (((MultiringNodeHandle) o).handle.equals(handle) && ((MultiringNodeHandle) o).ringId.equals(ringId));
  }
  
  /**
   * Returns the hashCode
   *
   * @return hashCode
   */
  public int hashCode() {
    return (handle.hashCode() + ringId.hashCode());
  }
}


