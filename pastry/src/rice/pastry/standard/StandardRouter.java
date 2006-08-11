package rice.pastry.standard;

import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.client.PastryAppl;

/**
 * An implementation of the standard Pastry routing algorithm.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 * @author Rongmei Zhang/Y.Charlie Hu
 */

public class StandardRouter extends PastryAppl {

  /**
   * Constructor.
   * 
   * @param rt the routing table.
   * @param ls the leaf set.
   */

  public StandardRouter(PastryNode thePastryNode) {
    super(thePastryNode, RouterAddress.getCode());
  }

  /**
   * Receive a message from a remote node.
   * 
   * @param msg the message.
   */

  public void receiveMessage(Message msg) {
    if (msg instanceof RouteMessage) {
      route((RouteMessage) msg);
    } else {
      throw new Error("message " + msg + " bounced at StandardRouter");
    }
  }

  private void route(RouteMessage rm) {
    if (rm.routeMessage(thePastryNode.getLocalHandle()) == false)
      receiveRouteMessage(rm);    
  }
  
  
  /**
   * Receive and process a route message.
   * 
   * @param msg the message.
   */

  /**
   * Receive and process a route message.
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

    if (lsPos == 0) // message is for the local node so deliver it
      msg.nextHop = thePastryNode.getLocalHandle();

    else if ((lsPos > 0 && (lsPos < cwSize || !thePastryNode.getLeafSet().get(lsPos).getNodeId()
        .clockwise(target)))
        || (lsPos < 0 && (-lsPos < ccwSize || thePastryNode.getLeafSet().get(lsPos).getNodeId()
            .clockwise(target)))) {
      if (logger.level <= Logger.FINEST) logger.log("receiveRouteMessage("+msg+"):1");  
    // the target is within range of the leafset, deliver it directly    
      NodeHandle handle = thePastryNode.getLeafSet().get(lsPos);

      if (handle.isAlive() == false) {
        // node is dead - get rid of it and try again
        thePastryNode.getLeafSet().remove(handle);
        receiveRouteMessage(msg);
        return;
      } else {
        msg.nextHop = handle;
        msg.getOptions().setRerouteIfSuspected(false);
        thePastryNode.getRoutingTable().put(handle);        
      }
    } else {
      if (logger.level <= Logger.FINEST) logger.log("receiveRouteMessage("+msg+"):2");  
      // use the routing table
      RouteSet rs = thePastryNode.getRoutingTable().getBestEntry(target);
      NodeHandle handle = null;

      // get the closest alive node
      if (rs == null
          || ((handle = rs.closestNode(NodeHandle.LIVENESS_ALIVE)) == null)) {
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
        
        // no live routing table entry matching the next digit
        // get best alternate RT entry
        handle = thePastryNode.getRoutingTable().bestAlternateRoute(NodeHandle.LIVENESS_ALIVE,
            target);

        if (handle == null) {
          // no alternate in RT, take leaf set extent
          handle = thePastryNode.getLeafSet().get(lsPos);

          if (handle.isAlive() == false) {
            thePastryNode.getLeafSet().remove(handle);
            receiveRouteMessage(msg);
            return;
          } else {
            msg.getOptions().setRerouteIfSuspected(false);
          }
        } else {
          if (logger.level <= Logger.FINEST) logger.log("receiveRouteMessage("+msg+"):3");  
          Id.Distance altDist = handle.getNodeId().distance(target);
          Id.Distance lsDist = thePastryNode.getLeafSet().get(lsPos).getNodeId().distance(
              target);

          if (lsDist.compareTo(altDist) < 0) {
            // closest leaf set member is closer
            handle = thePastryNode.getLeafSet().get(lsPos);

            if (handle.isAlive() == false) {
              thePastryNode.getLeafSet().remove(handle);
              receiveRouteMessage(msg);
              return;
            } else {
              msg.getOptions().setRerouteIfSuspected(false);
            }
          }
        }
      } //else {
        // we found an appropriate RT entry, check for RT holes at previous node
//      checkForRouteTableHole(msg, handle);
//      }

      msg.nextHop = handle;
    }
    
    // this wasn't being called often enough in its previous location, moved here Aug 11, 2006
    checkForRouteTableHole(msg, msg.nextHop);
    msg.setPrevNode(thePastryNode.getLocalHandle());
    thePastryNode.getLocalHandle().receiveMessage(msg);
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

    int diffDigit;

    // if we both have the same prefix (in other words the previous node didn't make a prefix of progress)
    if ((diffDigit = prevId.indexOfMSDD(key, thePastryNode.getRoutingTable().baseBitLength())) == 
      thePastryNode.getNodeId().indexOfMSDD(key, thePastryNode.getRoutingTable().baseBitLength())) {

      // the previous node is missing a RT entry, send the row
      // for now, we send the entire row for simplicity

      RouteSet[] row = thePastryNode.getRoutingTable().getRow(diffDigit);
      BroadcastRouteRow brr = new BroadcastRouteRow(thePastryNode.getLocalHandle(), row);

      if (prevNode.isAlive()) {
        if (logger.level <= Logger.FINE) {
          logger.log("Found hole in "+prevNode+"'s routing table. Sending "+brr.toStringFull());  
        }
        prevNode.receiveMessage(brr);
      }
    }

  }

  public boolean deliverWhenNotReady() {
    return true;
  }

  public void messageForAppl(Message msg) {
    throw new RuntimeException("Should not be called.");
  }
}

