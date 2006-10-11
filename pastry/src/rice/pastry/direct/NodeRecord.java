/*
 * Created on Nov 8, 2005
 */
package rice.pastry.direct;

public interface NodeRecord {

  float networkDelay(NodeRecord nrb);
  
  float proximity(NodeRecord nrb);
  
  void markDead();
  
}
