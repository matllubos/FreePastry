package rice.pastry.testing;

import rice.pastry.*;
import rice.pastry.client.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;
import rice.pastry.direct.*;

import java.util.*;

/**
 * Ping
 * 
 * A performance test suite for pastry. This is the per-node app object.
 * 
 * @version $Id$
 * 
 * @author Rongmei Zhang
 */

public class Ping extends PastryAppl {
  private static int pingAddress = PingAddress.getCode();

  public Ping(PastryNode pn) {
    super(pn);
  }

  public int getAddress() {
    return pingAddress;
  }

  public void sendPing(Id nid) {
    routeMsg(nid, new PingMessageNew(pingAddress, getNodeHandle(), nid), 
        new SendOptions());
  }

  public void messageForAppl(Message msg) {

    PingMessageNew pMsg = (PingMessageNew) msg;
    int nHops = pMsg.getHops() - 1;
    double fDistance = pMsg.getDistance();
    double rDistance;

    NetworkSimulator sim = ((DirectNodeHandle) ((DirectPastryNode) thePastryNode)
        .getLocalHandle()).getSimulator();
    PingTestRecord tr = (PingTestRecord) (sim.getTestRecord());

    double dDistance = sim.proximity((DirectNodeHandle)thePastryNode.getLocalHandle(), (DirectNodeHandle)pMsg
        .getSender());
    if (dDistance == 0) {
      rDistance = 0;
    } else {
      rDistance = fDistance / dDistance;
    }
    tr.addHops(nHops);
    tr.addDistance(rDistance);

  }

  public boolean enrouteMessage(Message msg, Id from, NodeHandle nextHop,
      SendOptions opt) {

    PingMessageNew pMsg = (PingMessageNew) msg;
    pMsg.incrHops();
    pMsg.incrDistance(((DirectNodeHandle) ((DirectPastryNode) thePastryNode)
        .getLocalHandle()).getSimulator().proximity(
            (DirectNodeHandle)thePastryNode.getLocalHandle(),
            (DirectNodeHandle)nextHop));

    return true;
  }

  public void leafSetChange(NodeHandle nh, boolean wasAdded) {
  }

  public void routeSetChange(NodeHandle nh, boolean wasAdded) {
  }
}

