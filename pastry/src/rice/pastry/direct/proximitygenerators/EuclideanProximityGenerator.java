package rice.pastry.direct.proximitygenerators;

import rice.environment.random.RandomSource;
import rice.pastry.direct.NodeRecord;
import rice.pastry.direct.ProximityGenerator;

public class EuclideanProximityGenerator implements ProximityGenerator{
  int side;
  RandomSource random;
  
  /**
   * Constructor.
   */
  public EuclideanProximityGenerator(int maxDiameter) {
    side = (int)(maxDiameter/Math.sqrt(2.0));
  }

  public NodeRecord generateNodeRecord() {
    return new EuclideanNodeRecord(); 
  }
  
  /**
   * Initialize a random Euclidean NodeRecord
   *
   * @version $Id: EuclideanNetwork.java 3613 2007-02-15 14:45:14Z jstewart $
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

    public float proximity(NodeRecord that) {
      return Math.round((networkDelay(that)*2.0));
    }
    
    public float networkDelay(NodeRecord that) {
      EuclideanNodeRecord nr = (EuclideanNodeRecord)that;
      int dx = x - nr.x;
      int dy = y - nr.y;
      
      float ret = (float)Math.sqrt(dx * dx + dy * dy);
//      int ret = (int)Math.round(sqrt);
      if ((ret < 2.0) && !this.equals(that)) return (float)2.0;
      
      return ret;
    }
    
    public String toString() {
      return "ENR("+x+","+y+")"; 
    }

    public void markDead() {
    }
    
  }

  public void setRandom(RandomSource random) {
    this.random = random;
  }  

}
