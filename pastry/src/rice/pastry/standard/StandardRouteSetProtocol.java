package rice.pastry.standard;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.p2p.commonapi.rawserialization.*;
import rice.pastry.*;
import rice.pastry.client.PastryAppl;
import rice.pastry.messaging.*;
import rice.pastry.routing.*;

import java.io.IOException;
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

public class StandardRouteSetProtocol extends PastryAppl {
  private final int maxTrials;

  private RoutingTable routeTable;

  private Environment environmet;
  
  protected Logger logger;

  static class SRSPDeserializer extends PJavaSerializedDeserializer {
    public SRSPDeserializer(PastryNode pn) {
      super(pn); 
    }
    
    public Message deserialize(InputBuffer buf, short type, int priority, NodeHandle sender) throws IOException {
      switch (type) {
        case RequestRouteRow.TYPE:
          return new RequestRouteRow(sender,buf);
        case BroadcastRouteRow.TYPE:
          return new BroadcastRouteRow(buf,pn,pn);
      }
      return null;
    }    
  }
  
  /**
   * Constructor.
   * 
   * @param lh the local handle
   * @param sm the security manager
   * @param rt the routing table
   */
  public StandardRouteSetProtocol(PastryNode ln, RoutingTable rt, Environment env) {
    this(ln, rt, env, null);
  }
  
  public StandardRouteSetProtocol(PastryNode ln, RoutingTable rt, Environment env, MessageDeserializer md) {
    super(ln, null, RouteProtocolAddress.getCode(),  md == null ? new SRSPDeserializer(ln) : md);
    this.environmet = env;
    maxTrials = (1 << rt.baseBitLength()) / 2;
    routeTable = rt;
    logger = env.getLogManager().getLogger(getClass(), null);
  }

  /**
   * Receives a message.
   * 
   * @param msg the message.
   */

  public void messageForAppl(Message msg) {
    if (msg instanceof BroadcastRouteRow) {
      BroadcastRouteRow brr = (BroadcastRouteRow) msg;
      if (logger.level <= Logger.FINER+5) logger.log("Received "+brr.toStringFull());

      RouteSet[] row = brr.getRow();

      NodeHandle nh = brr.from();
      if (nh.isAlive())
        routeTable.put(nh);

      for (int i = 0; i < row.length; i++) {
        RouteSet rs = row[i];

        for (int j = 0; rs != null && j < rs.size(); j++) {
          nh = rs.get(j);
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

      RouteSet row[] = routeTable.getRow(reqRow);
      BroadcastRouteRow brr = new BroadcastRouteRow(thePastryNode.getLocalHandle(), row);
      if (logger.level <= Logger.FINER+5) logger.log("Responding to "+rrr+" with "+brr.toStringFull());
      thePastryNode.send(nh,brr);
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

    if (logger.level <= Logger.INFO) logger.log(
      "maintainRouteSet " + thePastryNode.getLocalHandle().getNodeId());

    // for each populated row in our routing table
    for (byte i = (byte)(routeTable.numRows() - 1); i >= 0; i--) {
      RouteSet row[] = routeTable.getRow(i);
      BroadcastRouteRow brr = new BroadcastRouteRow(thePastryNode.getLocalHandle(), row);
      RequestRouteRow rrr = new RequestRouteRow(thePastryNode.getLocalHandle(), i);
      int myCol = thePastryNode.getLocalHandle().getNodeId().getDigit(i,
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

        if (rs != null && rs.size() > 0) {
          NodeHandle nh;
          
          nh = rs.closestNode(10); // any liveness status will work
          
          // this logic is to make this work correctly in the simulator
          // if we find him not alive, then we would have routed to him and the liveness would have failed
          // thus the correct behavior is to break, not continue.  In other words, we waste this cycle
          // finding the node faulty and removing it rather than doing an actual swap
          // - Jeff Hoye,  Aug 9, 2006
          if (!nh.isAlive()) {
            if (logger.level <= Logger.FINE) logger.log("found dead node in table:"+nh);            
            rs.remove(nh);
            break;
          }

          if (logger.level <= Logger.FINE) logger.log("swapping with "+(i+1)+"/"+routeTable.numRows()+" "+(j+1)+"/"+maxTrials+":"+nh);
          thePastryNode.send(nh,brr);
          thePastryNode.send(nh,rrr);
          break;
        }
      }

      // once we hit a row where we can't find a populated entry after numTrial
      // trials, we finish
      if (j == maxTrials)
        break;

    }

  }

  public boolean deliverWhenNotReady() {
    return true;
  }

}
