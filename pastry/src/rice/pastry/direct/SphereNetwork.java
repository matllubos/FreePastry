package rice.pastry.direct;
import java.lang.*;

import java.util.*;

import rice.environment.Environment;
import rice.environment.logging.Logger;
import rice.pastry.*;
import rice.pastry.messaging.*;

/**
 * Sphere network topology and idealized node life. Emulates a network of nodes that are randomly
 * placed on a sphere. Proximity is based on euclidean distance on the sphere.
 *
 * @version $Id$
 * @author Y. Charlie Hu
 * @author Rongmei Zhang
 */
public class SphereNetwork extends GeometricNetworkSimulator {

  /**
   * Constructor.
   */
  public SphereNetwork(Environment env) {
    super(env);
  }

  public NodeRecord generateNodeRecord() {
    return new SphereNodeRecord(); 
  }
  
  /**
   * Initialize a random Sphere NodeRecord
   *
   * @version $Id$
   * @author amislove
   */
  private class SphereNodeRecord implements NodeRecord {
    /**
     * DESCRIBE THE FIELD
     */
    public double theta, phi;

    /**
     * Constructor for NodeRecord.
     *
     * @param nh DESCRIBE THE PARAMETER
     */
    public SphereNodeRecord() {
      theta = Math.asin(2.0 * random.nextDouble() - 1.0);
      phi = 2.0 * Math.PI * random.nextDouble();
    }

    /**
     * DESCRIBE THE METHOD
     *
     * @param nr DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public int proximity(NodeRecord that) {
      SphereNodeRecord nr = (SphereNodeRecord)that;
      return (int) (10000 * Math.acos(Math.cos(phi - nr.phi) * Math.cos(theta) * Math.cos(nr.theta) +
        Math.sin(theta) * Math.sin(nr.theta)));
    }
  }
}

