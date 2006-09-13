package rice.pastry.standard;

import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.client.PastryAppl;
import rice.pastry.leafset.*;
import rice.pastry.routing.*;

import java.util.*;

/**
 * An implementation of a simple leaf set protocol.
 * 
 * @version $Id$
 * 
 * @author Peter Druschel
 * @author Andrew Ladd
 */

public class StandardLeafSetProtocol extends PastryAppl {
  protected final boolean failstop = true;

  // nodes are assumed to fail silently

  protected LeafSet leafSet;

  protected RoutingTable routeTable;

  protected Logger logger;

  public StandardLeafSetProtocol(PastryNode ln, NodeHandle local,
      LeafSet ls, RoutingTable rt) {
    super(ln, LeafSetProtocolAddress.getCode());
    leafSet = ls;
    cachedSet = new HashSet(leafSet.maxSize() * 2);  
    routeTable = rt;
    logger = ln.getEnvironment().getLogManager().getLogger(getClass(), null);
  }

  /**
   * Receives messages.
   * 
   * @param msg the message.
   */
  public void messageForAppl(Message msg) {
    if (msg instanceof BroadcastLeafSet) {
      // receive a leafset from another node
      BroadcastLeafSet bls = (BroadcastLeafSet) msg;
      int type = bls.type();

      NodeHandle from = bls.from();
      LeafSet remotels = bls.leafSet();

      // first, merge the received leaf set into our own
      boolean changed = mergeLeafSet(remotels, from);

      if (type == BroadcastLeafSet.JoinInitial) {
        // we have now successfully joined the ring, set the local node ready
        // localNode.setReady();
      }

      if (!failstop) {
        // with arbitrary node failures, we need to broadcast whenever we learn
        // something new
        if (changed)
          broadcast();

        // then, send ls to sending node if that node's ls is missing nodes
        checkLeafSet(remotels, from, false);
        return;
      }

      // if this node has just joined, notify the members of our new leaf set
      if (type == BroadcastLeafSet.JoinInitial)
        broadcast();

      // if we receive a correction to a leafset we have sent out, notify the
      // members of our new leaf set
      if (type == BroadcastLeafSet.Correction && changed)
        broadcast();

      // Check if any of our local leaf set members are missing in the received
      // leaf set
      // if so, we send our leafset to each missing entry and to the source of
      // the leafset
      // this guarantees correctness in the event of concurrent node joins in
      // the same leaf set
      checkLeafSet(remotels, from, true);

    } else if (msg instanceof RequestLeafSet) {
      // request for leaf set from a remote node
      RequestLeafSet rls = (RequestLeafSet) msg;

      NodeHandle returnHandle = rls.returnHandle();

      if (returnHandle.isAlive()) {
        BroadcastLeafSet bls = new BroadcastLeafSet(thePastryNode.getLocalHandle(), leafSet,
            BroadcastLeafSet.Update, rls.getTimeStamp());

        thePastryNode.send(returnHandle,bls);
      }
    } else if (msg instanceof InitiateLeafSetMaintenance) {
      // request for leafset maintenance

      // perform leafset maintenance
      maintainLeafSet();

    } else
      throw new Error("message received is of unknown type");
  }


  /**
   * Optimization. 
   * gc was thrashing due to all of these HashSets being created.  So, now we just reuse one.
   */
  HashSet cachedSet = null;
  
  /**
   * Checks a received leafset advertisement for missing nodes
   * 
   * @param remotels the remote leafset
   * @param from the node from which we received the leafset
   * @param notifyMissing if true, notify missing nodes
   * @return true if any nodes where found missing in the received leafset
   */
  protected boolean checkLeafSet(LeafSet remotels, NodeHandle from,
      boolean notifyMissing) {

    // check if any of our local leaf set members are missing in the received
    // leaf set
    // if so, we send our leafset to the source of the leafset,
    // and if notifyMissing, to each missing entry
    // this ensures correctness in the event of concurrent node joins in the
    // same leaf set
    // it also ensures recovery in the event of node failures

    HashSet insertedHandles;
    if (notifyMissing) {
      cachedSet.clear();
      insertedHandles = cachedSet;      
    } else
      insertedHandles = null;

    BroadcastLeafSet bl = new BroadcastLeafSet(thePastryNode.getLocalHandle(), leafSet,
        BroadcastLeafSet.Correction, 0);
    boolean changed = remotels.merge(leafSet, thePastryNode.getLocalHandle(), null,
        true, insertedHandles);

    if (changed) {
      // nodes where missing, send update to "from"
      thePastryNode.send(from,bl);

      if (notifyMissing) {
        // send leafset to nodes that where missing from remotels

        // for now, conservatively send to everyone
        // broadcast(BroadcastLeafSet.Correction);

        Iterator it = new ArrayList(insertedHandles).iterator();
        while (it.hasNext()) {
          // send leafset to missing node
          NodeHandle nh = (NodeHandle) it.next();
          thePastryNode.send(nh,bl);
        }
      }
    }

    return changed;
  }

