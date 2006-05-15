package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * The address of the route protocol at a pastry node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class RouteProtocolAddress {
  private static final int myCode = 0x89ce110e;

  public static int getCode() {
    return myCode;
  }
}