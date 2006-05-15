package rice.pastry.routing;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * The address of the router at a pastry node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class RouterAddress {
  private static final int myCode = 0xACBDFE17;

  public static int getCode() {
    return myCode; 
  }  
}