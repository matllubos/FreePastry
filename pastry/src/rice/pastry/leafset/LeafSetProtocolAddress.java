package rice.pastry.leafset;

import rice.pastry.*;
import rice.pastry.messaging.*;

import java.io.*;

/**
 * The address of the leafset protocol at a pastry node.
 * 
 * @version $Id$
 * 
 * @author Andrew Ladd
 */

public class LeafSetProtocolAddress implements Address {
  private static final int myCode = 0xf921def1;

  /**
   * Constructor.
   */

  public LeafSetProtocolAddress() {
  }

  public boolean equals(Object obj) {
    return (obj instanceof LeafSetProtocolAddress);
  }

  public int hashCode() {
    return myCode;
  }

  public String toString() {
    return "[LeafSetProtocolAddress]";
  }
}