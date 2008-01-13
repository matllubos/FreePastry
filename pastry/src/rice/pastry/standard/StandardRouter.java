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
package rice.pastry.standard;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.util.HashMap;
import java.util.Map;

import org.mpisws.p2p.transport.exception.NodeIsFaultyException;

import rice.environment.logging.Logger;
import rice.p2p.commonapi.exception.AppNotRegisteredException;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.MessageDeserializer;
import rice.pastry.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.transport.PMessageNotification;
import rice.pastry.transport.PMessageReceipt;
import rice.pastry.client.PastryAppl;

/**
 * An implementation of the standard Pastry routing algorithm.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 * @author Rongmei Zhang/Y.Charlie Hu
 */

public class StandardRouter extends PastryAppl implements Router {

  MessageDispatch dispatch;
  
  /**
   * Constructor.
   * 
   * @param rt the routing table.
   * @param ls the leaf set.
   */

  public StandardRouter(final PastryNode thePastryNode, MessageDispatch dispatch) {
    super(thePastryNode, null, RouterAddress.getCode(), new MessageDeserializer() {
    
      public rice.p2p.commonapi.Message deserialize(InputBuffer buf, short type, int priority,
          rice.p2p.commonapi.NodeHandle sender) throws IOException {
        RouteMessage rm;
        rm = RouteMessage.build(buf, (byte)priority, thePastryNode, (NodeHandle)sender,
            (byte)thePastryNode.getEnvironment().getParameters().getInt("pastry_protocol_router_routeMsgVersion"));
        return rm;
      }    
    });
    this.dispatch = dispatch;
  }

  /**
   * Receive a message from a remote node.
   * 
   * @param msg the message.
   */

  public void receiveMessage(Message msg) {
    if (logger.level <= Logger.FINER) logger.log("receiveMessage("+msg+")");
    if (msg instanceof RouteMessage) {
      // should only happen for messages coming off the wire
      route((RouteMessage) msg);
    } else {
      throw new Error("message " + msg + " bounced at StandardRouter");
    }
  }

//  public void route(RouteMessage rm) {
//    route(rm, null);
//  }

  public void route(RouteMessage rm) {
    if (logger.level <= Logger.FINE) logger.log("route("+rm+")");
    if (routeMessage(rm) == false)
      receiveRouteMessage(rm);    
  }
  
  /**
   * Routes the messages if the next hop has been set up.
   * 
   * Note, this once lived in the RouteMessaage
   * 
   * @param localId the node id of the local node.
   * 
   * @return true if the message got routed, false otherwise.
   */
  public boolean routeMessage(RouteMessage rm) {
    if (logger.level <= Logger.FINER) logger.log("routeMessage("+rm+")");
    if (rm.getNextHop() == null)
      return false;
    rm.setSender(thePastryNode.getLocalHandle());

    NodeHandle handle = rm.getNextHop();
    rm.setNextHop(null);
    rm.setPrevNode(thePastryNode.getLocalHandle());
    
    if (thePastryNode.getLocalHandle().equals(handle)) {
      // the local node is the closest node to the id
      if (rm.getDestinationHandle() != null && !rm.getDestinationHandle().equals(thePastryNode.getLocalHandle())) {
        // no idea how to contact the destination, drop
        if (logger.level <= Logger.WARNING) logger.log("Message "+rm+" has destination "+rm.getDestinationHandle()+" but I'm the root of the id.  Dropping.");
        rm.sendFailed(new NoRouteToHostException(rm.getDestinationHandle().toString()));
        return true;
      }
      thePastryNode.receiveMessage(rm.internalMsg);
      rm.sendSuccess(thePastryNode.getLocalHandle());
    } else {
      sendTheMessage(rm, handle);
    }
    return true;
  }

