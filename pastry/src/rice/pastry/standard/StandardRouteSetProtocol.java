package rice.pastry.standard;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;

import java.util.*;

/**
 * An implementation of a simple route set protocol.
 * 
 * @version $Id: StandardRouteSetProtocol.java,v 1.15 2005/03/11 00:58:02 jeffh
 *          Exp $
 * 
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class StandardRouteSetProtocol implements Observer, MessageReceiver {
  private final int maxTrials;

  private NodeHandle localHandle;

  private PastrySecurityManager security;

  private RoutingTable routeTable;

  private Address address;

  private Environment environmet;
  /**
   * Constructor.
   * 
   * @param lh the local handle
   * @param sm the security manager
   * @param rt the routing table
   */

  public StandardRouteSetProtocol(NodeHandle lh, PastrySecurityManager sm,
      RoutingTable rt, Environment env) {
    
    this.environmet = env;
    maxTrials = (1 << rt.baseBitLength()) / 2;
    localHandle = lh;
    security = sm;
    routeTable = rt;
    address = new RouteProtocolAddress();

    rt.addObserver(this);
  }

  /**
   * Gets the address.
   * 
   * @return the address.
   */

  public Address getAddress() {
    return address;
  }

  /**
   * Observer update.
   * 
   * @param obs the observable.
   * @param arg the argument.
   */

  public void update(Observable obs, Object arg) {
  }

  /**
   * Receives a message.
   * 
   * @param msg the message.
   */

  public void receiveMessage(Message msg) {
    if (msg instanceof BroadcastRouteRow) {
      BroadcastRouteRow brr = (BroadcastRouteRow) msg;

      RouteSet[] row = brr.getRow();

      NodeHandle nh = brr.from();
      nh = security.verifyNodeHandle(nh);
      if (nh.isAlive())
        routeTable.put(nh);

      for (int i = 0; i < row.length; i++) {
        RouteSet rs = row[i];

        for (int j = 0; rs != null && j < rs.size(); j++) {
          nh = rs.get(j);
          nh = security.verifyNodeHandle(nh);
          if (nh.isAlive() == false)
            continue;
          routeTable.put(nh);
        }
      }
    }

    else if (msg instanceof RequestRouteRow) { // a remote node request one of
                                               // our routeTable rows
      RequestRouteRow rrr = (RequestRouteRow) msg;

      int reqRow = rrr.getRow();
      NodeHandle nh = rrr.returnHandle();
      nh = security.verifyNodeHandle(nh);

      RouteSet row[] = routeTable.getRow(reqRow);
      BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);
      nh.receiveMessage(brr);
    }

    else if (msg instanceof InitiateRouteSetMaintenance) { // request for
                                                           // routing table
                                                           // maintenance

      // perform routing table maintenance
      maintainRouteSet();

    }

    else
      throw new Error(
          "StandardRouteSetProtocol: received message is of unknown type");

  }

  /**
   * performs periodic maintenance of the routing table for each populated row
   * of the routing table, it picks a random column and swaps routing table rows
   * with the closest entry in that column
   */

  private void maintainRouteSet() {

    environmet.getLogManager().getLogger(StandardRouteSetProtocol.class, null).log(Logger.FINE,
      "maintainRouteSet " + localHandle.getNodeId());

    // for each populated row in our routing table
    for (int i = routeTable.numRows() - 1; i >= 0; i--) {
      RouteSet row[] = routeTable.getRow(i);
      BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);
      RequestRouteRow rrr = new RequestRouteRow(localHandle, i);
      int myCol = localHandle.getNodeId().getDigit(i,
          routeTable.baseBitLength());
      int j;

      // try up to maxTrials times to find a column with live entries
      for (j = 0; j < maxTrials; j++) {
        // pick a random column
        int col = environmet.getRandomSource().nextInt(routeTable.numColumns());
        if (col == myCol)
          continue;

        RouteSet rs = row[col];

        // swap row with closest node only
        NodeHandle nh;

        if (rs != null && (nh = rs.closestNode()) != null) {
          nh.receiveMessage(brr);
          nh.receiveMessage(rrr);
          break;
        }
      }

      // once we hit a row where we can't find a populated entry after numTrial
      // trials, we finish
      if (j == maxTrials)
        break;

    }

  }

}
