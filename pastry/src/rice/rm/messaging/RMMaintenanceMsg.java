
package rice.rm.messaging;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.security.*;
import rice.pastry.routing.*;
import rice.pastry.messaging.*;

import rice.rm.*;
import rice.rm.messaging.*;

import java.io.*;
import java.util.*;

/**
 * @(#) RMMaintenanceMsg.java
 * 
 * A periodic message scheduled on the local node to trigger the periodic
 * replica manager maintenance protocol.
 * 
 * @version $Id$
 * 
 * @author Animesh Nandi
 */

public class RMMaintenanceMsg extends RMMessage implements Serializable {

  /**
   * The time interval between successive replica manager maintenance
   * activities.
   */
  public static int maintFreq = 120; // in seconds (here it is 2 minutes)

  /**
   * The time offset after the RM substrate on the local node is ready when we
   * first trigger the maintenance protocol.
   */
  public static int maintStart;                             // in seconds (here
                                                            // it is up to 10
                                                            // minutes)

  /**
   * Constructor
   * 
   * @param source the local node itself
   * @param address the RM application address
   * @param authorCred the credentials of the source
   * @param seqno for debugging purpose only
   */
  public RMMaintenanceMsg(NodeHandle source, Address address,
      Credentials authorCred, int seqno, Environment env) {
    super(source, address, authorCred, seqno);
    maintStart = env.getRandomSource().nextInt(600);
  }

  public void handleDeliverMessage(RMImpl rm) {
    //System.out.println("");
    //System.out.println("RMMaintenance message: at " + rm.getNodeId());
    rm.periodicMaintenance();
  }

  public String toString() {
    return new String("MAINTENANCE_MSG:");
  }
}

