package rice.pastry.direct;
import java.lang.*;

import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.environment.random.RandomSource;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Euclidean network topology and idealized node life. Emulates a network of nodes that are randomly
 * placed in a plane. Proximity is based on euclidean distance in the plane.
 *
 * @version $Id$
 * @author Andrew Ladd
 * @author Rongmei Zhang
 */
public class EuclideanNetwork extends BasicNetworkSimulator {

  final int side;
  /**
   * Constructor.
   */
  public EuclideanNetwork(Environment env) {
    super(env);
    side = (int)(maxDiameter/Math.sqrt(2.0));
//    System.out.println("side:"+side);    
  }

  public NodeRecord generateNodeRecord() {
    return new EuclideanNodeRecord(); 
  }
  
  /**
   * Initialize a random Euclidean NodeRecord
   *
   * @version $Id$
   * @author amislove
   */
  private class EuclideanNodeRecord implements NodeRecord {
    /**
     * The euclidean position.
     */
    public int x, y;

    public boolean alive;

    /**
     * Constructor for NodeRecord.
     *
     * @param nh 
     */
    public EuclideanNodeRecord() {
      x = random.nextInt() % side;
      y = random.nextInt() % side;

      alive = true;
    }

    public int proximity(NodeRecord that) {
      return (int)Math.round((networkDelay(that)*2.0));
    }
    
    public double networkDelay(NodeRecord that) {
      EuclideanNodeRecord nr = (EuclideanNodeRecord)that;
      int dx = x - nr.x;
      int dy = y - nr.y;
      
      double ret = Math.sqrt(dx * dx + dy * dy);
//      int ret = (int)Math.round(sqrt);
      if ((ret < 2.0) && !this.equals(that)) return 2.0;
      
      return ret;
    }
    
    public String toString() {
      return "ENR("+x+","+y+")"; 
    }
    
  }  
}
