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
import rice.pastry.leafset.LeafSet;
import rice.pastry.messaging.Message;
import rice.pastry.routing.BroadcastRouteRow;
import rice.pastry.routing.RouteMessage;
import rice.pastry.routing.RouteSet;
import rice.pastry.routing.RoutingTable;
import rice.pastry.security.PastrySecurityManager;
import rice.pastry.security.PermissiveCredentials;
import rice.pastry.socket.SocketPastryNode;
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
    //System.out.println(localNode+" ************ ChurnJoinProtocol.handleRouteJoinRequest()");
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
    // this is different from the regular join protocol, because the probes 
    // are causing the leafset to be updated before the node has joined
//    System.out.println("  CJP.handleRouteJoinRequest() "+rm.nextHop+","+rm.getTarget());
    if (rm.nextHop.getId().equals(rm.getTarget())) {
//      System.out.println("  CJP.handleRouteJoinRequest() 2");
      handleJoinRequest(jr,rm.nextHop);      
    } else {
      rm.routeMessage(localHandle);    
    }
  }

//  receive-root(<JOIN-REQUEST, R, j>, j)
//    if (active)
//      send (<JOIN-REPLY, R, L,> to j)
  protected void handleJoinRequest(JoinRequest jr, NodeHandle nh) {
    //System.out.println(localNode+" ************ ChurnJoinProtocol.handleJoinRequest()");
    //Thread.dumpStack();
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
    //System.out.println(localNode+" ************ ChurnJoinProtocol.handleJoinResponse():"+jr.accepted());
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
          lsProtocol.mergeLeafSet(jr.getLeafSet());
          
          // update local RT, then broadcast rows to our peers
          mergeRows(jr);
          
          ((SocketPastryNode)localNode).setJoinedState(Probe.STATE_JOINED);
//  for each j in Li do { probe(j) }          
          lsProtocol.probeLeafSet(true);

          // we have now successfully joined the ring, set the local node ready
          //localNode.setReady();
        }
      }    
  }
  
  /**
   * Broadcasts the route table rows.
   *
   * @param jr the join row.
   */
  public void mergeRows(JoinRequest jr) {
    //NodeId localId = localHandle.getNodeId();
    int n = jr.numRows();

    // send the rows to the RouteSetProtocol on the local node
    for (int i = jr.lastRow(); i < n; i++) {
      RouteSet row[] = jr.getRow(i);

      if (row != null) {
        BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);

        localHandle.receiveMessage(brr);
      }
    }
  }

  public void broadcastRows(JoinRequest jr) {
    // now broadcast the rows to our peers in each row

    int n = jr.numRows();
    for (int i = jr.lastRow(); i < n; i++) {
      RouteSet row[] = jr.getRow(i);

      BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);

      for (int j = 0; j < row.length; j++) {
        RouteSet rs = row[j];
        if (rs == null)
          continue;

        // send to closest nodes only

        NodeHandle nh = rs.closestNode();
        if (nh != null)
          nh = security.verifyNodeHandle(nh);
        if (nh != null)
          nh.receiveMessage(brr);

        /*
        int m = rs.size();
        for (int k=0; k<m; k++) {
            NodeHandle nh = rs.get(k);
            
            nh.receiveMessage(brr);
        }
        */
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