  /**
   * Merge a remote leafset into our own
   * 
   * @param remotels the remote leafset
   * @param from the node from which we received the leafset
   * @return true if the leafset changed
   */

  protected boolean mergeLeafSet(LeafSet remotels, NodeHandle from) {
    return leafSet.merge(remotels, from, routeTable, false, null);
  }

  /**
   * Broadcast the leaf set to all members of the local leaf set.
   */

  protected void broadcast() {
    broadcast(BroadcastLeafSet.JoinAdvertise);
  }

  /**
   * Broadcast the leaf set to all members of the local leaf set.
   * 
   * @param type the type of broadcast message used
   */

  protected void broadcast(int type) {
    BroadcastLeafSet bls = new BroadcastLeafSet(thePastryNode.getLocalHandle(), leafSet, type, 0);

    int cwSize = leafSet.cwSize();
    int ccwSize = leafSet.ccwSize();
    IdSet sent = new IdSet();

    for (int i = -ccwSize; i <= cwSize; i++) {
      if (i == 0)
        continue;

      NodeHandle nh = leafSet.get(i);
      if (nh == null || nh.isAlive() == false)
        continue;

      if (!sent.isMember(nh.getNodeId())) {
        thePastryNode.send(nh,bls);
        sent.addMember(nh.getNodeId());
      }
    }
  }

  /**
   * Broadcast the local leaf set to all members of the given leaf set, plus the
   * node from which the leaf set was received.
   * 
   * @param ls the leafset whose members we send to local leaf set
   * @param from the node from which ls was received
   */

  protected void broadcast(LeafSet ls, NodeHandle from) {
    BroadcastLeafSet bls = new BroadcastLeafSet(thePastryNode.getLocalHandle(), leafSet,
        BroadcastLeafSet.JoinAdvertise, 0);

    int cwSize = ls.cwSize();
    int ccwSize = ls.ccwSize();

    for (int i = -ccwSize; i <= cwSize; i++) {
      NodeHandle nh;

      if (i == 0)
        nh = from;
      else
        nh = ls.get(i);

      if (nh == null || nh.isAlive() == false)
        continue;

      thePastryNode.send(nh,bls);

    }
  }

  /**
   * Maintain the leaf set. This method checks for dead leafset entries and
   * replaces them as needed. It is assumed that this method be invoked
   * periodically.
   */
  public void maintainLeafSet() {
    if (logger.level <= Logger.FINE) logger.log(
        "maintainLeafSet " + thePastryNode.getLocalHandle().getNodeId());

    boolean lostMembers = false;

    // check leaf set for dead entries
    // ccw half
    for (int i = -leafSet.ccwSize(); i < 0; i++) {
      NodeHandle nh = leafSet.get(i);
      if (nh != null && !nh.ping()) {
        // remove the dead entry
        leafSet.remove(nh);
        lostMembers = true;
      }
    }

    // cw half
    for (int i = leafSet.cwSize(); i > 0; i--) {
      NodeHandle nh = leafSet.get(i);
      if (nh != null && !nh.ping()) {
        // remove the dead entry
        leafSet.remove(nh);
        lostMembers = true;
      }
    }

    // if we lost entries, or the size is below max and we don't span the entire
    // ring then
    // request the leafset from other leafset members
    if (lostMembers || // (leafSet.size() < leafSet.maxSize() &&
                        // !leafSet.overlaps())) {
        (leafSet.size() < leafSet.maxSize())) {

      // request leaf sets
      requestLeafSet();
    }
  }

  /**
   * request the leaf sets from the two most distant members of our leaf set
   */

  private void requestLeafSet() {

    RequestLeafSet rls = new RequestLeafSet(thePastryNode.getLocalHandle(), thePastryNode.getEnvironment().getTimeSource().currentTimeMillis());
    int cwSize = leafSet.cwSize();
    int ccwSize = leafSet.ccwSize();
    boolean allDead = true;

    // request from most distant live ccw entry
    for (int i = -ccwSize; i < 0; i++) {
      NodeHandle handle = leafSet.get(i);
      if (handle != null && handle.isAlive()) {
        thePastryNode.send(handle,rls);
        allDead = false;
        break;
      }
    }

    if (allDead && leafSet.size() > 0)
      if (logger.level <= Logger.SEVERE) logger.log(
          "Ring failure at" + thePastryNode.getLocalHandle().getNodeId()
              + "all ccw leafset entries failed");

    allDead = true;
    // request from most distant live cw entry
    for (int i = cwSize; i > 0; i--) {
      NodeHandle handle = leafSet.get(i);
      if (handle != null && handle.isAlive()) {
        thePastryNode.send(handle,rls);
        allDead = false;
        break;
      }
    }

    if (allDead && leafSet.size() > 0)
      if (logger.level <= Logger.SEVERE) logger.log(
          "Ring failure at" + thePastryNode.getLocalHandle().getNodeId()
              + "all cw leafset entries failed");

  }

  public boolean deliverWhenNotReady() {
    return true;
  }

}
