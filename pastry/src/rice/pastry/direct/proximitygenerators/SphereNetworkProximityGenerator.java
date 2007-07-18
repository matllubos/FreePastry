package rice.pastry.direct.proximitygenerators;

import rice.environment.Environment;
import rice.environment.random.RandomSource;
import rice.pastry.direct.NodeRecord;
import rice.pastry.direct.ProximityGenerator;

public class SphereNetworkProximityGenerator implements ProximityGenerator {
  int maxDiameter;
  RandomSource random;

  public SphereNetworkProximityGenerator(int maxDiameter) {
    this.maxDiameter = maxDiameter;
  }


  public NodeRecord generateNodeRecord() {
    return new SphereNodeRecord(); 
  }
  
  /**
   * Initialize a random Sphere NodeRecord
   *
   * @version $Id: SphereNetwork.java 3613 2007-02-15 14:45:14Z jstewart $
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
    public float proximity(NodeRecord that) {
      return (float)Math.round(networkDelay(that)*2.0);
    }
    
    public float networkDelay(NodeRecord that) {
      SphereNodeRecord nr = (SphereNodeRecord)that;
      double ret = (radius * Math.acos(Math.cos(phi - nr.phi) * Math.cos(theta) * Math.cos(nr.theta) +
        Math.sin(theta) * Math.sin(nr.theta)));
      
      if ((ret < 2.0) && !this.equals(that)) return (float)2.0;
      
      return (float)ret;
    }

    public void markDead() {
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
    new SphereNetworkProximityGenerator(Environment.directEnvironment().getParameters().getInt("pastry_direct_max_diameter")).test();    
  }
  
  public void setRandom(RandomSource random) {
    this.random = random;
  }  
}