  protected void sendTheMessage(final RouteMessage rm, final NodeHandle handle) {    
    if (logger.level <= Logger.FINER) logger.log("sendTheMessage("+rm+","+handle+")");
    rm.setTLCancellable(thePastryNode.send(handle, rm, new PMessageNotification(){    
      public void sent(PMessageReceipt msg) {
        rm.sendSuccess(handle);
      }    
      public void sendFailed(PMessageReceipt msg, Exception reason) {
        if (rm.sendFailed(reason)) {
          if (logger.level <= Logger.CONFIG) logger.logException("sendFailed("+rm+")=>"+handle, reason);
        } else {
          if (logger.level <= Logger.FINE) {
            logger.logException("sendFailed("+rm+")=>"+handle, reason);                      
          } else {
            if (logger.level <= Logger.WARNING) logger.log("sendFailed("+rm+")=>"+handle+" "+reason);          
          }
        }
      }    
    }, rm.getTLOptions())); 
  }

  
  /**
   * Receive and process a route message.  Sets a nextHop.
   * 
   * @param msg the message.
   */
  private void receiveRouteMessage(RouteMessage msg) {
    if (logger.level <= Logger.FINER) logger.log("receiveRouteMessage("+msg+")");  
    Id target = msg.getTarget();

    if (target == null)
      target = thePastryNode.getNodeId();

    int cwSize = thePastryNode.getLeafSet().cwSize();
    int ccwSize = thePastryNode.getLeafSet().ccwSize();

    int lsPos = thePastryNode.getLeafSet().mostSimilar(target);

    if (lsPos == 0) {
      // message is for the local node so deliver it
      msg.setNextHop(thePastryNode.getLocalHandle());
      
      // don't return, we want to check for routing table hole
    } else if ((lsPos > 0 &&  (lsPos < cwSize || !thePastryNode.getLeafSet().get(lsPos).getNodeId().clockwise(target)))
            || (lsPos < 0 && (-lsPos < ccwSize || thePastryNode.getLeafSet().get(lsPos).getNodeId().clockwise(target)))) {
      if (logger.level <= Logger.FINEST) logger.log("receiveRouteMessage("+msg+"):1"); 
      
      // the target is within range of the leafset, deliver it directly          
      msg.setNextHop(getBestHandleFromLeafset(msg, lsPos));
      if (msg.getNextHop() == null) return; // we are dropping this message to maintain consistency
      thePastryNode.getRoutingTable().put(msg.getNextHop());        
    } else {
      // use the routing table     
      
      // enable rapid rerouting
      msg.getOptions().setRerouteIfSuspected(true);
      if (logger.level <= Logger.FINEST) logger.log("receiveRouteMessage("+msg+"):2");  
      RouteSet rs = thePastryNode.getRoutingTable().getBestEntry(target);
      NodeHandle handle = null;

      // get the closest alive node
      if (rs == null
          || ((handle = rs.closestNode(NodeHandle.LIVENESS_ALIVE)) == null)) {
        // no live routing table entry matching the next digit
        
        
        // cull out dead nodes (this is mostly for the simulator -- I hope --, the listener interface should make this work normally)
        if (rs != null) {
          for (int index = 0; index < rs.size(); index++) {
            NodeHandle nh = rs.get(index);
            if (!nh.isAlive()) {
              rs.remove(nh);
              index--;
            }
          }
        }
        
        // get best alternate RT entry
        handle = thePastryNode.getRoutingTable().bestAlternateRoute(NodeHandle.LIVENESS_ALIVE,
            target);

        if (handle != null) {
          // found alternate route, make sure that the leafset isn't better
          if (logger.level <= Logger.FINEST) logger.log("receiveRouteMessage("+msg+"):3");  
          Id.Distance altDist = handle.getNodeId().distance(target);
          Id.Distance lsDist = thePastryNode.getLeafSet().get(lsPos).getNodeId().distance(
              target);

          if (lsDist.compareTo(altDist) < 0) {
            // the leaf set member on the edge of the leafset is closer
            handle = getBestHandleFromLeafset(msg, lsPos);
            msg.setNextHop(handle);
            if (msg.getNextHop() == null) return; // we are dropping this message to maintain consistency
            thePastryNode.getRoutingTable().put(msg.getNextHop());        
          } 
        } else {
          // no alternate in RT, take node at the edge of the leafset
          handle = getBestHandleFromLeafset(msg, lsPos);
          msg.setNextHop(handle);
          if (msg.getNextHop() == null) return; // we are dropping this message to maintain consistency
          thePastryNode.getRoutingTable().put(msg.getNextHop());        
        }
      } //else {
        // we found an appropriate RT entry, check for RT holes at previous node
//      checkForRouteTableHole(msg, handle);
//      }

      msg.setNextHop(handle);
    }
    
    // this wasn't being called often enough in its previous location, moved here Aug 11, 2006
    checkForRouteTableHole(msg, msg.getNextHop());
    msg.setPrevNode(thePastryNode.getLocalHandle());

    // here, we need to deliver the msg to the proper app
    deliverToApplication(msg);
  }

