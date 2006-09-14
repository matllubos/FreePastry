/*
 * Created on Nov 8, 2005
 */
package rice.pastry.direct;

public interface NodeRecord {

  int networkDelay(NodeRecord nrb);
  
  int proximity(NodeRecord nrb);
  
}
