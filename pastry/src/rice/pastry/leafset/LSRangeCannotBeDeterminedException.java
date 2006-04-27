/*
 * Created on Aug 16, 2005
 */
package rice.pastry.leafset;

import rice.p2p.commonapi.*;

/**
 * @author Jeff Hoye
 */
public class LSRangeCannotBeDeterminedException extends RangeCannotBeDeterminedException {

  public int r;
  public int pos;
  public int uniqueCount;
  public NodeHandle nh;
  
  /**
   * @param string
   */
  public LSRangeCannotBeDeterminedException(String string, int r, int pos, int uniqueNodes, NodeHandle nh, LeafSet ls) {
    super(string+" replication factor:"+r+" nh position:"+pos+" handle:"+nh+" ls.uniqueNodes():"+uniqueNodes+" "+ls.toString());
    this.r = r;
    this.pos = pos;
    this.nh = nh;
    this.uniqueCount = uniqueNodes;
  }

}
