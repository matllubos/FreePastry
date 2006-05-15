package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;
import java.util.*;

/**
 * Initiate routing table maintenance on the local node
 * 
 * @version $Id: InitiateRouteSetMaintenance.java,v 1.2 2005/03/11 00:58:10
 *          jeffh Exp $
 * 
 * @author Peter Druschel
 */

public class InitiateRouteSetMaintenance extends Message implements
    Serializable {
  /**
   * Constructor.
   * 
   * @param nh the return handle.
   * @param r which row
   */

  public InitiateRouteSetMaintenance() {
    super(RouteProtocolAddress.getCode());
  }

}