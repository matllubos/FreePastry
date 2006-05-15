package rice.pastry.join;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * The address of the join receiver at a pastry node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class JoinAddress {
  private static final int myCode = 0xe80c17e8;

  public static int getCode() {
    return myCode;
  }
}