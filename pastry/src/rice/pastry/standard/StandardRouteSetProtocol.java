
package rice.pastry.standard;

import rice.pastry.*;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;
import rice.pastry.security.*;

import java.util.*;

/**
 * An implementation of a simple route set protocol.
 *
 * @version $Id$
 *
 * @author Andrew Ladd
 * @author Peter Druschel
 */

public class StandardRouteSetProtocol implements Observer, MessageReceiver
{
    private static final int maxTrials = (1 << RoutingTable.baseBitLength()) / 2;
    private NodeHandle localHandle;    
    private PastrySecurityManager security;
    private RoutingTable routeTable;
    private Address address;
    private Random rng;
        
    /**
     * Constructor.
     *
     * @param lh the local handle
     * @param sm the security manager
     * @param rt the routing table
     */

    public StandardRouteSetProtocol(NodeHandle lh, PastrySecurityManager sm, RoutingTable rt) {
	localHandle = lh;
	security = sm;
	routeTable = rt;
	rng = new Random(PastrySeed.getSeed());
	address = new RouteProtocolAddress();

	rt.addObserver(this);
    }

    /**
     * Gets the address.
     *
     * @return the address.
     */

    public Address getAddress() { return address; }

    /**
     * Observer update.
     *
     * @param obs the observable.
     * @param arg the argument.
     */

    public void update(Observable obs, Object arg) {}

    /**
     * Receives a message.
     *
     * @param msg the message.
     */

    public void receiveMessage(Message msg) {
	if (msg instanceof BroadcastRouteRow) {
	    BroadcastRouteRow brr = (BroadcastRouteRow) msg;

	    RouteSet[] row = brr.getRow();
	    //System.out.println("BroadcastRouteRow from " + brr.from().getNodeId());

	    NodeHandle nh = brr.from();
	    nh = security.verifyNodeHandle(nh);
	    if (nh.isAlive()) routeTable.put(nh);

	    for (int i=0; i<row.length; i++) {
        RouteSet rs = row[i];
        //int n = rs.size();
        //System.out.print(n + " ");
        
        for (int j=0; rs!=null && j<rs.size(); j++) {
          nh = rs.get(j);
          nh = security.verifyNodeHandle(nh);
          if (nh.isAlive() == false) continue;
          routeTable.put(nh);
        }
	    }

	    //System.out.println("done");
	}

	else if (msg instanceof RequestRouteRow) {  // a remote node request one of our routeTable rows
	    RequestRouteRow rrr = (RequestRouteRow) msg;

	    int reqRow = rrr.getRow();
	    NodeHandle nh = rrr.returnHandle();
	    nh = security.verifyNodeHandle(nh);

	    //System.out.println("RequestRouteRow " + reqRow + " from " + nh.getNodeId());

	    RouteSet row[] = routeTable.getRow(reqRow);
	    BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);
	    nh.receiveMessage(brr);
	}

	else if (msg instanceof InitiateRouteSetMaintenance) {  // request for routing table maintenance
	    
	    // perform routing table maintenance
	    maintainRouteSet();
 
	}

	else throw new Error("StandardRouteSetProtocol: received message is of unknown type");

    }


    /**
     * performs periodic maintenance of the routing table
     * for each populated row of the routing table, it picks a random column
     * and swaps routing table rows with the closest entry in that column
     */

    private void maintainRouteSet() {

	if (Log.ifp(7))
	    System.out.println("maintainRouteSet " + localHandle.getNodeId());

	// for each populated row in our routing table
	for (int i=routeTable.numRows()-1; i>=0; i--) {
	    RouteSet row[] = routeTable.getRow(i);
	    BroadcastRouteRow brr = new BroadcastRouteRow(localHandle, row);
	    RequestRouteRow rrr = new RequestRouteRow(localHandle, i);
	    int myCol = localHandle.getNodeId().getDigit(i, RoutingTable.baseBitLength());
	    int j;

	    // try up to maxTrials times to find a column with live entries
	    for (j=0; j<maxTrials; j++) {
		// pick a random column
		int col = rng.nextInt(routeTable.numColumns());
		if (col == myCol) continue;

		RouteSet rs = row[col];

		// swap row with closest node only
		NodeHandle nh;

		if (rs != null && (nh = rs.closestNode()) != null) {
		    //System.out.println(localHandle.getNodeId() + 
		    //	       " swapping RT row[" + i + "," + col + "] with " + nh.getNodeId());
		    nh.receiveMessage(brr);
		    nh.receiveMessage(rrr);
		    break;
		}
	    }
	    
	    // once we hit a row where we can't find a populated entry after numTrial trials, we finish
	    if (j == maxTrials) break;

	}

    }

}