  public void deliverToApplication(RouteMessage msg) {
    PastryAppl appl = dispatch.getDestinationByAddress(msg.getAuxAddress());
    if (appl == null) {
      if (msg.sendFailed(new AppNotRegisteredException(msg.getAuxAddress()))) {
        if (logger.level <= Logger.CONFIG) logger.log(
            "Dropping message " + msg + " because the application address " + msg.getAuxAddress() + " is unknown.");        
      } else {
        if (logger.level <= Logger.WARNING) logger.log(
            "Dropping message " + msg + " because the application address " + msg.getAuxAddress() + " is unknown.");                
      }
      return;      
    }
    appl.receiveMessage(msg);
//    thePastryNode.receiveMessage(msg);    
  }
  
  /**
   * This is a helper function to get the rapid-rerouting correct.  
   * 
   * Consider that we have less accurate information about the liveness 
   * of nodes on the edge of the leafset.  Thus we want rapid-rerouting to closer
   * leafset members if the far ones are suspected.  
   * 
   * This code picks the nearest node to the lsPos that is not suspected.  If
   * they are all suspected, it goes with lsPos.  
   * 
   * If the node at lsPos is faulty, then we need to drop the message to preserve
   * consistency.  Because if he is in the leafset, but faulty, then this indicates
   * we gave him a lease.  In this case we return null.  This indicates that the
   * message should be dropped.
   *  
   * @param msg will properly setRerouteIfSuspected() on this
   * @param lsPos the best candidate not considering liveness
   * @return the best candidtate from the leafset.  If null, drop the message.
   */
  private NodeHandle getBestHandleFromLeafset(RouteMessage msg, int lsPos) {
    NodeHandle handle = thePastryNode.getLeafSet().get(lsPos);

    switch (handle.getLiveness()) {
      case NodeHandle.LIVENESS_ALIVE:
        // go ahead and leave rapid rerouting on, even if this is our
        // next door neighbor, it is possible that someone new will
        // come into the leafset between us
        msg.getOptions().setRerouteIfSuspected(true);
        break;
      case NodeHandle.LIVENESS_SUSPECTED:
        // you have more accurate information about liveness in your leafset
        // closer to you.  Thus, just try to get it closer because they 
        // may have already found the node faulty
        // if there is someone between us who is alive, deliver it to them
        if (lsPos > 0) {
          // search for someone between us who is alive
          for (int i = lsPos-1; i > 0; i--) {
            NodeHandle temp = thePastryNode.getLeafSet().get(i);
            if (temp.getLiveness() < NodeHandle.LIVENESS_SUSPECTED) {
              handle = temp;
              break; // the for loop
            }
          }
        } else { // lsPos < 0
          for (int i = lsPos; i < 0; i++) {
            NodeHandle temp = thePastryNode.getLeafSet().get(i);
            if (temp.getLiveness() < NodeHandle.LIVENESS_SUSPECTED) {
              handle = temp;
              break; // the for loop
            }
          }            
        }
        if (handle.getLiveness() < NodeHandle.LIVENESS_SUSPECTED) {
          // we found someone closer, turn on rapid rerouting
          msg.getOptions().setRerouteIfSuspected(true);
        } else {
          // we didn't find anyone better, don't reroute if suspected, 
          // cause everyone is suspected
          msg.getOptions().setRerouteIfSuspected(false);            
        }
        break;
      default: // if (!handle.isAlive()) {
        // node is dead but still in the leafset 
        // we must have given him a lease
        // drop the message
        
        // generally, there shouldn't be anyone between us (handle and localHandle) in the leafset, and if
        // there is, he is probably not ready, or if he is, he shouldn't be, so drop the message          
        if (msg.sendFailed(new NodeIsFaultyException(handle))) {
          if (logger.level <= Logger.CONFIG) {
            logger.log("Dropping "+msg+" because next hop: "+handle+" is dead but has lease.");
//            logger.logException("Dropping "+msg+" because next hop: "+handle+" is dead but has lease.", new Exception("Stack Trace"));
          }          
        } else {
          if (logger.level <= Logger.WARNING) {
            logger.log("Dropping "+msg+" because next hop: "+handle+" is dead but has lease.");
//            logger.logException("Dropping "+msg+" because next hop: "+handle+" is dead but has lease.", new Exception("Stack Trace"));
          }                    
        }
        return null;
    }
    return handle;
  }

