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
public class SphereNetwork extends BasicNetworkSimulator {

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
    double radius;
    
    /**
     * Constructor for NodeRecord.
     *
     * @param nh DESCRIBE THE PARAMETER
     */
    public SphereNodeRecord() {
      this(Math.asin(2.0 * random.nextDouble() - 1.0),
           2.0 * Math.PI * random.nextDouble());
    }

    public SphereNodeRecord(double theta, double phi) {
      this.theta = theta;
      this.phi = phi;
      radius = maxDiameter/Math.PI;
    }
    
    /**
     * DESCRIBE THE METHOD
     *
     * @param nr DESCRIBE THE PARAMETER
     * @return DESCRIBE THE RETURN VALUE
     */
    public int proximity(NodeRecord that) {
      return networkDelay(that)*2;
    }
    
    public int networkDelay(NodeRecord that) {
      SphereNodeRecord nr = (SphereNodeRecord)that;
      int ret = (int) (radius * Math.acos(Math.cos(phi - nr.phi) * Math.cos(theta) * Math.cos(nr.theta) +
        Math.sin(theta) * Math.sin(nr.theta)));
      
      if ((ret < 2) && !this.equals(that)) return 2;
      
      return ret;
    }
  }
  
  public void test() {
    System.out.println(new SphereNodeRecord(0,0).proximity(new SphereNodeRecord(0, Math.PI))); 
    System.out.println(new SphereNodeRecord(-1,0).proximity(new SphereNodeRecord(1, Math.PI))); 
    for (int i = 0; i < 100; i++) {
      System.out.println(new SphereNodeRecord().proximity(new SphereNodeRecord())); 
    }
  }
  
  public static void main(String[] argz) {
    System.out.println("hello world"); 
    new SphereNetwork(Environment.directEnvironment()).test();    
  }
}

