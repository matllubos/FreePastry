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
public class EuclideanNetwork extends GeometricNetworkSimulator {

  /**
   * Constructor.
   */
  public EuclideanNetwork(Environment env) {
    super(env);
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
      x = random.nextInt() % 10000;
      y = random.nextInt() % 10000;

      alive = true;
    }


    public int proximity(NodeRecord that) {
      EuclideanNodeRecord nr = (EuclideanNodeRecord)that;
      int dx = x - nr.x;
      int dy = y - nr.y;

      return ((int) Math.sqrt(dx * dx + dy * dy));
    }
  }
}