  /**
   * checks to see if the previous node along the path was missing a RT entry if
   * so, we send the previous node the corresponding RT row to patch the hole
   * 
   * @param msg the RouteMessage being routed
   * @param handle the next hop handle
   */

  private void checkForRouteTableHole(RouteMessage msg, NodeHandle handle) {
    if (logger.level <= Logger.FINEST) logger.log("checkForRouteTableHole("+msg+","+handle+")");  

    NodeHandle prevNode = msg.getPrevNode();
    if (prevNode == null) {
      if (logger.level <= Logger.FINER) logger.log("No prevNode defined in "+msg);  
      return;
    }

    if (prevNode.equals(getNodeHandle())) {
      if (logger.level <= Logger.FINER) logger.log("prevNode is me in "+msg);  
      return;
    }

    // we don't want to send the repair if they just routed in the leafset
    LeafSet ls = thePastryNode.getLeafSet();
    if (ls.overlaps()) return; // small network, don't bother
    if (ls.member(prevNode)) {
      // ok, it's in my leafset, so I'm in his, but make sure that it's not on the edge
      int index = ls.getIndex(prevNode);
      if ((index == ls.cwSize()) || (index == -ls.ccwSize())) {
        // it is the edge... continue with repair 
      } else {
        return;
      }
    }
    
    Id prevId = prevNode.getNodeId();
    Id key = msg.getTarget();

    int diffDigit = prevId.indexOfMSDD(key, thePastryNode.getRoutingTable().baseBitLength());

    // if we both have the same prefix (in other words the previous node didn't make a prefix of progress)
    if (diffDigit >= 0 && 
        diffDigit == thePastryNode.getNodeId().indexOfMSDD(key, thePastryNode.getRoutingTable().baseBitLength())) {      
      synchronized(lastTimeSentRouteTablePatch) {
        if (lastTimeSentRouteTablePatch.containsKey(prevNode)) {
          long lastTime = lastTimeSentRouteTablePatch.get(prevNode);
          if (lastTime > (thePastryNode.getEnvironment().getTimeSource().currentTimeMillis() - ROUTE_TABLE_PATCH_THROTTLE)) {
            if (logger.level <= Logger.INFO) logger.log("not sending route table patch to "+prevNode+" because throttled.  Last Time:"+lastTime);            
            return;
          }
        }
        lastTimeSentRouteTablePatch.put(prevNode, thePastryNode.getEnvironment().getTimeSource().currentTimeMillis());
      }
      
      // the previous node is missing a RT entry, send the row
      // for now, we send the entire row for simplicity

      RouteSet[] row = thePastryNode.getRoutingTable().getRow(diffDigit);
      BroadcastRouteRow brr = new BroadcastRouteRow(thePastryNode.getLocalHandle(), row);

      if (prevNode.isAlive()) {
        if (logger.level <= Logger.FINE) {
          logger.log("Found hole in "+prevNode+"'s routing table. Sending "+brr.toStringFull());  
        }
        thePastryNode.send(prevNode,brr,null,options);
      }
    }
  }

  protected int ROUTE_TABLE_PATCH_THROTTLE = 5000;
  /**
   * We can end up causing a nasty feeback if we blast too many BRRs, so we're 
   * going to throttle.
   */
  protected Map<NodeHandle, Long> lastTimeSentRouteTablePatch = new HashMap<NodeHandle, Long>();
  
  
  public boolean deliverWhenNotReady() {
    return true;
  }

  public void messageForAppl(Message msg) {
    throw new RuntimeException("Should not be called.");
  }
}

