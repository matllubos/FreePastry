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

import java.io.IOException;
import java.net.*;
import java.util.*;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.Id;
import rice.pastry.PastryNode;
import rice.pastry.dist.DistNodeHandle;
import rice.pastry.messaging.Message;
import rice.pastry.socket.SocketSourceRouteManager.AddressManager;

/**
 * Class which represents the address and nodeId of a remote node.  In
 * the socket protocol, it simply represents this information - all 
 * other details are managed by the local nodes.
 *
 * SocketNodeHandle can now internally exist without a NodeId.  This is to get
 * the memory management correct
 *
 * @version $Id$
 * @author Alan Mislove
 */
public class SocketNodeHandle extends DistNodeHandle {
  
  static final long serialVersionUID = -5452528188786429274L;
  
  // a special liveness value that indicates that this node will never come alive again
  public static final int LIVENESS_DEAD_FOREVER = 4;

  // the default distance, which is used before a ping
  public static final int DEFAULT_PROXIMITY = 60*60*1000;

  protected EpochInetSocketAddress eaddress;
  
  transient AddressManager addressManager;
  
  /**
   * Constructor
   *
   * @param nodeId This node handle's node Id.
   * @param address DESCRIBE THE PARAMETER
   */
  public SocketNodeHandle(EpochInetSocketAddress address, Id nodeId) {
    super(nodeId);
    
    this.eaddress = address;    
  }
  
  public EpochInetSocketAddress getEpochAddress() {
    return eaddress;
  }

  /**
   * Returns the last known liveness information about the Pastry node
   * associated with this handle. Invoking this method does not cause network
   * activity.
   *
   * @return true if the node is alive, false otherwise.
   */
  public int getLiveness() {
    SocketPastryNode spn = (SocketPastryNode) getLocalNode();

    if (spn == null || getLocalNode().getLocalHandle() == null) {
      return LIVENESS_ALIVE;
    } else {
      if (isLocal()) 
        return LIVENESS_ALIVE;
      else {
        // make sure this isn't an old existance of ourself:       
        EpochInetSocketAddress localEaddr = ((SocketNodeHandle)getLocalNode().getLocalHandle()).eaddress;
        if (localEaddr.addressEquals(eaddress) && (eaddress.epoch != localEaddr.epoch))
          return LIVENESS_DEAD_FOREVER;
        
        return spn.getSocketSourceRouteManager().getLiveness(eaddress);
      }
    }
  }
  
  /**
   * You can call this method if the node shuts down nicely.  This will cause it to be removed
   * from the leafset.  Improves the performance of consistency.
   */
  public void markDeadForever() {
    SocketPastryNode spn = (SocketPastryNode) getLocalNode();
    spn.getSocketSourceRouteManager().getAddressManager(eaddress, false).markDeadForever();
  }
  
  /**
   * Returns the InetSocketAddress that should be used to contact the node
   * 
   * @param addressList The sorted list of address alieses.  From Internet to LAN
   * @return
   */
  public InetSocketAddress getAddress(InetAddress[] addressList) {
    return eaddress.getAddress(addressList);
  }
  
  public InetSocketAddress getAddress() {
    return eaddress.address[0];
  }
  
  /**
   * Method which FORCES a check of liveness of the remote node.  Note that
   * this method should ONLY be called by internal Pastry maintenance algorithms - 
   * this is NOT to be used by applications.  Doing so will likely cause a
   * blowup of liveness traffic.
   *
   * @return true if node is currently alive.
   */
  public boolean checkLiveness() {
    SocketPastryNode spn = (SocketPastryNode) getLocalNode();
    
    if (spn != null)
      spn.getSocketSourceRouteManager().checkLiveness(eaddress);
    
    return isAlive();
  }

  /**
   * Method which returns whether or not this node handle is on its
   * home node.
   *
   * @return Whether or not this handle is local
   */
  public boolean isLocal() {
    assertLocalNode();
    return getLocalNode().getLocalHandle().equals(this);
  }
  
  /**
   * Called to send a message to the node corresponding to this handle.
   *
   * @deprecated use PastryNode.send(msg, nh)
   * @param msg Message to be delivered, may or may not be routeMessage.
   */
  public void receiveMessage(Message msg) {
    assertLocalNode();

    getLocalNode().send(this, msg);
  }
  
