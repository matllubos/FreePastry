
package rice.pastry;

import java.lang.Comparable;
import java.io.*;
import java.util.*;

/**
 * Represents a Pastry identifier for a node.
 *
 * @version $Id$
 *
 * @author Peter Druschel
 * @author Alan Mislove
 */
public class NodeId extends Id {
  
  /**
   * Support for coalesced Ids - ensures only one copy of each Id is in memory
   */
  private static WeakHashMap NODEID_MAP = new WeakHashMap();
  
  /**
   * This is the bit length of the node ids.  If it is n, then
   * there are 2^n different Pastry nodes.  We currently assume
   * that it is divisible by 32.
   */
  public final static int nodeIdBitLength = IdBitLength;
  
  /**
   * serialver for backwards compatibility
   */
  static final long serialVersionUID = 4346947555837618045L;
  
  /**
   * Constructor.
   *
   * @param material an array of length at least IdBitLength/32 containing raw Id material.
   */
  private NodeId(int material[]) {
    super(material);
  } 
  
  /**
   * Constructor.
   *
   * @param material an array of length at least IdBitLength/32 containing raw Id material.
   */
  public static NodeId buildNodeId(int material[]) {
    return (NodeId) resolve(NODEID_MAP, new NodeId(material));
  }
  
  /**
    * Constructor.
   *
   * @param material an array of length at least IdBitLength/8 containing raw Id material.
   */
  public static NodeId buildNodeId(byte[] material) {
    return buildNodeId(trans(material));
  }
  
  /**
    * Constructor. It constructs a new Id with a value of 0 for all bits.
   */
  public static NodeId buildNodeId() {
    return buildNodeId(new int[nlen]);
  }
  
  /**
   * Define readResolve, which will replace the deserialized object with the canootical
   * one (if one exists) to ensure Id coalescing.
   *
   * @return The real Id
   */
  private Object readResolve() throws ObjectStreamException {
    return resolve(NODEID_MAP, this);
  }
}


