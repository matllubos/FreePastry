/*
 * Created on Aug 1, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package rice.pastry.churn;

import rice.pastry.NodeHandle;
import rice.pastry.NodeId;
import rice.pastry.PastryNode;
import rice.pastry.join.InitiateJoin;
import rice.pastry.join.JoinRequest;
import rice.pastry.leafset.BroadcastLeafSet;
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.RouteMessage;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;
import rice.pastry.security.PermissiveCredentials;
import rice.pastry.standard.StandardJoinProtocol;

/**
 * @author jeffh
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class ChurnJoinProtocol extends StandardJoinProtocol {
  ChurnLeafSetProtocol lsProtocol;
  
  public ChurnJoinProtocol(PastryNode ln, NodeHandle lh, PastrySecurityManager sm, 
      RoutingTable rt, LeafSet ls, ChurnLeafSetProtocol lsProtocol) {
    super(ln, lh, sm, rt, ls);
    this.lsProtocol = lsProtocol;
  }

  /**
   * JOINi(seed)
   *   send (JOIN-REQUEST; {}; i) to seed
   * 
   * @param ij
   */
  protected void handleInitiateJoin(InitiateJoin ij) {
    NodeHandle nh = ij.getHandle();

    nh = security.verifyNodeHandle(nh);

    if (nh.isAlive() == true) {
      JoinRequest jr = new JoinRequest(localHandle);

      RouteMessage rm =
        new RouteMessage(
          localHandle.getNodeId(),
          jr,
          new PermissiveCredentials(),
          address);
      rm.getOptions().setRerouteIfSuspected(false);
      nh.receiveMessage(rm);
    }    
  }

  /**
   * RECEIVE(JOIN-REQUEST; R; j)
   *   R.add(Ri)
   *   route(JOIN-REQUEST;R; ji; j)
   */
  protected void handleRouteJoinRequest(JoinRequest jr, RouteMessage rm) {
    System.out.println(localNode+" ************ ChurnJoinProtocol.handleJoinRequest()");
    NodeId localId = localHandle.getNodeId();
    NodeHandle jh = jr.getHandle();
    NodeId nid = jh.getNodeId();

    jh = security.verifyNodeHandle(jh);

    int base = RoutingTable.baseBitLength();

    int msdd = localId.indexOfMSDD(nid, base);
    int last = jr.lastRow();

    //System.out.println("join from " + nid + " at " + localId + " msdd=" + msdd + " last=" + last);

//  R.add(Ri) 

    for (int i = last - 1; msdd > 0 && i >= msdd; i--) {
      //System.out.println(routeTable);
      //System.out.print(i + " ");

      RouteSet row[] = routeTable.getRow(i);

      jr.pushRow(row);
    }

//  route(JOIN-REQUEST;R; ji; j)
    rm.routeMessage(localId);    
  }

//  receive-root(<JOIN-REQUEST, R, j>, j)
//    if (active)
//      send (<JOIN-REPLY, R, L,> to j)
  protected void handleJoinRequest(JoinRequest jr, NodeHandle nh) {
    //  receive-root(JOIN-REQUEST;R; ji; j)
    //if (activei)
      if (!localNode.isReady()) return;
    
      jr.acceptJoin(localHandle, leafSet);
    //send (JOIN-REPLY; R; L) to j
      nh.receiveMessage(jr);
  }

//  RECEIVE(JOIN-REPLY; R; L)
//    Ri.add(R [ L); Li:add(L)
//    for each j in Li do f probe(j) }
  protected void handleJoinResponse(JoinRequest jr) {
    System.out.println(localNode+" ************ ChurnJoinProtocol.handleJoinResponse():"+jr.accepted());
    NodeHandle nh = jr.getHandle();

    nh = security.verifyNodeHandle(nh);

    if (nh.isAlive() == true) // the handle is alive
      if (jr.accepted() == false) {
        // this is the terminal node on the request path
        handleJoinRequest(jr,nh);
      } else { // this is the node that initiated the join request in the first place
        NodeHandle jh = jr.getJoinHandle(); // the node we joined to.

        jh = security.verifyNodeHandle(jh);

        if (jh.equals(localHandle) && !localNode.isReady()) {
          System.out.println(
            "NodeId collision, unable to join: " + localHandle + ":" + jh);
          //Thread.dumpStack();
        } else if (jh.isAlive() == true) { // the join handle is alive          
          routeTable.put(jh);
          // add the num. closest node to the routing table

//        Ri.add(R [ L); Li:add(L)

          // now update the local leaf set
          //System.out.println("Join ls:" + jr.getLeafSet());
          BroadcastLeafSet bls =
            new BroadcastLeafSet(
              jh,
              jr.getLeafSet(),
              BroadcastLeafSet.JoinInitial);
          localHandle.receiveMessage(bls);

          // update local RT, then broadcast rows to our peers
          broadcastRows(jr);

          lsProtocol.probeLeafSet();

          // we have now successfully joined the ring, set the local node ready
          //localNode.setReady();
        }
      }    
  }

  public void receiveMessage(Message msg) {
    if (msg instanceof JoinRequest) {      
      JoinRequest jr = (JoinRequest) msg;
      handleJoinResponse(jr);
    } else if (
      msg instanceof RouteMessage) {
      // a join request message at an intermediate node
      RouteMessage rm = (RouteMessage) msg;

      JoinRequest jr = (JoinRequest) rm.unwrap();
      handleRouteJoinRequest(jr,rm);
    } else if (
      msg instanceof InitiateJoin) { // request from the local node to join
      InitiateJoin ij = (InitiateJoin) msg;
      handleInitiateJoin(ij);
    }
  }

}