  /**
   * Method which is used by Pastry to start the bootstrapping process on the 
   * local node using this handle as the bootstrap handle.  Default behavior is
   * simply to call receiveMessage(msg), but transport layer implementations may
   * care to perform other tasks by overriding this method, since the node is
   * not technically part of the ring yet.
   *
   * @param msg the bootstrap message.
   */
  public void bootstrap(Message msg) throws IOException {
    ((SocketPastryNode) getLocalNode()).getSocketSourceRouteManager().bootstrap(eaddress, msg);
  }
    
  /**
   * Returns a String representation of this DistNodeHandle. This method is
   * designed to be called by clients using the node handle, and is provided in
   * order to ensure that the right node handle is being talked to.
   *
   * @return A String representation of the node handle.
   */
  public String toString() {
    return "[SNH: " + nodeId + "/" + eaddress + "]";
//    if (getLocalNode() == null) {
//      return "[SNH: " + nodeId + "/" + eaddress + "]";
//    } else {
//      return "[SNH: " + getLocalNode().getNodeId() + " -> " + nodeId + "/" + eaddress + "]";
//    }
  }

  /**
   * Equivalence relation for nodehandles. They are equal if and only if their
   * corresponding NodeIds are equal.
   *
   * @param obj the other nodehandle .
   * @return true if they are equal, false otherwise.
   */
  public boolean equals(Object obj) {
    if (! (obj instanceof SocketNodeHandle)) 
      return false;

    SocketNodeHandle other = (SocketNodeHandle) obj;
    
    return (other.getNodeId().equals(getNodeId()) && other.eaddress.equals(eaddress));
  }

  /**
   * Hash codes for node handles. It is the hashcode of their corresponding
   * NodeId's.
   *
   * @return a hash code.
   */
  public int hashCode() {
    return getNodeId().hashCode() ^ eaddress.hashCode();
  }

  /**
   * Returns the last known proximity information about the Pastry node
   * associated with this handle. Invoking this method does not cause network
   * activity. Smaller values imply greater proximity. The exact nature and
   * interpretation of the proximity metric implementation-specific.
   *
   * @deprecated use PastryNode.proximity(nh)
   * @return the proximity metric value
   */
  public int proximity() {
    PastryNode spn = getLocalNode();
    if (spn == null)
      return DEFAULT_PROXIMITY;
    return spn.proximity(this);
  }

  /**
   * Ping the node. Refreshes the cached liveness status and proximity value of
   * the Pastry node associated with this. Invoking this method causes network
   * activity.
   *
   * @return true if node is currently alive.
   */
  public boolean ping() {
    final SocketPastryNode spn = (SocketPastryNode) getLocalNode();
    
//    Runnable runnable = new Runnable() {    
//      public void run() {
        if ((spn != null) && spn.srManager != null) 
          spn.srManager.ping(eaddress);
//      }
//    };
//    
//    SelectorManager sm = spn.getEnvironment().getSelectorManager();
//    if (sm.isSelectorThread()) {
//      runnable.run();      
//    } else {
//      sm.invoke(runnable);
//    }
    
    return isAlive();
  }  

  /**
   * DESCRIBE THE METHOD
   *
   * @param o DESCRIBE THE PARAMETER
   * @param obj DESCRIBE THE PARAMETER
   */
  public void update(Observable o, Object obj) {
  }


/*********************** Serialization ***********************/  
  public void setNodeId(Id nodeId) {
    this.nodeId = nodeId;    
  }
  
  public void setLocalNode(SocketPastryNode spn) {
    localnode = spn; 
    this.logger = spn.getEnvironment().getLogManager().getLogger(getClass(),null);
  }
  
  /**
   * Note, this SNH needs to be coalesced!!!
   * @param buf
   * @return
   */
  static SocketNodeHandle build(InputBuffer buf) throws IOException {
//    NodeHandle (Version 0)
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    + Epoch InetSocketAddress                                       +
//    +                                                               +           
//    +                                                               +           
//    +                                                               +           
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    +   Id                                                          +
//    +                                                               +
//    +                                                               +
//    +                                                               +
//    +                                                               +
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    EpochInetSocketAddress eaddr = EpochInetSocketAddress.build(buf);
    Id nid = Id.build(buf);
    return new SocketNodeHandle(eaddr, nid);
  }

  public void serialize(OutputBuffer buf) throws IOException {
    eaddress.serialize(buf);
    nodeId.serialize(buf);
  }  
}


